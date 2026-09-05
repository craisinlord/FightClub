package com.craisinlord.combatoverhaul.combat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class CombatPlatform {
    private CombatPlatform() {}

    public static void knockback(LivingEntity target, double strength, double x, double z, Player player) {
        target.knockback(strength, x, z, player.damageSources().playerAttack(player), 0.0f);
    }
}
