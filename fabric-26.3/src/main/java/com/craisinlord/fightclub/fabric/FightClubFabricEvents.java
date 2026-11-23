package com.craisinlord.fightclub.fabric;

import com.craisinlord.fightclub.combat.CombatVanillaRules;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import com.craisinlord.fightclub.combat.CombatWeaponItem;
import com.craisinlord.fightclub.combat.CombatActionPayload;
import com.craisinlord.fightclub.combat.CombatChargeState;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class FightClubFabricEvents {
    private static final ThreadLocal<Boolean> REDUCED_DAMAGE = ThreadLocal.withInitial(() -> false);

    private FightClubFabricEvents() {}

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(CombatActionPayload.TYPE, CombatActionPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(com.craisinlord.fightclub.combat.CombatPosePayload.TYPE,
                com.craisinlord.fightclub.combat.CombatPosePayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(com.craisinlord.fightclub.combat.WeaponItemsPayload.TYPE,
                com.craisinlord.fightclub.combat.WeaponItemsPayload.STREAM_CODEC);
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                com.craisinlord.fightclub.combat.CombatPlatform.sendToPlayer(handler.player,
                        com.craisinlord.fightclub.combat.WeaponItemsPayload.current()));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
            var payload = com.craisinlord.fightclub.combat.WeaponItemsPayload.current();
            for (var player : server.getPlayerList().getPlayers()) com.craisinlord.fightclub.combat.CombatPlatform.sendToPlayer(player, payload);
        });
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            CombatChargeState.clear(handler.player);
            com.craisinlord.fightclub.combat.CombatRecovery.clear(handler.player);
        });
        ServerPlayNetworking.registerGlobalReceiver(CombatActionPayload.TYPE,
                (payload, context) -> context.server().execute(() ->
                        CombatChargeState.handle(context.player(), payload.action(), payload.target())));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var player : server.getPlayerList().getPlayers()) CombatChargeState.tick(player);
        });
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (hand == net.minecraft.world.InteractionHand.MAIN_HAND && stack.getItem() instanceof CombatWeaponItem weapon
                    && weapon.combatWeapon().equals("sword")
                    && com.craisinlord.fightclub.combat.CombatWeapons.isShield(player.getOffhandItem())) return InteractionResult.PASS;
            if (hand == net.minecraft.world.InteractionHand.MAIN_HAND && stack.getItem() instanceof CombatWeaponItem weapon
                    && weapon.combatWeapon().equals("sword")) {
                player.startUsingItem(hand);
                return InteractionResult.CONSUME;
            }
            return InteractionResult.PASS;
        });
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            CombatVanillaRules.applyLongswordRules(player);
            if (CombatVanillaRules.shieldBash(player, entity)) {
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
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
