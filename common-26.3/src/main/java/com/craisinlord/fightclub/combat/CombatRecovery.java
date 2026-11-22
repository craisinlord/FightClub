package com.craisinlord.fightclub.combat;

import com.craisinlord.fightclub.CombatConfig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

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
        start(player, ticks, player.getMainHandItem());
    }

    public static void start(Player player, int ticks, ItemStack weapon) {
        if (ticks <= 0 || !CombatConfig.enabled(CombatConfig.RECOVERY)) return;
        END_TICKS.put(player.getUUID(), player.level().getGameTime() + ticks);
        if (!weapon.isEmpty()) player.getCooldowns().addCooldown(weapon, ticks);
    }

    public static void clear(Player player) {
        END_TICKS.remove(player.getUUID());
    }
}
