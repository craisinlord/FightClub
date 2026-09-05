package com.craisinlord.combatoverhaul.combat;

import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CombatRecovery {
    private static final Map<UUID, Long> END_TICKS = new ConcurrentHashMap<>();

    private CombatRecovery() {}

    public static boolean active(Player player) {
        long now = player.level().getGameTime();
        Long end = END_TICKS.get(player.getUUID());
        if (end == null) return false;
        if (end <= now) {
            END_TICKS.remove(player.getUUID());
            return false;
        }
        return true;
    }

    public static void start(Player player, int ticks) {
        if (ticks > 0) END_TICKS.put(player.getUUID(), player.level().getGameTime() + ticks);
    }

    public static void clear(Player player) {
        END_TICKS.remove(player.getUUID());
    }
}
