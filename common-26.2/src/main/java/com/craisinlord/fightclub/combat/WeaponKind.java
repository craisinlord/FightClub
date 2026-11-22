package com.craisinlord.fightclub.combat;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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
        if (stack.is(ItemTags.SPEARS)) return SPEAR;
        if (stack.is(Items.TRIDENT)) return TRIDENT;
        if (stack.is(Items.MACE)) return MACE;
        if (CombatWeapons.isShield(stack)) return SHIELD;
        if (stack.is(ItemTags.SWORDS)) return SWORD;
        return NONE;
    }

    public boolean thrusts() { return this == SPEAR || this == TRIDENT; }

    public boolean hasRecovery() { return this != NONE && this != SHIELD && this != SWORD; }

    public boolean customSwing() { return this == DAGGER || this == TRIDENT || this == LONGSWORD; }
}
