package com.craisinlord.fightclub.combat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;

public final class CombatVanillaRules {
    private static final Identifier LONGSWORD_OFFHAND = Identifier.fromNamespaceAndPath("fight_club", "longsword_offhand");
    private static final String[] SWORD_MATERIALS = {"wood", "stone", "copper", "iron", "gold", "diamond", "netherite"};
    private static final Item[] SWORDS = {Items.WOODEN_SWORD, Items.STONE_SWORD, Items.COPPER_SWORD, Items.IRON_SWORD, Items.GOLDEN_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD};
    private CombatVanillaRules() {}

    public static boolean isSword(ItemStack stack) {
        return CombatWeapons.is(stack.getItem(), "sword") || isVanillaSword(stack.getItem()) && CombatWeapons.external(stack.getItem()) == null;
    }
    public static boolean isVanillaSword(Item item) { return item == Items.WOODEN_SWORD || item == Items.STONE_SWORD || item == Items.COPPER_SWORD || item == Items.IRON_SWORD || item == Items.GOLDEN_SWORD || item == Items.DIAMOND_SWORD || item == Items.NETHERITE_SWORD; }
    public static WeaponSpec swordSpec(ItemStack stack) {
        if (CombatWeapons.is(stack.getItem(), "sword")) return WeaponSpecs.getOrLoadBuiltin(CombatWeapons.material(stack.getItem()), "sword");
        Item item = stack.getItem();
        for (int i = 0; i < SWORDS.length; i++) if (item == SWORDS[i]) return WeaponSpecs.getOrLoadBuiltin(SWORD_MATERIALS[i], "sword");
        return null;
    }
    public static boolean isShield(ItemStack stack) { return CombatWeapons.isShield(stack); }

    public static boolean blocksVanillaSweep(Player player) {
        ExternalWeapons.Entry external = CombatWeapons.external(player.getMainHandItem().getItem());
        return external != null && !external.weapon().equals("sword");
    }
    public static boolean isMace(ItemStack stack) { return CombatWeapons.isMace(stack.getItem()); }

    public static boolean suppressesCrits(Player player) {
        return chargeOnlyCrits(player.getMainHandItem());
    }

    public static boolean chargeOnlyCrits(ItemStack stack) {
        if (!com.craisinlord.fightclub.CombatConfig.enabled(com.craisinlord.fightclub.CombatConfig.SPEAR_CHARGE_CRITS)) return false;
        String kind = CombatTooltips.kindOf(stack);
        if (!"spear".equals(kind) && !"trident".equals(kind)) return false;
        WeaponSpec spec = CombatTooltips.specFor(stack);
        return spec != null && !spec.criticalHits();
    }

