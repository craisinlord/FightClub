package com.craisinlord.fightclub.combat;

import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;

public final class KnifeThrowGoal extends Goal {
    private static final double MIN_RANGE_SQR = 16.0D;
    private static final double MAX_RANGE_SQR = 196.0D;
    private static final int WINDUP_TICKS = 12;
    private static final float MAX_MOB_VELOCITY = 2.2F;
    private final PathfinderMob mob;
    private boolean thrown;
    private int windup;
    private int nextThrow;

    public KnifeThrowGoal(PathfinderMob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override public boolean canUse() {
        LivingEntity target = mob.getTarget();
        if (!MobWeapons.skeletonKnives() || target == null || !target.isAlive() || mob.tickCount < nextThrow) return false;
        if (!MobWeapons.isDagger(mob.getMainHandItem())) return false;
        double distance = mob.distanceToSqr(target);
        return distance >= MIN_RANGE_SQR && distance <= MAX_RANGE_SQR && mob.getSensing().hasLineOfSight(target);
    }

    @Override public boolean canContinueToUse() {
        if (thrown) return false;
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive() && MobWeapons.isDagger(mob.getMainHandItem());
    }

    @Override public void start() {
        windup = WINDUP_TICKS;
        thrown = false;
        mob.getNavigation().stop();
    }

    @Override public void stop() {
        thrown = false;
        mob.getMoveControl().strafe(0.0F, 0.0F);
    }

    @Override public boolean requiresUpdateEveryTick() { return true; }

    @Override public void tick() {
        LivingEntity target = mob.getTarget();
        if (target != null) mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (thrown) return;
        if (target == null || --windup > 0) return;
        try {
            throwAt(target);
        } catch (RuntimeException e) {
            MobWeapons.warn(e);
            nextThrow = mob.tickCount + 200;
        }
    }

    private void throwAt(LivingEntity target) {
        ItemStack stack = mob.getMainHandItem();
        WeaponSpec spec = CombatWeapons.is(stack.getItem(), "dagger") ? WeaponSpecs.getOrLoadBuiltin(CombatWeapons.material(stack.getItem()), "dagger") : null;
        float velocity = Math.min(MAX_MOB_VELOCITY, spec == null ? 1.5F : spec.throwVelocity());
        DaggerProjectile projectile = new DaggerProjectile(mob.level(), mob, stack.copy());
        double dx = target.getX() - mob.getX();
        double dy = target.getY(0.5D) - projectile.getY();
        double dz = target.getZ() - mob.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float inaccuracy = switch (mob.level().getDifficulty()) {
            case HARD -> 2.0F;
            case NORMAL -> 6.0F;
            default -> 10.0F;
        };
        double flightTicks = horizontal / velocity;
        projectile.shoot(dx, dy + 0.5D * DaggerProjectile.GRAVITY * flightTicks * flightTicks, dz, velocity, inaccuracy);
        mob.level().addFreshEntity(projectile);
        mob.level().playSound(null, mob, CombatSounds.DAGGER_THROW, SoundSource.HOSTILE, 1.0F, 0.95F + mob.getRandom().nextFloat() * 0.1F);
        mob.swing(InteractionHand.MAIN_HAND);
        thrown = true;
        nextThrow = mob.tickCount + 40 + mob.getRandom().nextInt(20);
    }
}
