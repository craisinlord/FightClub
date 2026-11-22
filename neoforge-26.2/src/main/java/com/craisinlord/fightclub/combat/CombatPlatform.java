package com.craisinlord.fightclub.combat;

import com.craisinlord.fightclub.neoforge.FightClubNeoForge;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

public final class CombatPlatform {
    private CombatPlatform() {}

    public static void knockback(LivingEntity target, double strength, double x, double z, Player player) {
        target.knockback(strength, x, z, player.damageSources().playerAttack(player), 0.0f);
    }

    public static boolean isModLoaded(String modId) {
        return net.neoforged.fml.ModList.get().isLoaded(modId);
    }

    public static void sendToPlayer(Player player, CustomPacketPayload payload) {
        if (player instanceof ServerPlayer serverPlayer) PacketDistributor.sendToPlayer(serverPlayer, payload);
    }

    public static void sendPoseToTracking(Player player, CombatPosePayload payload) {
        if (player instanceof ServerPlayer) PacketDistributor.sendToPlayersTrackingEntity(player, payload);
    }

    public static EntityType<DaggerProjectile> daggerProjectileType() { return FightClubNeoForge.DAGGER_PROJECTILE.get(); }
    public static EntityType<SpearProjectile> spearProjectileType() { return FightClubNeoForge.SPEAR_PROJECTILE.get(); }
}