    public static void chargeCritEffects(Player player, Entity target) {
        if (player.level().isClientSide() || !chargeOnlyCrits(player.getMainHandItem())) return;
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_CRIT, player.getSoundSource(), 1.0f, 1.0f);
        player.crit(target);
    }

    public static boolean spearChargeCrits(Player player) {
        return !player.level().isClientSide() && CombatChargeState.isLeftCharging(player) && player.isUsingItem()
                && CombatChargeState.usesItemForCharge(player.getUseItem().getItem()) && chargeOnlyCrits(player.getUseItem());
    }

    public static void applyLongswordRules(Player player) {
        AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeed != null) attackSpeed.removeModifier(LONGSWORD_OFFHAND);
        if (CombatWeapons.is(player.getMainHandItem().getItem(), "longsword")) {
            player.resetFallDistance();
            if (attackSpeed != null) {
                WeaponSpec spec = WeaponSpecs.getOrLoadBuiltin(CombatWeapons.material(player.getMainHandItem().getItem()), "longsword");
                if (spec != null && !player.getOffhandItem().isEmpty()) {
                    attackSpeed.addTransientModifier(new AttributeModifier(LONGSWORD_OFFHAND,
                            spec.offhandSpeedMultiplier() - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                }
            }
        }
    }

    public static boolean swordBlocks(Player defender, Entity attacker) {
        if (!defender.isUsingItem() || !isSword(defender.getUseItem()) || !(attacker instanceof LivingEntity)) return false;
        WeaponSpec spec = swordSpec(defender.getUseItem());
        return spec != null && spec.blocking() && frontal(defender, attacker, spec.blockingAngleDegrees());
    }
    public static void playSwordBlockEffects(Player defender, Entity attacker) {
        if (!(defender.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        level.playSound(null, defender.getX(), defender.getY(), defender.getZ(), CombatSounds.SWORD_BLOCK,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.95f + level.getRandom().nextFloat() * 0.1f);
        Vec3 toward = attacker.position().subtract(defender.position()).normalize();
        Vec3 spark = defender.getEyePosition().add(toward.scale(0.6)).add(0.0, -0.35, 0.0);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, spark.x, spark.y, spark.z, 6, 0.12, 0.12, 0.12, 0.25);
    }

    public static float swordBlockDamageMultiplier(ItemStack stack) {
        WeaponSpec spec = swordSpec(stack);
        return spec == null ? 0.45f : spec.blockingDamageMultiplier();
    }

    public static boolean frontal(LivingEntity defender, Entity attacker, float angle) {
        Vec3 toAttacker = attacker.position().subtract(defender.position());
        if (toAttacker.lengthSqr() < 0.0001) return true;
        return defender.getLookAngle().dot(toAttacker.normalize()) >= Math.cos(Math.toRadians(angle * 0.5));
    }

    public static boolean shieldBash(Player player, Entity target) {
        if (!player.isUsingItem() || !isShield(player.getUseItem())) return false;
        long now = player.level().getGameTime();
        Long ready = BASH_READY.get(player.getUUID());
        if (ready != null && now < ready) return false;
        if (!frontal(player, target, 90.0f)) return false;
        CombatPlatform.knockback((LivingEntity) target, 1.6, player.getX() - target.getX(), player.getZ() - target.getZ(), player);
        bashLanded(player, now, CombatWeapons.shieldSpec(player.getUseItem()));
        return true;
    }

    private static final java.util.Map<java.util.UUID, Long> BASH_READY = new java.util.concurrent.ConcurrentHashMap<>();

    private static void bashLanded(Player player, long now, WeaponSpec spec) {
        int hitCooldown = spec == null ? WeaponSpec.DEFAULT_BASH_HIT_COOLDOWN : spec.bashHitCooldownTicks();
        BASH_READY.merge(player.getUUID(), now + hitCooldown, Math::max);
        CombatPlatform.sendToPlayer(player, new CombatPosePayload(player.getId(), CombatPosePayload.BASH_HIT));
    }

    public static boolean shieldBash(Player player) {
        if (!com.craisinlord.fightclub.CombatConfig.enabled(com.craisinlord.fightclub.CombatConfig.SHIELD_BASH)
                || !(player.level() instanceof net.minecraft.server.level.ServerLevel level)
                || !player.isUsingItem() || !isShield(player.getUseItem())) return false;
        long now = level.getGameTime();
        Long ready = BASH_READY.get(player.getUUID());
        if (ready != null && now < ready) return false;
        WeaponSpec spec = CombatWeapons.shieldSpec(player.getUseItem());
        double range = spec == null ? 2.5 : spec.reach();
        float angle = spec == null ? 100.0f : spec.blockingAngleDegrees();
        double strength = spec == null || spec.chargeKnockback() <= 0.0f ? 1.6 : spec.chargeKnockback();
        float damage = spec == null ? 0.0f : spec.attackDamage();
        int cooldown = spec == null || spec.recoveryTicks() <= 0 ? 25 : spec.recoveryTicks();
        BASH_READY.put(player.getUUID(), now + cooldown);
        CombatPlatform.sendPoseToTracking(player, new CombatPosePayload(player.getId(), CombatPosePayload.BASH));

        boolean hit = false;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range))) {
            if (target == player || !target.isAlive() || player.isAlliedTo(target)) continue;
            if (player.distanceTo(target) > range + target.getBbWidth() * 0.5 || !frontal(player, target, angle)) continue;
            if (damage > 0.0f) target.hurtServer(level, player.damageSources().playerAttack(player), damage);
            CombatPlatform.knockback(target, strength, player.getX() - target.getX(), player.getZ() - target.getZ(), player);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF,
                    target.getX(), target.getY(0.5), target.getZ(), 4, 0.2, 0.2, 0.2, 0.02);
            hit = true;
        }
        if (hit) bashLanded(player, now, spec);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), CombatSounds.SHIELD_BASH,
                net.minecraft.sounds.SoundSource.PLAYERS, hit ? 1.0f : 0.6f, 0.95f + level.getRandom().nextFloat() * 0.1f);
        return hit;
    }

    public static LivingEntity crosshairTarget(Player player, double reach) {
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(reach));
        AABB search = player.getBoundingBox().expandTowards(player.getLookAngle().scale(reach)).inflate(1.0);
        return player.level().getEntitiesOfClass(LivingEntity.class, search, target -> {
                    if (target == player || target.isRemoved() || target.isDeadOrDying()) return false;
                    return target.getBoundingBox().clip(start, end).isPresent();
                }).stream()
                .min(Comparator.comparingDouble(target -> target.distanceToSqr(player)))
                .orElse(null);
    }
}
