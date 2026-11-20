package com.craisinlord.fightclub.combat;

import com.craisinlord.fightclub.CombatConfig;
import com.craisinlord.fightclub.compat.BetterCombatCompat;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server side of the hold-left-click charge.
 *
 * <p>Daggers and tridents use their own charge state (as on 1.21.1), so the charge can't collide
 * with right-click throws or force vanilla's trident pose. Vanilla 26.x spears are different: their
 * charge <em>is</em> vanilla's kinetic lance charge, which only runs while the item is in use, so a
 * spear charge starts item use on both sides. Clients learn about everyone's charges from
 * {@link CombatPosePayload} and draw spear charges with vanilla's SPEAR use animation.
 */
public final class CombatChargeState {
    public static final byte START = 0;
    public static final byte RELEASE = 1;
    /** Attack pressed while blocking with a shield. Vanilla swallows that click, so it gets its own action. */
    public static final byte BASH = 2;
    /** A swing that hit only air (longsword air sweep, mace hit streak miss). */
    public static final byte MISS_SWING = 10;
    private static final Map<UUID, ChargeState> CHARGES = new ConcurrentHashMap<>();

    private CombatChargeState() {}

    public static void handle(Player player, byte action) { handle(player, action, -1); }

    public static void handle(Player player, byte action, int targetId) {
        if (action == START) begin(player);
        else if (action == RELEASE) release(player, targetId);
        else if (action == BASH) CombatVanillaRules.shieldBash(player);
        else if (action == MISS_SWING) {
            LongswordSpin.airSweep(player);
            MaceHitStreak.onMiss(player);
        }
    }

    private static void begin(Player player) {
        ItemStack stack = player.getMainHandItem();
        if (!CombatConfig.enabled(CombatConfig.CHARGED_ATTACKS) || CombatRecovery.active(player)
                || player.isUsingItem() || !isChargeWeapon(stack.getItem())) return;
        CHARGES.put(player.getUUID(), new ChargeState(player.level().getGameTime(), stack.getItem()));
        if (usesItemForCharge(stack.getItem())) player.startUsingItem(InteractionHand.MAIN_HAND);
        CombatPlatform.sendPoseToTracking(player, new CombatPosePayload(player.getId(), CombatPosePayload.CHARGE_START));
        BetterCombatCompat.setChargeActive(player, true);
    }

    private static void release(Player player, int targetId) {
        ChargeState state = CHARGES.remove(player.getUUID());
        if (state == null) return;
        CombatPlatform.sendPoseToTracking(player, new CombatPosePayload(player.getId(), CombatPosePayload.CHARGE_END));
        BetterCombatCompat.setChargeActive(player, false);
        Item item = player.getMainHandItem().getItem();
        if (usesItemForCharge(state.item())) {
            // Vanilla's kinetic weapon did the work while in use; just end the use (no throw).
            if (player.isUsingItem() && player.getUseItem().getItem() == state.item()) player.stopUsingItem();
            return;
        }
        if (CombatRecovery.active(player) || state.item() != item) return;
        int heldTicks = (int) Math.max(0, player.level().getGameTime() - state.startTick());
        if (item instanceof CombatWeaponItem weapon && weapon.combatWeapon().equals("dagger")) {
            WeaponSpec spec = WeaponSpecs.getOrLoadBuiltin(weapon.combatMaterial(), "dagger");
            if (spec != null && heldTicks >= spec.chargeTicks()) startPounce(player, spec, targetId);
            return;
        }
        WeaponSpec spec = thrustSpec(item);
        if (spec == null || heldTicks < spec.chargeTicks()) return;
        LivingEntity target = ThrustTargeting.find(player, null, spec).stream().findFirst().orElse(null);
        if (target == null) return;
        chargedAttack(player, target, spec);
    }

    /** A dagger pounce in flight: the client is dashing at the target, the server strikes on arrival. */
    private record Pounce(int targetId, long startTick, Item item, WeaponSpec spec) {}
    private static final Map<UUID, Pounce> POUNCES = new ConcurrentHashMap<>();
    /** Longest the client dash runs (see PounceClient), plus room for latency. */
    private static final int POUNCE_TIMEOUT_TICKS = 14;
    /** At timeout the hit still lands this far past normal reach; farther means the dash was blocked. */
    private static final double POUNCE_TIMEOUT_SLACK = 2.0;

