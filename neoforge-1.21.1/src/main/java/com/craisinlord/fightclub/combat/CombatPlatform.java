package com.craisinlord.fightclub.combat;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import com.craisinlord.fightclub.neoforge.FightClubNeoForge;
public final class CombatPlatform {
    private CombatPlatform() {}
    public static void knockback(LivingEntity target, double strength, double x, double z, Player player) { target.knockback(strength, x, z); }
    public static EntityType<DaggerProjectile> daggerProjectileType() { return FightClubNeoForge.DAGGER_PROJECTILE.get(); }
    public static EntityType<SpearProjectile> spearProjectileType() { return FightClubNeoForge.SPEAR_PROJECTILE.get(); }

    public static boolean isModLoaded(String modId) {
        return net.neoforged.fml.ModList.get().isLoaded(modId);
    }

    public static void sendToPlayer(Player player, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) PacketDistributor.sendToPlayer(serverPlayer, payload);
    }

    /** Sends to every player tracking this one (excludes the player itself). */
    public static void sendPoseToTracking(Player player, CombatPosePayload payload) {
        PacketDistributor.sendToPlayersTrackingEntity(player, payload);
    }
}
