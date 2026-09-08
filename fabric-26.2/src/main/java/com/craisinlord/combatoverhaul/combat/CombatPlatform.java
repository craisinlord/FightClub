package com.craisinlord.combatoverhaul.combat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;

import com.craisinlord.combatoverhaul.combat.DaggerProjectile;
import com.craisinlord.combatoverhaul.combat.SpearProjectile;
import com.craisinlord.combatoverhaul.fabric.CombatOverhaulFabric;

public final class CombatPlatform {
    private CombatPlatform() {}

    public static void knockback(LivingEntity target, double strength, double x, double z, Player player) {
        target.knockback(strength, x, z, player.damageSources().playerAttack(player), 0.0f);
    }

    public static EntityType<DaggerProjectile> daggerProjectileType() { return CombatOverhaulFabric.DAGGER_PROJECTILE; }
    public static EntityType<SpearProjectile> spearProjectileType() { return CombatOverhaulFabric.SPEAR_PROJECTILE; }
}
