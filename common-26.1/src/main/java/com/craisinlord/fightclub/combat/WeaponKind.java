package com.craisinlord.fightclub.combat;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public enum WeaponKind {
    NONE, SWORD, LONGSWORD, SPEAR, DAGGER, TRIDENT, MACE, SHIELD;

    public static WeaponKind of(ItemStack stack) {
        if (stack.isEmpty()) return NONE;
        String weapon = CombatWeapons.weapon(stack.getItem());
        if (weapon != null) {
            return switch (weapon) {
                case "longsword" -> LONGSWORD;
                case "spear" -> SPEAR;
                case "dagger" -> DAGGER;
                case "sword" -> SWORD;
                default -> NONE;
            };
        }
        if (stack.is(ItemTags.SPEARS)) return SPEAR;
        if (CombatWeapons.isTrident(stack.getItem())) return TRIDENT;
        if (CombatWeapons.isMace(stack.getItem())) return MACE;
        if (CombatWeapons.isShield(stack)) return SHIELD;
        if (stack.is(ItemTags.SWORDS)) return SWORD;
        return NONE;
    }

    public boolean thrusts() { return this == SPEAR || this == TRIDENT; }

    public boolean hasRecovery() { return this != NONE && this != SHIELD && this != SWORD; }

    public boolean customSwing() { return this == DAGGER || this == TRIDENT || this == LONGSWORD; }
}
