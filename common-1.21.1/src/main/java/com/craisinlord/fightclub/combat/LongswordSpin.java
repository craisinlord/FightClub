package com.craisinlord.fightclub.combat;

import com.craisinlord.fightclub.CombatConfig;
import com.craisinlord.fightclub.compat.BetterCombatCompat;
import net.minecraft.core.particles.ParticleTypes;
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

/**
 * Longsword specials, all server-authoritative:
 * <ul>
 * <li>Hold right-click to charge a spin ({@code charge_ticks}); releasing a full charge hits every
 * living thing within {@code spin_radius} once, for {@code charge_damage} with {@code charge_knockback}
 * pushing outward, then puts the sword on a {@code spin_cooldown_ticks} item cooldown. Releasing
 * early does nothing. Right-click is Fight Club's, so this also works with Better Combat.</li>
 * <li>Without Better Combat, a swing that hits only air still sweeps the arc in front of the
 * player (the spec's sweep radius and angle). Better Combat already does arc hits itself.</li>
 * </ul>
 */
public final class LongswordSpin {
    private static final Map<UUID, Long> SPIN_READY = new ConcurrentHashMap<>();

    private LongswordSpin() {}

    public static boolean isLongsword(ItemStack stack) {
        return stack.getItem() instanceof CombatWeaponItem weapon && weapon.combatWeapon().equals("longsword");
    }

    public static WeaponSpec spec(ItemStack stack) {
        return stack.getItem() instanceof CombatWeaponItem weapon && weapon.combatWeapon().equals("longsword")
                ? WeaponSpecs.getOrLoadBuiltin(weapon.combatMaterial(), "longsword") : null;
    }

    /** True when this longsword can charge a spin at all (spin enabled and specced). */
    public static boolean hasSpin(ItemStack stack) {
        WeaponSpec spec = spec(stack);
        return spec != null && CombatConfig.enabled(CombatConfig.LONGSWORD_SPIN) && spec.chargeTicks() > 0 && spec.spinRadius() > 0.0f;
    }

    /** Spin charge length in ticks (20 if unknown). */
    public static int chargeTicks(ItemStack stack) {
        WeaponSpec spec = spec(stack);
        return spec == null ? 20 : Math.max(1, spec.chargeTicks());
    }

    /** Still recovering from the last spin (the server's own clock, so a shorter item cooldown can't cut it short). */
    public static boolean onSpinCooldown(Player player) {
        Long ready = SPIN_READY.get(player.getUUID());
        if (ready == null) return false;
        if (player.level().getGameTime() >= ready) {
            SPIN_READY.remove(player.getUUID());
            return false;
        }
        return true;
    }

    /** Right-click released after {@code heldTicks}: spin if the charge was full. */
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
            if (spec.chargeDamage() > 0.0f && target.hurt(source, spec.chargeDamage())) {
                EnchantmentHelper.doPostAttackEffects(level, target, source);
            }
            if (spec.chargeKnockback() > 0.0f) {
                CombatPlatform.knockback(target, spec.chargeKnockback(), player.getX() - target.getX(), player.getZ() - target.getZ(), player);
            }
            hit = true;
        }
        // A ring of sweep arcs, so the spin reads for everyone nearby.
        double y = player.getY(0.5);
        for (int i = 0; i < 6; i++) {
            double angle = i * Math.PI / 3.0;
            double dx = -Math.sin(angle), dz = Math.cos(angle);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX() + dx * 1.4, y, player.getZ() + dz * 1.4, 0, dx, 0.0, dz, 0.0);
        }
        if (hit && !player.getAbilities().instabuild) stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        int cooldown = spec.spinCooldownTicks();
        if (cooldown > 0) {
            SPIN_READY.put(player.getUUID(), level.getGameTime() + cooldown);
            player.getCooldowns().addCooldown(stack.getItem(), cooldown);
        }
    }

    /**
     * A longsword swing that hit only air (sent by the client, see MinecraftInputMixin). Uses the
     * server's own attack strength and vanilla's sweep conditions (full strength, on the ground,
     * not sprinting), then resets the server's attack timer like a real attack.
     */
    public static void airSweep(Player player) {
        if (BetterCombatCompat.isLoaded() || !CombatConfig.enabled(CombatConfig.DATA_SWEEPS)
                || !(player.level() instanceof ServerLevel level)) return;
        ItemStack stack = player.getMainHandItem();
        WeaponSpec spec = spec(stack);
        if (spec == null || spec.sweepRadius() <= 0.0f || player.isUsingItem() || CombatRecovery.active(player)) return;
        float strength = player.getAttackStrengthScale(0.5f);
        player.resetAttackStrengthTicker();
        if (strength < 0.9f || !player.onGround() || player.isSprinting() || player.isPassenger()) return;

        double range = Math.min(spec.reach(), spec.sweepRadius() + 1.0);
        float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * (0.2f + strength * strength * 0.8f);
        DamageSource source = player.damageSources().playerAttack(player);
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        boolean hit = false;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range, 0.5, range))) {
            if (!validTarget(player, target) || horizontalGap(player, target) > range
                    || !CombatVanillaRules.frontal(player, target, spec.sweepAngleDegrees())) continue;
            if (target.hurt(source, damage)) {
                CombatPlatform.knockback(target, 0.4, Mth.sin(yaw), -Mth.cos(yaw), player);
                EnchantmentHelper.doPostAttackEffects(level, target, source);
                hit = true;
            }
        }
        if (!hit) return;
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP,
                player.getSoundSource(), 1.0f, 1.0f);
        double dx = -Mth.sin(yaw), dz = Mth.cos(yaw);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX() + dx, player.getY(0.5), player.getZ() + dz, 0, dx, 0.0, dz, 0.0);
        if (!player.getAbilities().instabuild) stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        if (!onSpinCooldown(player)) CombatRecovery.start(player, spec.recoveryTicks());
    }

    /** Not the player, not allies, not the player's pets, not marker armor stands. */
    private static boolean validTarget(Player player, LivingEntity target) {
        if (target == player || !target.isAlive() || player.isAlliedTo(target) || target.isSpectator()) return false;
        if (target instanceof OwnableEntity pet && pet.getOwner() == player) return false;
        if (target instanceof Player other && other.isCreative()) return false;
        return !(target instanceof ArmorStand stand && stand.isMarker());
    }

    /** Horizontal gap between the player's and the target's hitboxes, with a vertical limit. */
    private static double horizontalGap(Player player, LivingEntity target) {
        if (Math.abs(target.getY() - player.getY()) > 2.0) return Double.MAX_VALUE;
        double dx = target.getX() - player.getX(), dz = target.getZ() - player.getZ();
        return Math.max(0.0, Math.sqrt(dx * dx + dz * dz) - (target.getBbWidth() + player.getBbWidth()) * 0.5);
    }

    public static void clear(Player player) {
        SPIN_READY.remove(player.getUUID());
    }
}
