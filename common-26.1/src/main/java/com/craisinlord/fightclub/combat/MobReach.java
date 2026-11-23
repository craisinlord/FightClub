package com.craisinlord.fightclub.combat;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

public final class MobReach {
    private static final double BASE_REACH = 3.0;

    private MobReach() {}

    public static double longswordBonus(Mob mob) {
        ItemStack stack = mob.getMainHandItem();
        if (stack.isEmpty() || !CombatWeapons.is(stack.getItem(), "longsword")) return 0.0;
        WeaponSpec spec = CombatTooltips.specFor(stack);
        return spec == null ? 0.0 : Math.max(0.0, spec.reach() - BASE_REACH);
    }
}
