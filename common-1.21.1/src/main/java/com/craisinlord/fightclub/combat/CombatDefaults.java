package com.craisinlord.fightclub.combat;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;

public final class CombatDefaults {
    public static final List<Item> POTIONS = List.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION);

    private CombatDefaults() {}

    public static int potionStackSize() {
        return com.craisinlord.fightclub.CombatConfig.intValue(com.craisinlord.fightclub.CombatConfig.POTION_STACK);
    }
}
