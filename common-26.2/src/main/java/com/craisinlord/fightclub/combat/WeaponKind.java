package com.craisinlord.fightclub.combat;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Coarse weapon families used by animation and input code on both sides. */
public enum WeaponKind {
    NONE, SWORD, LONGSWORD, SPEAR, DAGGER, TRIDENT, MACE, SHIELD;

    public static WeaponKind of(ItemStack stack) {
        if (stack.isEmpty()) return NONE;
        if (stack.getItem() instanceof CombatWeaponItem weapon) {
            return switch (weapon.combatWeapon()) {
                case "longsword" -> LONGSWORD;
                case "spear" -> SPEAR;
                case "dagger" -> DAGGER;
                case "sword" -> SWORD;
                default -> NONE;
            };
        }
        // 26.x uses the vanilla spears (kinetic weapons) instead of Fight Club's own.
        if (stack.is(ItemTags.SPEARS)) return SPEAR;
        if (stack.is(Items.TRIDENT)) return TRIDENT;
        if (stack.is(Items.MACE)) return MACE;
        if (stack.is(Items.SHIELD)) return SHIELD;
        if (stack.is(ItemTags.SWORDS)) return SWORD;
        return NONE;
    }

    /** Spear-like thrust weapons (spear, trident). */
    public boolean thrusts() { return this == SPEAR || this == TRIDENT; }

    /** Weapons whose recovery should show as a hotbar cooldown and a lowered weapon. */
    public boolean hasRecovery() { return this != NONE && this != SHIELD && this != SWORD; }

    /**
     * Swings that Fight Club animates itself (without Better Combat). Vanilla 26.x spears
     * already have their own stab (SwingAnimationType.STAB), so they are left alone.
     */
    public boolean customSwing() { return this == DAGGER || this == TRIDENT || this == LONGSWORD; }
}
