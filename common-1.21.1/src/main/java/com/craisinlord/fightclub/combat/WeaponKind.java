package com.craisinlord.fightclub.combat;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;

/** Coarse weapon families used by animation and input code on both sides. */
public enum WeaponKind {
    NONE, SWORD, LONGSWORD, SPEAR, DAGGER, TRIDENT, MACE, SHIELD;

    public static WeaponKind of(ItemStack stack) {
        if (stack.isEmpty()) return NONE;
        if (CombatWeapons.isCombatWeapon(stack.getItem())) {
            return switch (CombatWeapons.weapon(stack.getItem())) {
                case "longsword" -> LONGSWORD;
                case "spear" -> SPEAR;
                case "dagger" -> DAGGER;
                case "sword" -> SWORD;
                default -> NONE;
            };
        }
        if (stack.is(Items.TRIDENT)) return TRIDENT;
        if (stack.is(Items.MACE)) return MACE;
        if (stack.is(Items.SHIELD)) return SHIELD;
        if (stack.getItem() instanceof SwordItem) return SWORD;
        return NONE;
    }

    /** Spear-like thrust weapons (spear, trident). */
    public boolean thrusts() { return this == SPEAR || this == TRIDENT; }

    /** Weapons whose recovery should show as a hotbar cooldown and a lowered weapon. */
    public boolean hasRecovery() { return this != NONE && this != SHIELD && this != SWORD; }
}