    /**
     * Charged dagger release. The client picked the target and is dashing at it; with no target it
     * sends -1 and the player lunges forward instead (see {@link #lunge}).
     */
    private static void startPounce(Player player, WeaponSpec spec, int targetId) {
        if (player.isPassenger() || player.isFallFlying()) return;
        if (targetId < 0 || !(player.level().getEntity(targetId) instanceof LivingEntity target)
                || !PounceTargeting.validForServer(player, target, spec)) {
            lunge(player);
            return;
        }
        POUNCES.put(player.getUUID(), new Pounce(targetId, player.level().getGameTime(), player.getMainHandItem().getItem(), spec));
        // Better Combat: keep its swings paused through the dash, so a click can't spend the attack early.
        BetterCombatCompat.setChargeActive(player, true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), CombatSounds.DAGGER_SWING,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.8f);
    }

    /** Horizontal speed of a targetless dagger lunge: about 2.5 blocks on the ground. */
    private static final double LUNGE_SPEED = 0.9;
    /** Longest a dagger charge stays up after it is full; its damage immunity can't be held forever. */
    private static final int DAGGER_MAX_FULL_HOLD_TICKS = 60;

    /**
     * A full dagger charge released with nothing to strike: a short dash along the look direction,
     * with a little lift so ground friction doesn't eat it. Server velocity only, like the pounce's
     * push-off, so the camera moves just as vanilla knockback would move it.
     */
    private static void lunge(Player player) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        if (flat.lengthSqr() < 1.0E-4) return;
        Vec3 dash = flat.normalize().scale(LUNGE_SPEED);
        player.setDeltaMovement(dash.x, Math.max(player.getDeltaMovement().y, 0.12), dash.z);
        player.hurtMarked = true;
        dashTrail(player);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), CombatSounds.DAGGER_SWING,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.1f);
    }

    /** True while this player holds a dagger charge: they take no damage (DaggerChargeImmunityMixin). */
    public static boolean isDaggerCharging(Player player) {
        ChargeState state = CHARGES.get(player.getUUID());
        return state != null && isDagger(state.item()) && player.getMainHandItem().getItem() == state.item();
    }

    /** Ends a dagger charge held too long past full, for this player and everyone watching. */
    private static void capDaggerCharge(Player player) {
        ChargeState state = CHARGES.get(player.getUUID());
        if (state == null || !isDagger(state.item())) return;
        long held = player.level().getGameTime() - state.startTick();
        if (held < chargeTicksFor(state.item().getDefaultInstance()) + DAGGER_MAX_FULL_HOLD_TICKS) return;
        CHARGES.remove(player.getUUID());
        CombatPosePayload end = new CombatPosePayload(player.getId(), CombatPosePayload.CHARGE_END);
        CombatPlatform.sendPoseToTracking(player, end);
        // The holder's client ends its own charge on this (CombatInputClient.serverEndedCharge).
        CombatPlatform.sendToPlayer(player, end);
        if (!POUNCES.containsKey(player.getUUID())) BetterCombatCompat.setChargeActive(player, false);
    }

    /** Called every server tick for each player. */
    public static void tick(Player player) {
        capDaggerCharge(player);
        MaceHitStreak.tick(player);
        Pounce pounce = POUNCES.get(player.getUUID());
        if (pounce == null) return;
        boolean timedOut = player.level().getGameTime() - pounce.startTick() >= POUNCE_TIMEOUT_TICKS;
        if (!(player.level().getEntity(pounce.targetId()) instanceof LivingEntity target) || !target.isAlive()
                || player.getMainHandItem().getItem() != pounce.item() || !player.isAlive()) {
            endPounce(player);
            return;
        }
        double gap = edgeDistance(player, target);
        double reach = pounce.spec().reach();
        if (gap > reach && !(timedOut && gap <= reach + POUNCE_TIMEOUT_SLACK)) {
            if (timedOut) endPounce(player);
            else dashTrail(player);
            return;
        }
        endPounce(player);
        pounceHit(player, target, pounce.spec());
    }

    private static void endPounce(Player player) {
        if (POUNCES.remove(player.getUUID()) != null && !CHARGES.containsKey(player.getUUID())) {
            BetterCombatCompat.setChargeActive(player, false);
        }
    }

    /** A few crit sparks behind the dashing player, for everyone nearby. */
    private static void dashTrail(Player player) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, player.getX(), player.getY(0.5), player.getZ(),
                3, player.getBbWidth() * 0.3, player.getBbHeight() * 0.2, player.getBbWidth() * 0.3, 0.05);
    }

    /** Horizontal gap between the player's and the target's hitboxes, with a vertical limit. */
    private static double edgeDistance(Player player, LivingEntity target) {
        if (Math.abs(target.getY() - player.getY()) > 2.5) return Double.MAX_VALUE;
        double dx = target.getX() - player.getX(), dz = target.getZ() - player.getZ();
        return Math.max(0.0, Math.sqrt(dx * dx + dz * dz) - (target.getBbWidth() + player.getBbWidth()) * 0.5);
    }

    private static void pounceHit(Player player, LivingEntity target, WeaponSpec spec) {
        // The pouncing client already shows its own stab; this shows it to everyone else.
        player.swing(InteractionHand.MAIN_HAND, false);
        player.attack(target);
        float bonus = Math.max(0.0f, spec.chargeDamage() - spec.attackDamage());
        if (bonus > 0.0f && player.level() instanceof net.minecraft.server.level.ServerLevel level) {
            target.hurtServer(level, player.damageSources().playerAttack(player), bonus);
        }
        playHit(player, target, CombatSounds.DAGGER_CHARGED_STAB);
        if (player.level() instanceof net.minecraft.server.level.ServerLevel level) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(),
                    12, target.getBbWidth() * 0.3, target.getBbHeight() * 0.2, target.getBbWidth() * 0.3, 0.35);
        }
        Vec3 away = player.position().subtract(target.position());
        Vec3 escape = new Vec3(away.x, 0.0, away.z);
        escape = escape.lengthSqr() < 1.0E-6 ? Vec3.ZERO : escape.normalize().scale(0.65);
        player.setDeltaMovement(escape.x, 0.22, escape.z);
        player.hurtMarked = true;
        CombatRecovery.start(player, spec.recoveryTicks());
    }

    private static void chargedAttack(Player player, LivingEntity target, WeaponSpec spec) {
        player.swing(InteractionHand.MAIN_HAND, false);
        player.attack(target);
        float bonus = Math.max(0.0f, spec.chargeDamage() - spec.attackDamage());
        if (bonus > 0.0f) target.hurtServer((net.minecraft.server.level.ServerLevel) player.level(),
                player.damageSources().playerAttack(player), bonus);
        playHit(player, target, CombatSounds.CHARGE_HIT);
        CombatVanillaRules.chargeCritEffects(player, target);
        if (spec.chargeKnockback() > 0.0f) CombatPlatform.knockback(target, spec.chargeKnockback(),
                player.getX() - target.getX(), player.getZ() - target.getZ(), player);
        CombatRecovery.start(player, spec.recoveryTicks());
    }

    private static void playHit(Player player, LivingEntity target, net.minecraft.sounds.SoundEvent sound) {
        player.level().playSound(null, target.getX(), target.getY(), target.getZ(), sound,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.95f + player.getRandom().nextFloat() * 0.1f);
    }

    private static WeaponSpec thrustSpec(Item item) {
        if (item == Items.TRIDENT) return WeaponSpecs.getOrLoadBuiltin("base", "trident");
        if (isSpear(item)) return WeaponSpecs.getOrLoadBuiltin(spearMaterial(item), "spear");
        return null;
    }

    public static boolean isChargeWeapon(Item item) {
        return item instanceof CombatWeaponItem weapon && weapon.combatWeapon().equals("dagger")
                || isSpear(item) || item == Items.TRIDENT;
    }

    /** Vanilla kinetic spears: the charge runs as vanilla item use (see the class comment). */
    public static boolean usesItemForCharge(Item item) {
        return isSpear(item);
    }

    public static boolean isSpearWeapon(Item item) {
        return item instanceof CombatWeaponItem weapon && weapon.combatWeapon().equals("spear") || isSpear(item);
    }

    public static boolean isDagger(Item item) {
        return item instanceof CombatWeaponItem weapon && weapon.combatWeapon().equals("dagger");
    }

    public static boolean isLeftCharging(Player player) { return CHARGES.containsKey(player.getUUID()); }

    /** A right-click release arrived while a left-click charge held the item: treat it as the charge's release. */
    public static void releaseLeft(Player player) { release(player, -1); }

    /** Charge length in ticks for the held charge weapon, for client-side charge progress. */
    public static int chargeTicksFor(ItemStack stack) {
        Item item = stack.getItem();
        WeaponSpec spec = item instanceof CombatWeaponItem weapon && weapon.combatWeapon().equals("dagger")
                ? WeaponSpecs.getOrLoadBuiltin(weapon.combatMaterial(), "dagger")
                : thrustSpec(item);
        return spec == null ? 20 : Math.max(1, spec.chargeTicks());
    }

    /** Drops per-player charge state; called on death, respawn and logout. */
    public static void clear(Player player) {
        MaceHitStreak.reset(player);
        LongswordSpin.clear(player);
        boolean wasPouncing = POUNCES.remove(player.getUUID()) != null;
        if (CHARGES.remove(player.getUUID()) != null || wasPouncing) BetterCombatCompat.setChargeActive(player, false);
    }

    private static boolean isSpear(Item item) {
        return item == Items.WOODEN_SPEAR || item == Items.STONE_SPEAR || item == Items.COPPER_SPEAR
                || item == Items.IRON_SPEAR || item == Items.GOLDEN_SPEAR || item == Items.DIAMOND_SPEAR
                || item == Items.NETHERITE_SPEAR;
    }

    private static String spearMaterial(Item item) {
        if (item == Items.WOODEN_SPEAR) return "wood";
        if (item == Items.STONE_SPEAR) return "stone";
        if (item == Items.COPPER_SPEAR) return "copper";
        if (item == Items.IRON_SPEAR) return "iron";
        if (item == Items.GOLDEN_SPEAR) return "gold";
        if (item == Items.DIAMOND_SPEAR) return "diamond";
        return "netherite";
    }

    private record ChargeState(long startTick, Item item) {}
}
