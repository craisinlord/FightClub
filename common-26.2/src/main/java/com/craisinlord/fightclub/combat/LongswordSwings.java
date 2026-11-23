package com.craisinlord.fightclub.combat;

import com.craisinlord.fightclub.mixin.AttackTickerAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LongswordSwings {
    private static final float SWEEP_START = 2.0f, SWEEP_END = 10.0f, LATE_WINDOW = 0.2f;
    private static final long BACKHAND_WINDOW_TICKS = 30;
    private static final double DIRECT_SLACK = 1.0;
    private static final float SPIN_START = 1.8f, SPIN_END = 12.75f, SPIN_LEAD_ANGLE = 45.0f;

    private static final class Swing {
        final ItemStack stack;
        final float base;
        final boolean rightToLeft;
        final long start;
        final Entity direct;
        final int ticker;
        final boolean punch;
        final Set<Integer> hit = new HashSet<>();
        boolean directDone, landed, spin;
        float covered = -LATE_WINDOW;

        Swing(ItemStack stack, float base, boolean rightToLeft, long start, Entity direct, int ticker, boolean punch) {
            this.stack = stack;
            this.base = base;
            this.rightToLeft = rightToLeft;
            this.start = start;
            this.direct = direct;
            this.ticker = ticker;
            this.punch = punch;
        }
    }

    private record Side(long lastStart, boolean backhand) {}

    private static final Map<UUID, Swing> SWINGS = new ConcurrentHashMap<>();
    private static final Map<UUID, Side> SIDES = new ConcurrentHashMap<>();

    private LongswordSwings() {}

    public static boolean startWithTarget(Player player, Entity target) {
        if (!LongswordSpin.sweepsArc(player)) return false;
        float strength = player.getAttackStrengthScale(0.5f);
        AttackTickerAccessor ticker = (AttackTickerAccessor) player;
        int captured = ticker.fightClub$getAttackStrengthTicker();
        if (!begin(player, strength, target, captured)) return false;
        ticker.fightClub$setAttackStrengthTicker(0);
        LongswordSpin.markAttacked(player);
        return true;
    }

    public static void startSpin(Player player, ItemStack stack) {
        if (!(player.level() instanceof ServerLevel level)) return;
        Swing swing = new Swing(stack, 0.0f, player.getMainArm() == HumanoidArm.RIGHT, level.getGameTime(), null, 0, false);
        swing.spin = true;
        SWINGS.put(player.getUUID(), swing);
    }

    public static void start(Player player, float strength, Entity direct) {
        begin(player, strength, direct, 0);
    }

    private static boolean begin(Player player, float strength, Entity direct, int ticker) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        ItemStack stack = player.getMainHandItem();
        if (LongswordSpin.spec(stack) == null) return false;
        long now = level.getGameTime();
        Side previous = SIDES.get(player.getUUID());
        boolean backhand = previous != null && now - previous.lastStart() <= BACKHAND_WINDOW_TICKS && !previous.backhand();
        SIDES.put(player.getUUID(), new Side(now, backhand));
        boolean rightToLeft = (player.getMainArm() == HumanoidArm.RIGHT) != backhand;
        Swing swing = new Swing(stack, LongswordSpin.arcBase(player, strength), rightToLeft, now, direct, ticker, direct == null);
        if (direct != null) swing.hit.add(direct.getId());
        SWINGS.put(player.getUUID(), swing);
        return true;
    }

    public static void tick(Player player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        Swing swing = SWINGS.get(player.getUUID());
        if (swing == null) return;
        WeaponSpec spec = LongswordSpin.spec(swing.stack);
        if (spec == null || player.getMainHandItem() != swing.stack || !player.isAlive()) {
            SWINGS.remove(player.getUUID());
            return;
        }
        float elapsed = level.getGameTime() - swing.start;
        float progress;
        double half, range;
        if (swing.spin) {
            float t = Mth.clamp((elapsed - SPIN_START) / (SPIN_END - SPIN_START), 0.0f, 1.0f);
            progress = t * t * (3.0f - 2.0f * t);
            half = 180.0;
            range = spec.spinRadius();
        } else {
            progress = Mth.clamp((elapsed - SWEEP_START) / (SWEEP_END - SWEEP_START), 0.0f, 1.0f);
            half = spec.sweepAngleDegrees() * 0.5;
            range = LongswordSpin.arcRange(spec);
        }
        if (progress <= 0.0f) return;

        if (swing.direct != null && !swing.directDone) {
            Entity direct = swing.direct;
            if (direct.isRemoved() || direct.level() != level) {
                swing.directDone = true;
            } else if (Mth.clamp(position(player, direct, half, swing.rightToLeft), 0.0f, 1.0f) <= progress) {
                swing.directDone = true;
                if (direct instanceof LivingEntity living ? LongswordSpin.horizontalGap(player, living) <= spec.reach() + DIRECT_SLACK
                        : player.distanceTo(direct) <= spec.reach() + DIRECT_SLACK) {
                    fireDirect(player, swing, direct);
                }
            }
        }

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range, swing.spin ? 1.0 : 0.5, range))) {
            if (swing.hit.contains(target.getId()) || !LongswordSpin.validTarget(player, target)
                    || LongswordSpin.horizontalGap(player, target) > range) continue;
            float position = swing.spin ? spinPosition(player, target, swing.rightToLeft) : position(player, target, half, swing.rightToLeft);
            float width = width(player, target, half);
            float lead = position - width, trail = position + width;
            if (trail < 0.0f || lead > 1.0f || lead > progress || trail < swing.covered - LATE_WINDOW) continue;
            swing.hit.add(target.getId());
            if (swing.spin) {
                LongswordSpin.spinHit(player, swing.stack, target);
                landed(player, swing);
            } else if (LongswordSpin.arcHit(player, swing.stack, target, swing.base)) {
                landed(player, swing);
            }
        }
        swing.covered = progress;
        if (progress >= 1.0f && (swing.direct == null || swing.directDone)) SWINGS.remove(player.getUUID());
    }

    private static void fireDirect(Player player, Swing swing, Entity target) {
        AttackTickerAccessor ticker = (AttackTickerAccessor) player;
        int current = ticker.fightClub$getAttackStrengthTicker();
        ticker.fightClub$setAttackStrengthTicker(swing.ticker);
        DelayedAttacks.attackNow(player, target);
        ticker.fightClub$setAttackStrengthTicker(current);
        landed(player, swing);
    }

    private static void landed(Player player, Swing swing) {
        if (swing.landed) return;
        swing.landed = true;
        if (swing.spin) LongswordSpin.spinLanded(player, swing.stack);
        else LongswordSpin.arcLanded(player, swing.stack, swing.punch);
    }

    private static float position(Player player, Entity target, double half, boolean rightToLeft) {
        double dx = target.getX() - player.getX(), dz = target.getZ() - player.getZ();
        if (dx * dx + dz * dz < 1.0e-4) return 0.5f;
        double yaw = player.getYRot() * Mth.DEG_TO_RAD;
        double forward = -Math.sin(yaw) * dx + Math.cos(yaw) * dz;
        double right = -Math.cos(yaw) * dx - Math.sin(yaw) * dz;
        double angle = Math.toDegrees(Math.atan2(right, forward));
        double t = (angle + half) / (2.0 * half);
        return (float) (rightToLeft ? 1.0 - t : t);
    }

    private static float spinPosition(Player player, Entity target, boolean leftward) {
        double dx = target.getX() - player.getX(), dz = target.getZ() - player.getZ();
        if (dx * dx + dz * dz < 1.0e-4) return 0.0f;
        double yaw = player.getYRot() * Mth.DEG_TO_RAD;
        double forward = -Math.sin(yaw) * dx + Math.cos(yaw) * dz;
        double right = -Math.cos(yaw) * dx - Math.sin(yaw) * dz;
        double angle = Math.toDegrees(Math.atan2(right, forward));
        double travelled = leftward ? SPIN_LEAD_ANGLE - angle : angle + SPIN_LEAD_ANGLE;
        return (float) ((travelled % 360.0 + 360.0) % 360.0 / 360.0);
    }

    private static float width(Player player, Entity target, double half) {
        double dx = target.getX() - player.getX(), dz = target.getZ() - player.getZ();
        double distance = Math.max(0.5, Math.sqrt(dx * dx + dz * dz));
        return (float) (Math.toDegrees(Math.atan2(target.getBbWidth() * 0.5, distance)) / (2.0 * half));
    }

    public static void clear(Player player) {
        SWINGS.remove(player.getUUID());
        SIDES.remove(player.getUUID());
    }
}
