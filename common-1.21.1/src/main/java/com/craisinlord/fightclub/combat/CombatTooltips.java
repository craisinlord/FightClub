package com.craisinlord.fightclub.combat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Weapon tooltips, per the design doc: one short translatable line saying what's special about
 * the weapon, and the detailed numbers only while Shift is held. Used for Fight Club's own
 * weapons and for the vanilla weapons the mod changes.
 */
public final class CombatTooltips {
    /** Set by the client entrypoints (Screen::hasShiftDown); always false on a dedicated server. */
    private static BooleanSupplier shiftDown = () -> false;

    private CombatTooltips() {}

    public static void setShiftSupplier(BooleanSupplier supplier) {
        shiftDown = supplier;
    }

    /** Which tooltip a stack gets: "longsword", "sword", "axe", ..., or null for none. */
    public static String kindOf(ItemStack stack) {
        Item item = stack.getItem();
        if (CombatWeapons.isCombatWeapon(item)) return CombatWeapons.weapon(item);
        if (item == Items.MACE) return "mace";
        if (item == Items.TRIDENT) return "trident";
        if (item == Items.SHIELD) return "shield";
        if (axeMaterial(item) != null) return "axe";
        if (CombatVanillaRules.swordSpec(stack) != null) return "sword";
        return null;
    }

    public static void append(ItemStack stack, Consumer<Component> tooltip) {
        String kind = kindOf(stack);
        if (kind == null) return;
        // Two short lines: what the weapon is for, then its controls. Both only mention mechanics
        // that are switched on in the config (the server's settings, synced to clients).
        tooltip.accept(Component.translatable("tooltip.fight_club." + descriptionKey(kind)).withStyle(ChatFormatting.GRAY));
        Component controls = controls(kind);
        if (controls != null) tooltip.accept(controls);
        WeaponSpec spec = specFor(stack, kind);
        if (spec == null) return;
        java.util.List<Component> details = details(spec, kind);
        if (details.isEmpty()) return;
        if (shiftDown.getAsBoolean()) {
            details.forEach(tooltip);
        } else {
            tooltip.accept(Component.translatable("tooltip.fight_club.hold_shift").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static boolean on(String option) {
        return com.craisinlord.fightclub.CombatConfig.enabled(option);
    }

    private static String descriptionKey(String kind) {
        return switch (kind) {
            case "spear", "trident" -> on(com.craisinlord.fightclub.CombatConfig.PENETRATION) ? kind : kind + ".no_pierce";
            case "mace" -> on(com.craisinlord.fightclub.CombatConfig.DATA_SWEEPS) ? kind : kind + ".no_sweep";
            default -> kind;
        };
    }

    /** The controls line, joined from the enabled mechanics with " · ", or null if there's nothing to say. */
    private static Component controls(String kind) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        boolean charge = on(com.craisinlord.fightclub.CombatConfig.CHARGED_ATTACKS);
        boolean toss = on(com.craisinlord.fightclub.CombatConfig.THROWING);
        switch (kind) {
            case "sword" -> { if (on(com.craisinlord.fightclub.CombatConfig.SWORD_BLOCKING)) parts.add("block"); }
            case "longsword" -> { if (on(com.craisinlord.fightclub.CombatConfig.LONGSWORD_SPIN)) parts.add("spin"); if (on(com.craisinlord.fightclub.CombatConfig.LONGSWORD_OFFHAND)) parts.add("offhand"); }
            case "spear" -> { if (charge) parts.add("charge"); if (toss) parts.add("throw"); }
            case "dagger" -> { if (charge) parts.add("stab"); if (toss) parts.add("throw"); }
            case "trident" -> { if (charge) parts.add("charge"); parts.add("throw"); }
            case "mace" -> { if (on(com.craisinlord.fightclub.CombatConfig.MACE_HIT_STREAK)) parts.add("streak"); }
            case "shield" -> { if (on(com.craisinlord.fightclub.CombatConfig.SHIELD_BASH)) parts.add("bash"); }
            case "axe" -> parts.add("shields");
            default -> { }
        }
        if (parts.isEmpty()) return null;
        net.minecraft.network.chat.MutableComponent line = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) line.append(" · ");
            line.append(Component.translatable("tooltip.fight_club.control." + parts.get(i)));
        }
        return line.withStyle(ChatFormatting.GRAY);
    }

