package com.craisinlord.fightclub.fabric;

import com.craisinlord.fightclub.combat.CombatVanillaRules;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.world.InteractionResult;
import com.craisinlord.fightclub.combat.CombatActionPayload;
import com.craisinlord.fightclub.combat.CombatChargeState;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class FightClubFabricEvents {
    private static final ThreadLocal<Boolean> REDUCED_DAMAGE = ThreadLocal.withInitial(() -> false);

    private FightClubFabricEvents() {}

    public static void register() {
        PayloadTypeRegistry.playC2S().register(CombatActionPayload.TYPE, CombatActionPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(com.craisinlord.fightclub.combat.CombatPosePayload.TYPE,
                com.craisinlord.fightclub.combat.CombatPosePayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(CombatActionPayload.TYPE,
                (payload, context) -> context.server().execute(() ->
                        CombatChargeState.handle(context.player(), payload.action(), payload.target())));
        // Sword right-click blocking is started by ItemDefaultsMixin (shared by both loaders).
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            CombatVanillaRules.beforeAttack(player);
            return InteractionResult.PASS;
        });
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                CombatVanillaRules.clearPlayerState(handler.getPlayer()));
        // Send the server's weapon specs on join and after /reload, so clients show the same stats.
        PayloadTypeRegistry.playS2C().register(com.craisinlord.fightclub.combat.WeaponSpecSyncPayload.TYPE,
                com.craisinlord.fightclub.combat.WeaponSpecSyncPayload.STREAM_CODEC);
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (ServerPlayNetworking.canSend(handler.getPlayer(), com.craisinlord.fightclub.combat.WeaponSpecSyncPayload.TYPE)) {
                ServerPlayNetworking.send(handler.getPlayer(), com.craisinlord.fightclub.combat.WeaponSpecSyncPayload.current());
            }
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
            for (var player : server.getPlayerList().getPlayers()) {
                if (ServerPlayNetworking.canSend(player, com.craisinlord.fightclub.combat.WeaponSpecSyncPayload.TYPE)) {
                    ServerPlayNetworking.send(player, com.craisinlord.fightclub.combat.WeaponSpecSyncPayload.current());
                }
            }
        });
        // Longsword offhand penalty follows the offhand live, not just at the moment of attacking.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var player : server.getPlayerList().getPlayers()) {
                CombatVanillaRules.applyLongswordRules(player);
                CombatChargeState.tick(player);
            }
        });
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                CombatVanillaRules.clearPlayerState(newPlayer));
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (REDUCED_DAMAGE.get() || !(entity instanceof net.minecraft.world.entity.player.Player player)) return true;
            if (!(source.getDirectEntity() instanceof net.minecraft.world.entity.LivingEntity attacker)
                    || !CombatVanillaRules.swordBlocks(player, attacker)) return true;
            CombatVanillaRules.playSwordBlockEffects(player, attacker);
            REDUCED_DAMAGE.set(true);
            try {
                entity.hurt(source, amount * CombatVanillaRules.swordBlockDamageMultiplier(player.getUseItem()));
            } finally {
                REDUCED_DAMAGE.set(false);
            }
            return false;
        });
    }
}
