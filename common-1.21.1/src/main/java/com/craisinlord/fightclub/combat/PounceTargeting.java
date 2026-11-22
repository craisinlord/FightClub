package com.craisinlord.fightclub.combat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;

public final class PounceTargeting {
    private static final double PASSIVE_ANGLE_DEGREES = 12.0;
    private static final double DISTANCE_WEIGHT = 0.35;

    private PounceTargeting() {}

    public static LivingEntity find(Player player, WeaponSpec spec) {
        double range = spec.pounceRange();
        AABB search = player.getBoundingBox().inflate(range + 1.0);
        return player.level().getEntitiesOfClass(LivingEntity.class, search,
                        target -> valid(player, target, range, spec.pounceAngleDegrees()))
                .stream()
                .min(Comparator.comparingDouble(target -> score(player, target, range)))
                .orElse(null);
    }

    public static boolean validForServer(Player player, LivingEntity target, WeaponSpec spec) {
        return valid(player, target, spec.pounceRange() + 2.0, spec.pounceAngleDegrees() + 15.0);
    }

    private static boolean valid(Player player, LivingEntity target, double range, double angleDegrees) {
        if (target == player || !target.isAlive() || target.isSpectator() || !target.isAttackable()
                || target instanceof ArmorStand || target.isPassengerOfSameVehicle(player)) return false;
        if (target instanceof OwnableEntity pet && player.getUUID().equals(pet.getOwnerUUID())) return false;
        if (target instanceof Player other && !player.canHarmPlayer(other)) return false;
        if (distance(player, target) > range) return false;
        double limit = target instanceof Enemy ? angleDegrees : Math.min(angleDegrees, PASSIVE_ANGLE_DEGREES);
        if (Math.toDegrees(angle(player, target)) > limit) return false;
        return lineOfSight(player, target);
    }

    private static double score(Player player, LivingEntity target, double range) {
        return angle(player, target) + DISTANCE_WEIGHT * distance(player, target) / Math.max(1.0, range);
    }

    private static double distance(Player player, Entity target) {
        return Math.max(0.0, player.getEyePosition().distanceTo(target.getBoundingBox().getCenter()) - target.getBbWidth() * 0.5);
    }

    private static double angle(Player player, Entity target) {
        Vec3 toTarget = target.getBoundingBox().getCenter().subtract(player.getEyePosition());
        if (toTarget.lengthSqr() < 1.0E-6) return 0.0;
        double dot = toTarget.normalize().dot(player.getLookAngle().normalize());
        return Math.acos(Math.max(-1.0, Math.min(1.0, dot)));
    }

    private static boolean lineOfSight(Player player, Entity target) {
        Vec3 eye = player.getEyePosition();
        for (Vec3 point : new Vec3[]{target.getEyePosition(), target.getBoundingBox().getCenter()}) {
            HitResult hit = player.level().clip(new ClipContext(eye, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.MISS) return true;
        }
        return false;
    }
}
