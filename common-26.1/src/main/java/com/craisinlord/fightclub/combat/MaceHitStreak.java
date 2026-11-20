package com.craisinlord.fightclub.combat;

import com.craisinlord.fightclub.CombatConfig;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mace hit streak (server side): every mace hit that lands adds {@code hit_streak_speed_bonus}
 * attack speed, up to {@code hit_streak_max_stacks}, as a transient ATTACK_SPEED modifier (the same
 * way the longsword offhand penalty works; vanilla syncs it to the client's attack indicator).
 * The streak ends on a miss (reported by the client, see MinecraftInputMixin), when no hit lands
 * within {@code hit_streak_window_ticks} of the attack cooldown filling up again, or when the
 * player stops holding the mace.
 */
public final class MaceHitStreak {
    private static final Identifier MODIFIER = Identifier.fromNamespaceAndPath("fight_club", "mace_hit_streak");
    private record Streak(int stacks, long expireTick) {}
    private static final Map<UUID, Streak> STREAKS = new ConcurrentHashMap<>();

    private MaceHitStreak() {}

    /** A mace attack landed (MaceItem.hurtEnemy). */
    public static void onHit(Player player) {
        if (player.level().isClientSide() || !CombatConfig.enabled(CombatConfig.MACE_HIT_STREAK)
                || !player.getMainHandItem().is(Items.MACE)) return;
        WeaponSpec spec = WeaponSpecs.getOrLoadBuiltin("base", "mace");
        if (spec == null || spec.hitStreakSpeedBonus() <= 0.0f || spec.hitStreakMaxStacks() <= 0) return;
        Streak previous = STREAKS.get(player.getUUID());
        int stacks = Math.min(spec.hitStreakMaxStacks(), previous == null ? 1 : previous.stacks() + 1);
        AttributeInstance speed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (speed == null) return;
        speed.removeModifier(MODIFIER);
        speed.addTransientModifier(new AttributeModifier(MODIFIER, spec.hitStreakSpeedBonus() * stacks,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        // The window starts once the (now faster) attack cooldown is full again.
        long expire = player.level().getGameTime() + (long) Math.ceil(player.getCurrentItemAttackStrengthDelay())
                + spec.hitStreakWindowTicks();
        STREAKS.put(player.getUUID(), new Streak(stacks, expire));
    }

    /** A swing that hit nothing. */
    public static void onMiss(Player player) {
        if (player.getMainHandItem().is(Items.MACE)) reset(player);
    }

    /** Called every server tick for each player. */
    public static void tick(Player player) {
        Streak streak = STREAKS.get(player.getUUID());
        if (streak == null) return;
        if (!player.getMainHandItem().is(Items.MACE) || player.level().getGameTime() > streak.expireTick()
                || !CombatConfig.enabled(CombatConfig.MACE_HIT_STREAK)) {
            reset(player);
        }
    }

    public static void reset(Player player) {
        STREAKS.remove(player.getUUID());
        AttributeInstance speed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (speed != null) speed.removeModifier(MODIFIER);
    }
}
