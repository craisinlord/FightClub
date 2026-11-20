package com.craisinlord.fightclub.compat;

import com.craisinlord.fightclub.combat.CombatPlatform;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Optional Better Combat integration. Every Better Combat reference lives in the nested
 * {@link Api} class, which the JVM only loads when Better Combat is actually installed, so the
 * mod runs normally without it.
 */
public final class BetterCombatCompat {
    public static final String MOD_ID = "bettercombat";
    private static Boolean loaded;

    private BetterCombatCompat() {}

    public static boolean isLoaded() {
        if (loaded == null) loaded = CombatPlatform.isModLoaded(MOD_ID);
        return loaded;
    }

    /**
     * Pauses Better Combat's own swings (including hold-to-attack) while a Fight Club charge
     * is held, through Better Combat's public CombatFlags API.
     */
    public static void setChargeActive(Player player, boolean active) {
        if (isLoaded() && player instanceof ServerPlayer serverPlayer) Api.setAttacksDisabled(serverPlayer, active);
    }

    /**
     * Client side: pauses Better Combat's swings on our own player right away, without waiting for
     * the server's flag to sync back. Better Combat's client checks this flag before every swing,
     * including hold-to-attack, so a held spear can never slip a swing in before the charge starts.
     * The server only syncs the flag when its own value changes, so the local value stands until then.
     */
    public static void setLocalAttacksPaused(Player player, boolean paused) {
        if (isLoaded() && player != null && player.level().isClientSide()) Api.setLocalPaused(player, paused);
    }

    private static int clientAttackStarts;
    private static boolean listening;

    /**
     * Client: how many Better Combat swings have started on our own player so far. Compare before
     * and after a startAttack call to tell whether Better Combat accepted it: it silently drops
     * clicks while the weapon is still recovering or a swing is winding up.
     */
    public static int clientAttackStarts() {
        if (isLoaded() && !listening) {
            listening = true;
            ClientApi.listenForAttackStarts();
        }
        return clientAttackStarts;
    }

    /** Client-only Better Combat references, kept apart so a dedicated server never loads them. */
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
