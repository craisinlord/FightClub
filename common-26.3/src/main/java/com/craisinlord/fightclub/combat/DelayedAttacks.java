package com.craisinlord.fightclub.combat;

import com.craisinlord.fightclub.compat.BetterCombatCompat;
import com.craisinlord.fightclub.mixin.AttackTickerAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DelayedAttacks {
    public static final int MACE_IMPACT_TICKS = 5;
    private static final double MAX_RANGE = 7.0;

    private record Pending(Entity target, ItemStack stack, int ticker, long fireTick) {}

    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    private static final Set<UUID> FIRING = ConcurrentHashMap.newKeySet();

    private DelayedAttacks() {}

    public static boolean defer(Player player, Entity target) {
        if (!(player.level() instanceof ServerLevel level) || FIRING.contains(player.getUUID())) return false;
        if (LongswordSwings.startWithTarget(player, target)) return true;
        int delay = delayTicks(player);
        if (delay <= 0) return false;
        fire(player);
        int ticker = ((AttackTickerAccessor) player).fightClub$getAttackStrengthTicker();
        PENDING.put(player.getUUID(), new Pending(target, player.getMainHandItem(), ticker, level.getGameTime() + delay));
        return true;
    }

    private static int delayTicks(Player player) {
        if (BetterCombatCompat.isLoaded() || !CombatWeapons.isMace(player.getMainHandItem().getItem())) return 0;
        if (MaceItem.canSmashAttack(player)) return 0;
        return MACE_IMPACT_TICKS;
    }

    public static void tick(Player player) {
        if (!(player.level() instanceof ServerLevel)) return;
        LongswordSwings.tick(player);
        Pending pending = PENDING.get(player.getUUID());
        if (pending != null && player.level().getGameTime() >= pending.fireTick()) fire(player);
    }

    private static void fire(Player player) {
        Pending pending = PENDING.remove(player.getUUID());
        if (pending == null) return;
        Entity target = pending.target();
        if (player.getMainHandItem() != pending.stack() || !player.isAlive() || target.isRemoved()
                || target.level() != player.level() || player.distanceTo(target) > MAX_RANGE) return;
        AttackTickerAccessor ticker = (AttackTickerAccessor) player;
        int elapsed = ticker.fightClub$getAttackStrengthTicker() - pending.ticker();
        ticker.fightClub$setAttackStrengthTicker(pending.ticker());
        attackNow(player, target);
        ticker.fightClub$setAttackStrengthTicker(Math.max(0, elapsed));
    }

    public static boolean firing(Player player) {
        return FIRING.contains(player.getUUID());
    }

    public static void attackNow(Player player, Entity target) {
        FIRING.add(player.getUUID());
        try {
            player.attack(target);
        } finally {
            FIRING.remove(player.getUUID());
        }
    }

    public static void clear(Player player) {
        PENDING.remove(player.getUUID());
        FIRING.remove(player.getUUID());
    }
}
