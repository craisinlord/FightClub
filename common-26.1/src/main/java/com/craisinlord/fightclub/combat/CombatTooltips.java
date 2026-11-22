package com.craisinlord.fightclub.combat;

import com.craisinlord.fightclub.CombatConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class CombatTooltips {
    private static BooleanSupplier shiftDown = () -> false;

    private static final String[] MATERIALS = {"wood", "stone", "copper", "iron", "gold", "diamond", "netherite"};
    private static final Item[] AXES = {Items.WOODEN_AXE, Items.STONE_AXE, Items.COPPER_AXE, Items.IRON_AXE,
            Items.GOLDEN_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE};
    private static final Item[] SPEARS = {Items.WOODEN_SPEAR, Items.STONE_SPEAR, Items.COPPER_SPEAR, Items.IRON_SPEAR,
            Items.GOLDEN_SPEAR, Items.DIAMOND_SPEAR, Items.NETHERITE_SPEAR};

    private CombatTooltips() {}

    public static void setShiftSupplier(BooleanSupplier supplier) {
        shiftDown = supplier;
    }

    public static String kindOf(ItemStack stack) {
        Item item = stack.getItem();
        if (CombatWeapons.isCombatWeapon(item)) return CombatWeapons.weapon(item);
        if (item == Items.MACE) return "mace";
        if (item == Items.TRIDENT) return "trident";
        if (CombatWeapons.isShield(stack)) return "shield";
        if (material(item, SPEARS) != null) return "spear";
        if (material(item, AXES) != null) return "axe";
        if (CombatVanillaRules.swordSpec(stack) != null) return "sword";
        return null;
    }

    public static void append(ItemStack stack, Consumer<Component> tooltip) {
        String kind = kindOf(stack);
        if (kind == null) return;
        tooltip.accept(Component.translatable("tooltip.fight_club." + descriptionKey(kind)).withStyle(ChatFormatting.GRAY));
        Component controls = controls(kind);
        if (controls != null) tooltip.accept(controls);
        WeaponSpec spec = specFor(stack, kind);
        if (spec == null) return;
        List<Component> details = details(spec, kind);
        if (details.isEmpty()) return;
        if (shiftDown.getAsBoolean()) {
            details.forEach(tooltip);
        } else {
            tooltip.accept(Component.translatable("tooltip.fight_club.hold_shift").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static boolean on(String option) {
        return CombatConfig.enabled(option);
    }

    private static String descriptionKey(String kind) {
        return switch (kind) {
            case "spear", "trident" -> on(CombatConfig.PENETRATION) ? kind : kind + ".no_pierce";
            case "mace" -> on(CombatConfig.DATA_SWEEPS) ? kind : kind + ".no_sweep";
            default -> kind;
        };
    }

    private static Component controls(String kind) {
        List<String> parts = new ArrayList<>();
        boolean charge = on(CombatConfig.CHARGED_ATTACKS);
        boolean toss = on(CombatConfig.THROWING);
        switch (kind) {
            case "sword" -> { if (on(CombatConfig.SWORD_BLOCKING)) parts.add("block"); }
            case "longsword" -> { if (on(CombatConfig.LONGSWORD_SPIN)) parts.add("spin"); if (on(CombatConfig.LONGSWORD_OFFHAND)) parts.add("offhand"); }
            case "spear" -> { if (charge) parts.add("charge"); if (toss) parts.add("throw"); }
            case "dagger" -> { if (charge) parts.add("stab"); if (toss) parts.add("throw"); }
            case "trident" -> { if (charge) parts.add("charge"); parts.add("throw"); }
            case "mace" -> { if (on(CombatConfig.MACE_HIT_STREAK)) parts.add("streak"); }
            case "shield" -> { if (on(CombatConfig.SHIELD_BASH)) parts.add("bash"); }
            case "axe" -> parts.add("shields");
            default -> { }
        }
        if (parts.isEmpty()) return null;
        MutableComponent line = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) line.append(" · ");
            line.append(Component.translatable("tooltip.fight_club.control." + parts.get(i)));
        }
        return line.withStyle(ChatFormatting.GRAY);
    }

    private static List<Component> details(WeaponSpec spec, String kind) {
        List<Component> lines = new ArrayList<>();
        boolean shield = "shield".equals(kind);
        if (spec.attackStyle() == WeaponSpec.AttackStyle.THRUST && spec.penetrationCount() > 0
                && on(CombatConfig.PENETRATION)) lines.add(stat("pierce", spec.penetrationCount()));
        if (spec.blocking() && !shield && on(CombatConfig.SWORD_BLOCKING)) {
            lines.add(stat("block", Math.round((1.0F - spec.blockingDamageMultiplier()) * 100.0F)));
        }
        if (spec.chargeTicks() > 0 && spec.chargeDamage() > 0.0F && on(CombatConfig.CHARGED_ATTACKS)) {
            lines.add(stat("charge", number(spec.chargeDamage()), seconds(spec.chargeTicks())));
        }
        if (spec.recoveryTicks() > 0) {
            if (shield && on(CombatConfig.SHIELD_BASH)) lines.add(stat("bash_cooldown", seconds(spec.bashHitCooldownTicks()), seconds(spec.recoveryTicks())));
            else if (!shield && on(CombatConfig.RECOVERY)) lines.add(stat("recovery", seconds(spec.recoveryTicks())));
        }
        if (spec.offhandSpeedMultiplier() < 1.0F && on(CombatConfig.LONGSWORD_OFFHAND)) {
            lines.add(stat("offhand", Math.round((1.0F - spec.offhandSpeedMultiplier()) * 100.0F)));
        }
        if (!spec.criticalHits() && !shield && !"mace".equals(kind)) {
            if ("spear".equals(kind) || "trident".equals(kind)) {
                if (on(CombatConfig.SPEAR_CHARGE_CRITS)) lines.add(stat(on(CombatConfig.CHARGED_ATTACKS) ? "charge_crits" : "no_crits"));
            } else if (on(CombatConfig.LONGSWORD_NO_CRITS)) {
                lines.add(stat("no_crits"));
            }
        }
        return lines;
    }

    public static WeaponSpec specFor(ItemStack stack) {
        String kind = kindOf(stack);
        return kind == null ? null : specFor(stack, kind);
    }

    private static WeaponSpec specFor(ItemStack stack, String kind) {
        Item item = stack.getItem();
        if (CombatWeapons.isCombatWeapon(item)) return CombatWeapons.spec(item);
        return switch (kind) {
            case "mace", "trident" -> WeaponSpecs.getOrLoadBuiltin("base", kind);
            case "shield" -> CombatWeapons.shieldSpec(stack);
            case "spear" -> WeaponSpecs.getOrLoadBuiltin(material(item, SPEARS), "spear");
            case "axe" -> WeaponSpecs.getOrLoadBuiltin(material(item, AXES), "axe");
            case "sword" -> CombatVanillaRules.swordSpec(stack);
            default -> null;
        };
    }

    private static String material(Item item, Item[] byMaterial) {
        for (int i = 0; i < byMaterial.length; i++) if (item == byMaterial[i]) return MATERIALS[i];
        return null;
    }

    private static Component stat(String key, Object... args) {
        return Component.literal(" ").append(Component.translatable("tooltip.fight_club.stat." + key, args))
                .withStyle(ChatFormatting.DARK_GREEN);
    }

    private static String number(float value) {
        return value == Math.round(value) ? Integer.toString(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
    }

    private static String seconds(int ticks) {
        return number(ticks / 20.0F);
    }
}
