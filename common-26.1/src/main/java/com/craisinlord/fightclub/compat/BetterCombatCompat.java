package com.craisinlord.fightclub.compat;

import com.craisinlord.fightclub.FightClub;
import com.craisinlord.fightclub.combat.CombatPlatform;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Method;

/**
 * Optional Better Combat integration. 26.x has no Better Combat build on the compile classpath,
 * so its public CombatFlags API is reached reflectively, and only when the mod is installed.
 */
public final class BetterCombatCompat {
    public static final String MOD_ID = "bettercombat";
    private static Boolean loaded;
    private static Method setAttacksDisabled;
    private static boolean lookupFailed;

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
        if (!isLoaded() || !(player instanceof ServerPlayer serverPlayer) || lookupFailed) return;
        try {
            if (setAttacksDisabled == null) {
                setAttacksDisabled = Class.forName("net.bettercombat.api.CombatFlags")
                        .getMethod("setAttacksDisabled", ServerPlayer.class, boolean.class);
            }
            setAttacksDisabled.invoke(null, serverPlayer, active);
        } catch (ReflectiveOperationException | LinkageError exception) {
            lookupFailed = true;
            FightClub.LOGGER.warn("Better Combat CombatFlags API unavailable: {}", exception.toString());
        }
    }
}
