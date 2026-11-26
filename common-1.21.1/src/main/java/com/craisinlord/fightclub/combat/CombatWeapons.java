package com.craisinlord.fightclub.combat;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CombatWeapons {
    public static final String BACKPORT_MOD_ID = "vanillabackport";
    public static final List<String> BACKPORT_SPEAR_MATERIALS = List.of("wood", "stone", "copper", "iron", "gold", "diamond", "netherite");
    private static final List<String> BACKPORT_COMPONENTS = List.of("attack_range", "kinetic_weapon", "piercing_weapon", "use_effects", "minimum_attack_range", "swing_animation");
    private static final Map<Item, DataComponentPatch> MOB_COMPONENTS = new ConcurrentHashMap<>();
    private static final Map<Item, String> BACKPORT_MATERIALS = new ConcurrentHashMap<>();
    private static Boolean backportLoaded;
    private static List<DataComponentType<?>> componentTypes;

    private CombatWeapons() {}

    public static boolean backportSpears() {
        if (backportLoaded == null) backportLoaded = CombatPlatform.isModLoaded(BACKPORT_MOD_ID);
        return backportLoaded;
    }

    public static boolean registersOwn(String material, String weapon) {
        if (material.equals("copper") && !backportSpears()) return false;
        return !weapon.equals("spear") || !backportSpears() || !BACKPORT_SPEAR_MATERIALS.contains(material);
    }

    public static boolean isHidden(Item item) {
        return ExternalWeapons.isReplaced(item) || copperDisabled(item);
    }

    public static java.util.Set<Item> hiddenItems() {
        java.util.Set<Item> hidden = new java.util.HashSet<>(ExternalWeapons.replacedItems());
        hidden.addAll(disabledCopperItems());
        return hidden;
    }

    public static java.util.Set<Item> disabledCopperItems() {
        java.util.Set<Item> disabled = new java.util.HashSet<>();
        if (backportSpears() && !com.craisinlord.fightclub.compat.VanillaBackportCompat.copperToolset()) {
            for (Item item : BuiltInRegistries.ITEM) if (copperWeapon(item)) disabled.add(item);
        }
        return disabled;
    }

    private static boolean copperDisabled(Item item) {
        return copperWeapon(item) && !com.craisinlord.fightclub.compat.VanillaBackportCompat.copperToolset();
    }

    private static boolean copperWeapon(Item item) {
        return item instanceof CombatWeaponItem weapon && weapon.combatMaterial().equals("copper");
    }

    public static boolean isCombatWeapon(Item item) {
        return weapon(item) != null;
    }

    public static String weapon(Item item) {
        if (item instanceof CombatWeaponItem weapon) return weapon.combatWeapon();
        if (backportSpearMaterial(item) != null) return "spear";
        ExternalWeapons.Entry external = ExternalWeapons.get(item);
        return external == null || external.weapon().equals("shield") ? null : external.weapon();
    }

    public static String material(Item item) {
        if (item instanceof CombatWeaponItem weapon) return weapon.combatMaterial();
        String backport = backportSpearMaterial(item);
        if (backport != null) return backport;
        ExternalWeapons.Entry external = ExternalWeapons.get(item);
        return external == null ? null : external.material();
    }

    public static ExternalWeapons.Entry external(Item item) {
        if (item instanceof CombatWeaponItem || backportSpearMaterial(item) != null) return null;
        ExternalWeapons.Entry external = ExternalWeapons.get(item);
        return external == null || external.weapon().equals("shield") ? null : external;
    }

    public static boolean usesSpecStats(Item item) {
        if (item instanceof CombatWeaponItem || backportSpearMaterial(item) != null) return true;
        ExternalWeapons.Entry external = ExternalWeapons.get(item);
        return external != null && external.specStats();
    }

    public static boolean isShield(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.is(net.minecraft.world.item.Items.SHIELD)) return true;
        ExternalWeapons.Entry external = ExternalWeapons.get(stack.getItem());
        return external != null && external.weapon().equals("shield");
    }

    public static WeaponSpec shieldSpec(ItemStack stack) {
        ExternalWeapons.Entry external = stack.isEmpty() ? null : ExternalWeapons.get(stack.getItem());
        if (external != null && external.weapon().equals("shield")) {
            WeaponSpec spec = WeaponSpecs.getOrLoadBuiltin(external.material(), "shield");
            if (spec != null) return spec;
        }
        return WeaponSpecs.getOrLoadBuiltin("base", "shield");
    }

    public static boolean is(Item item, String weapon) {
        return weapon.equals(weapon(item));
    }

    public static WeaponSpec spec(Item item) {
        String weapon = weapon(item);
        return weapon == null ? null : WeaponSpecs.getOrLoadBuiltin(material(item), weapon);
    }

    public static String backportSpearMaterial(Item item) {
        if (item instanceof CombatWeaponItem || !backportSpears()) return null;
        String cached = BACKPORT_MATERIALS.get(item);
        if (cached != null) return cached.isEmpty() ? null : cached;
        String material = resolveBackportSpearMaterial(item);
        if (item.builtInRegistryHolder().isBound()) BACKPORT_MATERIALS.put(item, material == null ? "" : material);
        return material;
    }

    private static String resolveBackportSpearMaterial(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (!id.getNamespace().equals("minecraft") || !id.getPath().endsWith("_spear")) return null;
        return switch (id.getPath()) {
            case "wooden_spear" -> "wood";
            case "stone_spear" -> "stone";
            case "copper_spear" -> "copper";
            case "iron_spear" -> "iron";
            case "golden_spear" -> "gold";
            case "diamond_spear" -> "diamond";
            case "netherite_spear" -> "netherite";
            default -> null;
        };
    }

    public static Item backportSpear(String material) {
        String path = switch (material) {
            case "wood" -> "wooden_spear";
            case "gold" -> "golden_spear";
            default -> material + "_spear";
        };
        return BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(path));
    }

    public static List<DataComponentType<?>> backportComponentTypes() {
        if (componentTypes == null) {
            List<DataComponentType<?>> types = new ArrayList<>();
            for (String path : BACKPORT_COMPONENTS) {
                BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(ResourceLocation.withDefaultNamespace(path)).ifPresent(types::add);
            }
            componentTypes = List.copyOf(types);
        }
        return componentTypes;
    }

    public static void captureMobComponents(Item item) {
        List<DataComponentType<?>> types = backportComponentTypes();
        DataComponentPatch.Builder patch = DataComponentPatch.builder();
        for (TypedDataComponent<?> component : item.components()) {
            if (types.contains(component.type())) set(patch, component);
        }
        MOB_COMPONENTS.put(item, patch.build());
    }

    private static <T> void set(DataComponentPatch.Builder patch, TypedDataComponent<T> component) {
        patch.set(component.type(), component.value());
    }

    public static boolean hasMobComponents(ItemStack stack) {
        for (DataComponentType<?> type : backportComponentTypes()) if (stack.has(type)) return true;
        return false;
    }

    public static boolean isPlayerBackportSpear(ItemStack stack) {
        return backportSpearMaterial(stack.getItem()) != null && !hasMobComponents(stack);
    }

    public static void restoreMobComponents(ItemStack stack) {
        if (backportSpearMaterial(stack.getItem()) == null || hasMobComponents(stack)) return;
        DataComponentPatch patch = MOB_COMPONENTS.get(stack.getItem());
        if (patch != null && !patch.isEmpty()) stack.applyComponents(patch);
    }

    public static void stripMobComponents(ItemStack stack) {
        if (backportSpearMaterial(stack.getItem()) == null) return;
        for (DataComponentType<?> type : backportComponentTypes()) if (stack.has(type)) stack.remove(type);
    }

    public static boolean isTrident(Item item) {
        return item instanceof net.minecraft.world.item.TridentItem;
    }

    public static boolean isMace(Item item) {
        return item instanceof net.minecraft.world.item.MaceItem;
    }

    public static boolean ownStats(Item item) {
        return item == net.minecraft.world.item.Items.TRIDENT || item == net.minecraft.world.item.Items.MACE || usesSpecStats(item);
    }

    public static float stackAttackDamage(ItemStack stack) {
        double[] damage = {1.0};
        stack.forEachModifier(net.minecraft.world.entity.EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.is(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
                    && modifier.operation() == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE) {
                damage[0] += modifier.amount();
            }
        });
        return (float) damage[0];
    }

    public static float hitDamage(ItemStack stack, WeaponSpec spec) {
        return stack.isEmpty() || ownStats(stack.getItem()) ? spec.attackDamage() : stackAttackDamage(stack);
    }

    public static float damageScale(ItemStack stack, WeaponSpec spec) {
        if (stack.isEmpty() || spec == null || spec.attackDamage() <= 0.0f || ownStats(stack.getItem())) return 1.0f;
        return net.minecraft.util.Mth.clamp(stackAttackDamage(stack) / spec.attackDamage(), 0.25f, 4.0f);
    }

    public static float chargeDamage(ItemStack stack, WeaponSpec spec) {
        return spec.chargeDamage() * damageScale(stack, spec);
    }

    public static float throwDamage(ItemStack stack, WeaponSpec spec) {
        return spec.throwDamage() * damageScale(stack, spec);
    }
}
