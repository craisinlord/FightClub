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
        } else if (action == LONGSWORD_SWING) LongswordSpin.missSwing(player);
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
            if (player.isUsingItem() && player.getUseItem().getItem() == state.item()) player.stopUsingItem();
            return;
        }
        if (CombatRecovery.active(player) || state.item() != item) return;
        int heldTicks = (int) Math.max(0, player.level().getGameTime() - state.startTick());
        if (CombatWeapons.is(item, "dagger")) {
            WeaponSpec spec = WeaponSpecs.getOrLoadBuiltin(CombatWeapons.material(item), "dagger");
            if (spec != null && heldTicks >= spec.chargeTicks()) startPounce(player, spec, targetId);
            return;
        }
        WeaponSpec spec = thrustSpec(item);
        if (spec == null || heldTicks < spec.chargeTicks()) return;
        LivingEntity target = ThrustTargeting.find(player, null, spec).stream().findFirst().orElse(null);
        if (target == null) return;
        chargedAttack(player, target, spec);
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

    private static void pounceHit(Player player, LivingEntity target, WeaponSpec spec) {
        player.swing(InteractionHand.MAIN_HAND, false);
        attackWithBonus(player, target, Math.max(0.0f, CombatWeapons.chargeDamage(player.getMainHandItem(), spec) - hitDamage(player, spec)));
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
        attackWithBonus(player, target, Math.max(0.0f, CombatWeapons.chargeDamage(player.getMainHandItem(), spec) - hitDamage(player, spec)));
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

    private static final net.minecraft.resources.Identifier CHARGE_BONUS =
            net.minecraft.resources.Identifier.fromNamespaceAndPath(com.craisinlord.fightclub.FightClub.MOD_ID, "charge_bonus");

    private static void attackWithBonus(Player player, LivingEntity target, float bonus) {
        net.minecraft.world.entity.ai.attributes.AttributeInstance damage =
                player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        boolean applied = damage != null && bonus > 0.0f;
        if (applied) {
            damage.removeModifier(CHARGE_BONUS);
            damage.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(CHARGE_BONUS, bonus,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
        }
        try {
            player.attack(target);
        } finally {
            if (applied) damage.removeModifier(CHARGE_BONUS);
        }
    }

    static float hitDamage(Player player, WeaponSpec spec) {
        return CombatWeapons.hitDamage(player.getMainHandItem(), spec);
    }

    private static WeaponSpec thrustSpec(Item item) {
        if (CombatWeapons.isTrident(item)) return WeaponSpecs.getOrLoadBuiltin("base", "trident");
        if (CombatWeapons.is(item, "spear")) return WeaponSpecs.getOrLoadBuiltin(CombatWeapons.material(item), "spear");
        if (isSpear(item)) return WeaponSpecs.getOrLoadBuiltin(spearMaterial(item), "spear");
        return null;
    }

    public static boolean isChargeWeapon(Item item) {
        return CombatWeapons.is(item, "dagger") || CombatWeapons.is(item, "spear")
                || isSpear(item) || CombatWeapons.isTrident(item);
    }

    public static boolean usesItemForCharge(Item item) {
        return isSpear(item);
    }

    public static boolean isSpearWeapon(Item item) {
        return CombatWeapons.is(item, "spear") || isSpear(item);
    }

    public static boolean isDagger(Item item) {
        return CombatWeapons.is(item, "dagger");
    }

    public static boolean isLeftCharging(Player player) { return CHARGES.containsKey(player.getUUID()); }

    public static void releaseLeft(Player player) { release(player, -1); }

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
