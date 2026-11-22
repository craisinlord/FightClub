package com.craisinlord.fightclub.combat;

import com.craisinlord.fightclub.CombatConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MaceHitStreak {
    private static final ResourceLocation MODIFIER = ResourceLocation.fromNamespaceAndPath("fight_club", "mace_hit_streak");
    private record Streak(int stacks, long expireTick) {}
    private static final Map<UUID, Streak> STREAKS = new ConcurrentHashMap<>();

    private MaceHitStreak() {}

    public static void onHit(Player player) {
        if (player.level().isClientSide || !CombatConfig.enabled(CombatConfig.MACE_HIT_STREAK)
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
        long expire = player.level().getGameTime() + (long) Math.ceil(player.getCurrentItemAttackStrengthDelay())
                + spec.hitStreakWindowTicks();
        STREAKS.put(player.getUUID(), new Streak(stacks, expire));
    }

    public static void onMiss(Player player) {
        if (player.getMainHandItem().is(Items.MACE)) reset(player);
    }

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
