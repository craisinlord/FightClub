package com.craisinlord.fightclub.combat;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class CombatWeapons {
    private CombatWeapons() {}

    public static boolean isCombatWeapon(Item item) {
        return weapon(item) != null;
    }

    public static String weapon(Item item) {
        if (item instanceof CombatWeaponItem weapon) return weapon.combatWeapon();
        ExternalWeapons.Entry external = ExternalWeapons.get(item);
        return external == null || external.weapon().equals("shield") ? null : external.weapon();
    }

    public static String material(Item item) {
        if (item instanceof CombatWeaponItem weapon) return weapon.combatMaterial();
        ExternalWeapons.Entry external = ExternalWeapons.get(item);
        return external == null ? null : external.material();
    }

    public static ExternalWeapons.Entry external(Item item) {
        if (item instanceof CombatWeaponItem) return null;
        ExternalWeapons.Entry external = ExternalWeapons.get(item);
        return external == null || external.weapon().equals("shield") ? null : external;
    }

    public static boolean usesSpecStats(Item item) {
        if (item instanceof CombatWeaponItem) return true;
        ExternalWeapons.Entry external = ExternalWeapons.get(item);
        return external != null && external.specStats();
    }

    public static boolean isShield(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.is(Items.SHIELD)) return true;
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
}
