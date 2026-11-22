package com.craisinlord.fightclub.combat;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import com.craisinlord.fightclub.fabric.FightClubFabric;
public final class CombatPlatform {
    private CombatPlatform() {}
    public static void knockback(LivingEntity target, double strength, double x, double z, Player player) { target.knockback(strength, x, z); }
    public static EntityType<DaggerProjectile> daggerProjectileType() { return FightClubFabric.DAGGER_PROJECTILE; }
    public static EntityType<SpearProjectile> spearProjectileType() { return FightClubFabric.SPEAR_PROJECTILE; }

    public static boolean isModLoaded(String modId) {
        return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(modId);
    }

    public static void sendToPlayer(Player player, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        if (player instanceof ServerPlayer serverPlayer && ServerPlayNetworking.canSend(serverPlayer, payload.type())) {
            ServerPlayNetworking.send(serverPlayer, payload);
        }
    }

    public static void sendPoseToTracking(Player player, CombatPosePayload payload) {
        for (ServerPlayer watcher : PlayerLookup.tracking(player)) {
            if (watcher != player && ServerPlayNetworking.canSend(watcher, CombatPosePayload.TYPE)) {
                ServerPlayNetworking.send(watcher, payload);
            }
        }
    }
}
