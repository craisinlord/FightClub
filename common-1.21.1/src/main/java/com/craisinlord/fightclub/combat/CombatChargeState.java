package com.craisinlord.fightclub.combat;

import com.craisinlord.fightclub.compat.BetterCombatCompat;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CombatChargeState {
    public static final byte START = 0;
    public static final byte RELEASE = 1;
    public static final byte BASH = 2;
    public static final byte MISS_SWING = 10;
    public static final byte LONGSWORD_SWING = 11;
    private static final Map<UUID, ChargeState> CHARGES = new ConcurrentHashMap<>();

    private CombatChargeState() {}

    public static void handle(Player player, byte action) { handle(player, action, -1); }

    public static void handle(Player player, byte action, int targetId) {
        if (action == START) begin(player);
        else if (action == RELEASE) release(player, targetId);
        else if (action == BASH) CombatVanillaRules.shieldBash(player);
        else if (action == MISS_SWING) {
            MaceHitStreak.onMiss(player);
        }
        else if (action == LONGSWORD_SWING) LongswordSpin.missSwing(player);
    }

    private static void begin(Player player) {
        Item item = player.getMainHandItem().getItem();
        if (!com.craisinlord.fightclub.CombatConfig.enabled(com.craisinlord.fightclub.CombatConfig.CHARGED_ATTACKS)
                || CombatRecovery.active(player) || player.isUsingItem() || !isChargeWeapon(item)) return;
        CHARGES.put(player.getUUID(), new ChargeState(player.level().getGameTime(), item, new java.util.HashSet<>()));
        CombatPlatform.sendPoseToTracking(player, new CombatPosePayload(player.getId(), CombatPosePayload.CHARGE_START));
        BetterCombatCompat.setChargeActive(player, true);
    }

    private static void release(Player player, int targetId) {
        ChargeState state = CHARGES.remove(player.getUUID());
        if (state == null) return;
        CombatPlatform.sendPoseToTracking(player, new CombatPosePayload(player.getId(), CombatPosePayload.CHARGE_END));
        BetterCombatCompat.setChargeActive(player, false);
        if (!state.hits().isEmpty()) {
            WeaponSpec spec = thrustSpec(state.item());
            if (spec != null && state.item() == player.getMainHandItem().getItem()) CombatRecovery.start(player, spec.recoveryTicks());
            return;
        }
        if (CombatRecovery.active(player) || state.item() != player.getMainHandItem().getItem()) return;
        Item item = player.getMainHandItem().getItem();
        int heldTicks = (int) Math.max(0, player.level().getGameTime() - state.startTick());
        if (CombatWeapons.is(item, "dagger")) {
            WeaponSpec spec = WeaponSpecs.getOrLoadBuiltin(CombatWeapons.material(item), "dagger");
            if (spec != null && heldTicks >= spec.chargeTicks()) startPounce(player, spec, targetId);
            return;
        }
        WeaponSpec spec = thrustSpec(item);
        if (spec == null || heldTicks < spec.chargeTicks()) return;
        LivingEntity target = ThrustTargeting.find(player, null, spec).stream().findFirst().orElse(null);
        if (target != null) chargedAttack(player, target, spec);
    }

    private static final Map<UUID, Vec3> LAST_POSITION = new ConcurrentHashMap<>();

    private static final double MIN_CHARGE_SPEED = 0.15;

    public static void tick(Player player) {
        MaceHitStreak.tick(player);
        tickPounce(player);
        capDaggerCharge(player);
        ChargeState state = CHARGES.get(player.getUUID());
        if (state == null) {
            LAST_POSITION.remove(player.getUUID());
            return;
        }
        Vec3 last = LAST_POSITION.put(player.getUUID(), player.position());
        if (last == null || CombatRecovery.active(player)
                || !com.craisinlord.fightclub.CombatConfig.enabled(com.craisinlord.fightclub.CombatConfig.RUNNING_CHARGE)) return;
        Item item = player.getMainHandItem().getItem();
        WeaponSpec spec = item == state.item() ? thrustSpec(item) : null;
        if (spec == null || player.level().getGameTime() - state.startTick() < spec.chargeTicks()) return;
        Vec3 move = new Vec3(player.getX() - last.x, 0.0, player.getZ() - last.z);
        double speed = move.length();
        Vec3 look = new Vec3(player.getLookAngle().x, 0.0, player.getLookAngle().z);
        if (speed < MIN_CHARGE_SPEED || look.lengthSqr() < 1.0E-4 || move.normalize().dot(look.normalize()) < 0.6) return;
        double contact = spec.reach() * 0.6;
        java.util.List<LivingEntity> targets = ThrustTargeting.find(player, null, spec).stream()
                .filter(entity -> !state.hits().contains(entity.getId()))
                .filter(entity -> entity.distanceTo(player) <= contact + entity.getBbWidth() * 0.5)
                .toList();
        if (targets.isEmpty()) return;

        float momentum = (float) Math.min(1.0, (speed - MIN_CHARGE_SPEED) / MIN_CHARGE_SPEED);
        float base = hitDamage(player, spec);
        float bonus = Math.max(0.0f, spec.chargeDamage() - base) + base * 0.5f * momentum;
        for (LivingEntity target : targets) {
            state.hits().add(target.getId());
            ((com.craisinlord.fightclub.mixin.LivingEntityAccessor) player).fightClub$setAttackStrengthTicker(1000);
            CombatAttacks.attackWithBonus(player, target, bonus);
            hitEffects(player, target, CombatSounds.CHARGE_HIT, 10);
            CombatVanillaRules.chargeCritEffects(player, target);
            if (spec.chargeKnockback() > 0.0f) CombatPlatform.knockback(target, spec.chargeKnockback() * (1.0f + momentum),
                    player.getX() - target.getX(), player.getZ() - target.getZ(), player);
        }
    }

    private static WeaponSpec thrustSpec(Item item) {
        if (item == Items.TRIDENT) return WeaponSpecs.getOrLoadBuiltin("base", "trident");
        if (CombatWeapons.is(item, "spear")) {
            return WeaponSpecs.getOrLoadBuiltin(CombatWeapons.material(item), "spear");
        }
        return null;
    }

    private record Pounce(int targetId, long startTick, Item item, WeaponSpec spec) {}
    private static final Map<UUID, Pounce> POUNCES = new ConcurrentHashMap<>();
    private static final int POUNCE_TIMEOUT_TICKS = 14;
    private static final double POUNCE_TIMEOUT_SLACK = 2.0;

    private static void startPounce(Player player, WeaponSpec spec, int targetId) {
        if (player.isPassenger() || player.isFallFlying()) return;
        if (targetId < 0 || !(player.level().getEntity(targetId) instanceof LivingEntity target)
                || !PounceTargeting.validForServer(player, target, spec)) {
            lunge(player);
            return;
        }
        POUNCES.put(player.getUUID(), new Pounce(targetId, player.level().getGameTime(), player.getMainHandItem().getItem(), spec));
        BetterCombatCompat.setChargeActive(player, true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), CombatSounds.DAGGER_SWING,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.8f);
    }

    private static final double LUNGE_SPEED = 0.9;
    private static final int DAGGER_MAX_FULL_HOLD_TICKS = 60;

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

    public static boolean isDaggerCharging(Player player) {
        ChargeState state = CHARGES.get(player.getUUID());
        return state != null && isDagger(state.item()) && player.getMainHandItem().getItem() == state.item();
    }

    private static void capDaggerCharge(Player player) {
        ChargeState state = CHARGES.get(player.getUUID());
        if (state == null || !isDagger(state.item())) return;
        long held = player.level().getGameTime() - state.startTick();
        if (held < chargeTicksFor(state.item().getDefaultInstance()) + DAGGER_MAX_FULL_HOLD_TICKS) return;
        CHARGES.remove(player.getUUID());
        CombatPosePayload end = new CombatPosePayload(player.getId(), CombatPosePayload.CHARGE_END);
        CombatPlatform.sendPoseToTracking(player, end);
        CombatPlatform.sendToPlayer(player, end);
        if (!POUNCES.containsKey(player.getUUID())) BetterCombatCompat.setChargeActive(player, false);
    }

    private static void tickPounce(Player player) {
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

    private static void dashTrail(Player player) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, player.getX(), player.getY(0.5), player.getZ(),
                3, player.getBbWidth() * 0.3, player.getBbHeight() * 0.2, player.getBbWidth() * 0.3, 0.05);
    }

    private static double edgeDistance(Player player, LivingEntity target) {
        if (Math.abs(target.getY() - player.getY()) > 2.5) return Double.MAX_VALUE;
        double dx = target.getX() - player.getX(), dz = target.getZ() - player.getZ();
        return Math.max(0.0, Math.sqrt(dx * dx + dz * dz) - (target.getBbWidth() + player.getBbWidth()) * 0.5);
    }

    private static float hitDamage(Player player, WeaponSpec spec) {
        ItemStack stack = player.getMainHandItem();
        if (CombatWeapons.external(stack.getItem()) == null || CombatWeapons.usesSpecStats(stack.getItem())) return spec.attackDamage();
        double[] damage = {1.0};
        stack.forEachModifier(net.minecraft.world.entity.EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.is(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
                    && modifier.operation() == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE) {
                damage[0] += modifier.amount();
            }
        });
        return (float) damage[0];
    }

    private static void pounceHit(Player player, LivingEntity target, WeaponSpec spec) {
        player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, false);
        CombatAttacks.attackWithBonus(player, target, Math.max(0.0f, spec.chargeDamage() - hitDamage(player, spec)));
        hitEffects(player, target, CombatSounds.DAGGER_CHARGED_STAB, 12);
        Vec3 away = player.position().subtract(target.position());
        Vec3 escape = new Vec3(away.x, 0.0, away.z);
        escape = escape.lengthSqr() < 1.0E-6 ? Vec3.ZERO : escape.normalize().scale(0.65);
        player.setDeltaMovement(escape.x, 0.22, escape.z);
        player.hurtMarked = true;
        CombatRecovery.start(player, spec.recoveryTicks());
    }

    private static void hitEffects(Player player, LivingEntity target, net.minecraft.sounds.SoundEvent sound, int particles) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        level.playSound(null, target.getX(), target.getY(), target.getZ(), sound,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.95f + level.random.nextFloat() * 0.1f);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(),
                particles, target.getBbWidth() * 0.3, target.getBbHeight() * 0.2, target.getBbWidth() * 0.3, 0.35);
    }

    private static void chargedAttack(Player player, LivingEntity target, WeaponSpec spec) {
        CombatAttacks.attackWithBonus(player, target, Math.max(0.0f, spec.chargeDamage() - hitDamage(player, spec)));
        hitEffects(player, target, CombatSounds.CHARGE_HIT, 10);
        CombatVanillaRules.chargeCritEffects(player, target);
        if (spec.chargeKnockback() > 0.0f) CombatPlatform.knockback(target, spec.chargeKnockback(),
                player.getX() - target.getX(), player.getZ() - target.getZ(), player);
        CombatRecovery.start(player, spec.recoveryTicks());
    }

    public static boolean isChargeWeapon(Item item) {
        return CombatWeapons.is(item, "dagger") || CombatWeapons.is(item, "spear") || item == Items.TRIDENT;
    }

    public static boolean isSpearWeapon(Item item) {
        return CombatWeapons.is(item, "spear");
    }

    public static boolean isDagger(Item item) {
        return CombatWeapons.is(item, "dagger");
    }

    public static boolean isLeftCharging(Player player) { return CHARGES.containsKey(player.getUUID()); }

    public static int chargeTicksFor(ItemStack stack) {
        Item item = stack.getItem();
        WeaponSpec spec = CombatWeapons.is(item, "dagger")
                ? WeaponSpecs.getOrLoadBuiltin(CombatWeapons.material(item), "dagger")
                : thrustSpec(item);
        return spec == null ? 20 : Math.max(1, spec.chargeTicks());
    }

    public static void clear(Player player) {
        MaceHitStreak.reset(player);
        LongswordSpin.clear(player);
        LAST_POSITION.remove(player.getUUID());
        boolean wasPouncing = POUNCES.remove(player.getUUID()) != null;
        if (CHARGES.remove(player.getUUID()) != null || wasPouncing) BetterCombatCompat.setChargeActive(player, false);
    }

    private record ChargeState(long startTick, Item item, java.util.Set<Integer> hits) {}
}
