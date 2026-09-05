package com.craisinlord.combatoverhaul.combat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

public final class ThrustTargeting {
    private ThrustTargeting() {}

    public static List<LivingEntity> find(Player attacker, LivingEntity primary, WeaponSpec spec) {
        Vec3 origin = attacker.getEyePosition();
        Vec3 direction = attacker.getLookAngle().normalize();
        double reach = spec.reach();
        AABB search = attacker.getBoundingBox().expandTowards(direction.scale(reach)).inflate(spec.thrustWidth(), 1.0, spec.thrustWidth());

        return attacker.level().getEntitiesOfClass(LivingEntity.class, search, target -> {
                    if (target == attacker || target == primary || target.isRemoved() || target.isDeadOrDying()) return false;
                    Vec3 offset = target.getBoundingBox().getCenter().subtract(origin);
                    double along = offset.dot(direction);
                    if (along < 0.0 || along > reach) return false;
                    double lateralSquared = offset.lengthSqr() - (along * along);
                    return lateralSquared <= Math.max(0.25, spec.thrustWidth() * spec.thrustWidth());
                }).stream()
                .sorted(Comparator.comparingDouble(target -> target.distanceToSqr(attacker)))
                .limit(Math.max(1, spec.penetrationCount()))
                .toList();
    }
}
