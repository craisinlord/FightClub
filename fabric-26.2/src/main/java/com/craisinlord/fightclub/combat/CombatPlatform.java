package com.craisinlord.fightclub.combat;

import com.craisinlord.fightclub.fabric.FightClubFabric;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class CombatPlatform {
    private CombatPlatform() {}

    public static void knockback(LivingEntity target, double strength, double x, double z, Player player) {
        target.knockback(strength, x, z, player.damageSources().playerAttack(player), 0.0f);
    }

    public static boolean isModLoaded(String modId) {
        return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(modId);
    }

    public static void sendToPlayer(Player player, CustomPacketPayload payload) {
        if (player instanceof ServerPlayer serverPlayer && ServerPlayNetworking.canSend(serverPlayer, payload.type())) {
            ServerPlayNetworking.send(serverPlayer, payload);
        }
    }

    /** Sends to every player tracking this one (not the player itself, whose client already knows). */
    public static void sendPoseToTracking(Player player, CombatPosePayload payload) {
        if (!(player instanceof ServerPlayer)) return;
        for (ServerPlayer watcher : PlayerLookup.tracking(player)) {
            if (watcher != player && ServerPlayNetworking.canSend(watcher, CombatPosePayload.TYPE)) {
                ServerPlayNetworking.send(watcher, payload);
            }
        }
    }

    public static EntityType<DaggerProjectile> daggerProjectileType() { return FightClubFabric.DAGGER_PROJECTILE; }
    public static EntityType<SpearProjectile> spearProjectileType() { return FightClubFabric.SPEAR_PROJECTILE; }
}