    /**
     * Only numbers the game actually applies. Reach is shown by Better Combat's own "Attack Range"
     * line when it's installed; sweep radius and throw range aren't enforced yet.
     */
    private static java.util.List<Component> details(WeaponSpec spec, String kind) {
        java.util.List<Component> lines = new java.util.ArrayList<>();
        boolean shield = "shield".equals(kind);
        if (spec.attackStyle() == WeaponSpec.AttackStyle.THRUST && spec.penetrationCount() > 0
                && on(com.craisinlord.fightclub.CombatConfig.PENETRATION)) lines.add(stat("pierce", spec.penetrationCount()));
        if (spec.blocking() && !shield && on(com.craisinlord.fightclub.CombatConfig.SWORD_BLOCKING)) {
            lines.add(stat("block", Math.round((1.0F - spec.blockingDamageMultiplier()) * 100.0F)));
        }
        if (spec.chargeTicks() > 0 && spec.chargeDamage() > 0.0F && on(com.craisinlord.fightclub.CombatConfig.CHARGED_ATTACKS)) {
            lines.add(stat("charge", number(spec.chargeDamage()), seconds(spec.chargeTicks())));
        }
        if (spec.recoveryTicks() > 0) {
            if (shield && on(com.craisinlord.fightclub.CombatConfig.SHIELD_BASH)) lines.add(stat("bash_cooldown", seconds(spec.bashHitCooldownTicks()), seconds(spec.recoveryTicks())));
            else if (!shield && on(com.craisinlord.fightclub.CombatConfig.RECOVERY)) lines.add(stat("recovery", seconds(spec.recoveryTicks())));
        }
        if (spec.offhandSpeedMultiplier() < 1.0F && on(com.craisinlord.fightclub.CombatConfig.LONGSWORD_OFFHAND)) {
            lines.add(stat("offhand", Math.round((1.0F - spec.offhandSpeedMultiplier()) * 100.0F)));
        }
        if (!spec.criticalHits() && !shield && !"mace".equals(kind)) {
            if ("spear".equals(kind) || "trident".equals(kind)) {
                // Spears and tridents crit only on charged hits (or never, with charges off).
                if (on(com.craisinlord.fightclub.CombatConfig.SPEAR_CHARGE_CRITS)) {
                    lines.add(stat(on(com.craisinlord.fightclub.CombatConfig.CHARGED_ATTACKS) ? "charge_crits" : "no_crits"));
                }
            } else if (on(com.craisinlord.fightclub.CombatConfig.LONGSWORD_NO_CRITS)) {
                lines.add(stat("no_crits"));
            }
        }
        return lines;
    }

    /** The weapon spec that governs this stack (Fight Club weapon or changed vanilla weapon), or null. */
    public static WeaponSpec specFor(ItemStack stack) {
        String kind = kindOf(stack);
        return kind == null ? null : specFor(stack, kind);
    }

    private static WeaponSpec specFor(ItemStack stack, String kind) {
        Item item = stack.getItem();
        if (CombatWeapons.isCombatWeapon(item)) return CombatWeapons.spec(item);
        return switch (kind) {
            case "mace", "trident", "shield" -> WeaponSpecs.getOrLoadBuiltin("base", kind);
            case "axe" -> WeaponSpecs.getOrLoadBuiltin(axeMaterial(item), "axe");
            case "sword" -> CombatVanillaRules.swordSpec(stack);
            default -> null;
        };
    }

    private static String axeMaterial(Item item) {
        if (item == Items.WOODEN_AXE) return "wood";
        if (item == Items.STONE_AXE) return "stone";
        if (item == Items.IRON_AXE) return "iron";
        if (item == Items.GOLDEN_AXE) return "gold";
        if (item == Items.DIAMOND_AXE) return "diamond";
        if (item == Items.NETHERITE_AXE) return "netherite";
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
