package com.craisinlord.fightclub.compat;

import com.craisinlord.fightclub.combat.CombatPlatform;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public final class BetterCombatCompat {
    public static final String MOD_ID = "bettercombat";
    private static Boolean loaded;

    private BetterCombatCompat() {}

    public static boolean isLoaded() {
        if (loaded == null) loaded = CombatPlatform.isModLoaded(MOD_ID);
        return loaded;
    }

    public static void setChargeActive(Player player, boolean active) {
        if (isLoaded() && player instanceof ServerPlayer serverPlayer) Api.setAttacksDisabled(serverPlayer, active);
    }

    public static void setLocalAttacksPaused(Player player, boolean paused) {
        if (isLoaded() && player != null && player.level().isClientSide()) Api.setLocalPaused(player, paused);
    }

    private static int clientAttackStarts;
    private static boolean listening;

    public static int clientAttackStarts() {
        if (isLoaded() && !listening) {
            listening = true;
            ClientApi.listenForAttackStarts();
        }
        return clientAttackStarts;
    }

    private static final class ClientApi {
        static void listenForAttackStarts() {
            net.bettercombat.api.client.BetterCombatClientEvents.ATTACK_START.register((player, hand) -> clientAttackStarts++);
        }
    }

    private static final class Api {
        static void setAttacksDisabled(ServerPlayer player, boolean disabled) {
            net.bettercombat.api.CombatFlags.setAttacksDisabled(player, disabled);
        }

        static void setLocalPaused(Player player, boolean paused) {
            net.bettercombat.api.EntityPlayer_BetterCombat flagged = (net.bettercombat.api.EntityPlayer_BetterCombat) player;
            byte flags = flagged.getCombatFlags();
            byte bit = (byte) net.bettercombat.api.CombatFlags.API_DISABLED;
            byte next = (byte) (paused ? flags | bit : flags & ~bit);
            if (next != flags) flagged.setCombatFlags(next);
        }
    }
}
