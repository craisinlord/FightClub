package com.craisinlord.fightclub.combat;

import com.craisinlord.fightclub.CombatConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CombatAttacks {
    private static final ResourceLocation CHARGE_BONUS =
            ResourceLocation.fromNamespaceAndPath("fight_club", "charge_bonus");
    private static final Map<UUID, Float> ATTACK_STRENGTH = new ConcurrentHashMap<>();

    private CombatAttacks() {}

    public static void captureStrength(Player player) {
        ATTACK_STRENGTH.put(player.getUUID(), player.getAttackStrengthScale(0.5F));
    }

    public static float capturedStrength(Player player) {
        Float strength = ATTACK_STRENGTH.get(player.getUUID());
        return strength == null ? 1.0F : strength;
    }

    public static void attackWithBonus(Player player, LivingEntity target, float bonus) {
        AttributeInstance damage = player.getAttribute(Attributes.ATTACK_DAMAGE);
        boolean applied = damage != null && bonus > 0.0F;
        if (applied) {
            damage.removeModifier(CHARGE_BONUS);
            damage.addTransientModifier(new AttributeModifier(CHARGE_BONUS, bonus, AttributeModifier.Operation.ADD_VALUE));
        }
        captureStrength(player);
        try {
            player.attack(target);
        } finally {
            if (applied) damage.removeModifier(CHARGE_BONUS);
        }
    }

    public static void pierce(Player player, LivingEntity primary, WeaponSpec spec) {
        if (spec.penetrationCount() <= 0 || !CombatConfig.enabled(CombatConfig.PENETRATION)) return;
        float strength = capturedStrength(player);
        float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * (0.2F + strength * strength * 0.8F);
        if (damage <= 0.0F) return;
        DamageSource source = player.damageSources().playerAttack(player);
        for (LivingEntity pierced : ThrustTargeting.find(player, primary, spec)) {
            if (player.isAlliedTo(pierced)) continue;
            if (pierced.hurt(source, damage) && player.level() instanceof ServerLevel serverLevel) {
                EnchantmentHelper.doPostAttackEffects(serverLevel, pierced, source);
                com.craisinlord.fightclub.compat.CompatAbilities.onHit(player.getMainHandItem(), pierced, player,
                        com.craisinlord.fightclub.compat.CompatAbilities.Hit.PIERCE);
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                        pierced.getX(), pierced.getY(0.6), pierced.getZ(), 5,
                        pierced.getBbWidth() * 0.25, pierced.getBbHeight() * 0.15, pierced.getBbWidth() * 0.25, 0.2);
            }
        }
    }

    public static void sweep(Player player, LivingEntity primary, WeaponSpec spec) {
        if (spec.sweepRadius() <= 0.0F || !CombatConfig.enabled(CombatConfig.DATA_SWEEPS) || !(player.level() instanceof ServerLevel level)) return;
        float strength = capturedStrength(player);
        if (strength < 0.9F || !player.onGround() || player.isSprinting()) return;
        double radius = spec.sweepRadius();
        double maxDistance = player.entityInteractionRange() + radius;
        float damage = (1.0F + (float) (player.getAttributeValue(Attributes.SWEEPING_DAMAGE_RATIO)
                * player.getAttributeValue(Attributes.ATTACK_DAMAGE))) * strength;
        DamageSource source = player.damageSources().playerAttack(player);
        float yaw = player.getYRot() * net.minecraft.util.Mth.DEG_TO_RAD;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, primary.getBoundingBox().inflate(radius, 0.25, radius))) {
            if (target == player || target == primary || player.isAlliedTo(target) || !target.isAlive()) continue;
            if (target instanceof net.minecraft.world.entity.decoration.ArmorStand stand && stand.isMarker()) continue;
            if (player.distanceToSqr(target) > maxDistance * maxDistance) continue;
            if (!CombatVanillaRules.frontal(player, target, spec.sweepAngleDegrees())) continue;
            target.knockback(0.4, net.minecraft.util.Mth.sin(yaw), -net.minecraft.util.Mth.cos(yaw));
            if (target.hurt(source, damage)) EnchantmentHelper.doPostAttackEffects(level, target, source);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP,
                player.getSoundSource(), 1.0F, 1.0F);
        player.sweepAttack();
        if (radius > 1.25) widerSweepParticles(player);
    }

    private static void widerSweepParticles(Player player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        double yaw = Math.toRadians(player.getYRot());
        double forwardX = -Math.sin(yaw), forwardZ = Math.cos(yaw);
        double rightX = -forwardZ, rightZ = forwardX;
        double y = player.getY(0.5);
        for (int side = -1; side <= 1; side += 2) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK,
                    player.getX() + forwardX * 1.1 + rightX * side * 0.95, y,
                    player.getZ() + forwardZ * 1.1 + rightZ * side * 0.95, 0, -0.2, 0.0, 0.0, 0.0);
        }
    }

    public static void clear(Player player) {
        ATTACK_STRENGTH.remove(player.getUUID());
    }
}
