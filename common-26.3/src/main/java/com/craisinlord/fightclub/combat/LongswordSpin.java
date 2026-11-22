package com.craisinlord.fightclub.combat;

import com.craisinlord.fightclub.CombatConfig;
import com.craisinlord.fightclub.compat.BetterCombatCompat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LongswordSpin {
    private static final Map<UUID, Long> SPIN_READY = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_ATTACK = new ConcurrentHashMap<>();

    private LongswordSpin() {}

    public static boolean isLongsword(ItemStack stack) {
        return CombatWeapons.is(stack.getItem(), "longsword");
    }

    public static WeaponSpec spec(ItemStack stack) {
        return isLongsword(stack) ? WeaponSpecs.getOrLoadBuiltin(CombatWeapons.material(stack.getItem()), "longsword") : null;
    }

    public static boolean hasSpin(ItemStack stack) {
        WeaponSpec spec = spec(stack);
        return spec != null && CombatConfig.enabled(CombatConfig.LONGSWORD_SPIN) && spec.chargeTicks() > 0 && spec.spinRadius() > 0.0f;
    }

    public static int chargeTicks(ItemStack stack) {
        WeaponSpec spec = spec(stack);
        return spec == null ? 20 : Math.max(1, spec.chargeTicks());
    }

    public static boolean onSpinCooldown(Player player) {
        Long ready = SPIN_READY.get(player.getUUID());
        if (ready == null) return false;
        if (player.level().getGameTime() >= ready) {
            SPIN_READY.remove(player.getUUID());
            return false;
        }
        return true;
    }

    public static void release(Player player, ItemStack stack, int heldTicks) {
        WeaponSpec spec = spec(stack);
        if (spec == null || !hasSpin(stack) || heldTicks < spec.chargeTicks() || onSpinCooldown(player)
                || !(player.level() instanceof ServerLevel level)) return;
        CombatPosePayload pose = new CombatPosePayload(player.getId(), CombatPosePayload.SPIN);
        CombatPlatform.sendPoseToTracking(player, pose);
        CombatPlatform.sendToPlayer(player, pose);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), CombatSounds.LONGSWORD_SPIN,
                SoundSource.PLAYERS, 1.0f, 0.95f + level.getRandom().nextFloat() * 0.1f);

        double radius = spec.spinRadius();
        DamageSource source = player.damageSources().playerAttack(player);
        boolean hit = false;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius, 1.0, radius))) {
            if (!validTarget(player, target) || horizontalGap(player, target) > radius) continue;
            if (spec.chargeDamage() > 0.0f && target.hurtServer(level, source, CombatWeapons.chargeDamage(stack, spec))) {
                EnchantmentHelper.doPostAttackEffects(level, target, source);
            }
            if (spec.chargeKnockback() > 0.0f) {
                CombatPlatform.knockback(target, spec.chargeKnockback(), player.getX() - target.getX(), player.getZ() - target.getZ(), player);
            }
            hit = true;
        }
        double y = smearY(player);
        for (int i = 0; i < 6; i++) {
            double angle = i * Math.PI / 3.0;
            double dx = -Math.sin(angle), dz = Math.cos(angle);
            sendSmear(level, player.getX() + dx * 1.4, y + (i % 2) * 0.04, player.getZ() + dz * 1.4, dx, dz, -0.85);
        }
        if (hit && !player.getAbilities().instabuild) stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        int cooldown = spec.spinCooldownTicks();
        if (cooldown > 0) {
            SPIN_READY.put(player.getUUID(), level.getGameTime() + cooldown);
            player.getCooldowns().addCooldown(stack, cooldown);
        }
    }

    public static boolean sweepsArc(Player player) {
        if (BetterCombatCompat.isLoaded() || !CombatConfig.enabled(CombatConfig.DATA_SWEEPS)) return false;
        WeaponSpec spec = spec(player.getMainHandItem());
        return spec != null && spec.sweepRadius() > 0.0f;
    }

    public static void punch(Player player) {
        Long attacked = LAST_ATTACK.remove(player.getUUID());
        if (attacked != null && attacked >= player.level().getGameTime() - 1) return;
        if (!sweepsArc(player) || player.isUsingItem() || CombatRecovery.active(player)) return;
        float strength = player.getAttackStrengthScale(0.5f);
        if (strength < 0.9f) return;
        arcSweep(player, strength, null);
    }

    public static void arcSweep(Player player, float strength, net.minecraft.world.entity.Entity directTarget) {
        if (!(player.level() instanceof ServerLevel level)) return;
        ItemStack stack = player.getMainHandItem();
        WeaponSpec spec = spec(stack);
        if (spec == null || !sweepsArc(player)) return;
        if (directTarget != null) LAST_ATTACK.put(player.getUUID(), level.getGameTime());

        double range = Math.min(spec.reach(), spec.sweepRadius() + 1.0);
        float base = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * (0.2f + strength * strength * 0.8f);
        DamageSource source = player.damageSources().playerAttack(player);
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        boolean hit = false;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range, 0.5, range))) {
            if (target == directTarget || !validTarget(player, target) || horizontalGap(player, target) > range
                    || !CombatVanillaRules.frontal(player, target, spec.sweepAngleDegrees())) continue;
            float damage = EnchantmentHelper.modifyDamage(level, stack, target, source, base);
            if (target.hurtServer(level, source, damage)) {
                CombatPlatform.knockback(target, 0.4, Mth.sin(yaw), -Mth.cos(yaw), player);
                EnchantmentHelper.doPostAttackEffects(level, target, source);
                hit = true;
            }
        }
        if (!hit && directTarget == null) return;
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP,
                player.getSoundSource(), 1.0f, 1.0f);
        if (directTarget != null) return;
        if (!player.getAbilities().instabuild) stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        if (!onSpinCooldown(player)) CombatRecovery.start(player, spec.recoveryTicks());
    }

    private static double smearY(Player player) {
        return Math.min(player.getY(0.6), player.getEyeY() - 0.45);
    }

    private static void sendSmear(ServerLevel level, double x, double y, double z, double dx, double dz, double size) {
        level.sendParticles(CombatParticles.LONGSWORD_SWEEP, x, y, z, 0, dx, size, dz, 1.0);
    }

    private static boolean validTarget(Player player, LivingEntity target) {
        if (target == player || !target.isAlive() || player.isAlliedTo(target) || target.isSpectator()) return false;
        if (target instanceof OwnableEntity pet && pet.getOwner() == player) return false;
        if (target instanceof Player other && other.isCreative()) return false;
        return !(target instanceof ArmorStand stand && stand.isMarker());
    }

    private static double horizontalGap(Player player, LivingEntity target) {
        if (Math.abs(target.getY() - player.getY()) > 2.0) return Double.MAX_VALUE;
        double dx = target.getX() - player.getX(), dz = target.getZ() - player.getZ();
        return Math.max(0.0, Math.sqrt(dx * dx + dz * dz) - (target.getBbWidth() + player.getBbWidth()) * 0.5);
    }

    public static void clear(Player player) {
        SPIN_READY.remove(player.getUUID());
        LAST_ATTACK.remove(player.getUUID());
    }
}
