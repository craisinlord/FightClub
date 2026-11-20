package com.craisinlord.fightclub.combat;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Vanilla item default changes, applied by each loader's default-component event. In 1.21.1 the
 * stack size is the item's default MAX_STACK_SIZE component, which every stack reads.
 */
public final class CombatDefaults {
    public static final List<Item> POTIONS = List.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION);

    private CombatDefaults() {}

    /** Configured potion stack size (read at startup; 1 means leave vanilla alone). */
    public static int potionStackSize() {
        return com.craisinlord.fightclub.CombatConfig.intValue(com.craisinlord.fightclub.CombatConfig.POTION_STACK);
    }
}
