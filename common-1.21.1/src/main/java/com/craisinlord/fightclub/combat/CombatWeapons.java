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
    private static Boolean backportLoaded;
    private static List<DataComponentType<?>> componentTypes;

    private CombatWeapons() {}

    public static boolean backportSpears() {
        if (backportLoaded == null) backportLoaded = CombatPlatform.isModLoaded(BACKPORT_MOD_ID);
        return backportLoaded;
    }

    public static boolean registersOwn(String material, String weapon) {
        return !weapon.equals("spear") || !backportSpears() || !BACKPORT_SPEAR_MATERIALS.contains(material);
    }

    public static boolean isCombatWeapon(Item item) {
        return item instanceof CombatWeaponItem || backportSpearMaterial(item) != null;
    }

    public static String weapon(Item item) {
        if (item instanceof CombatWeaponItem weapon) return weapon.combatWeapon();
        return backportSpearMaterial(item) != null ? "spear" : null;
    }

    public static String material(Item item) {
        if (item instanceof CombatWeaponItem weapon) return weapon.combatMaterial();
        return backportSpearMaterial(item);
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
}
