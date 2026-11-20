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
    private CombatVanillaRules() {}

    public static boolean isSword(ItemStack stack) { return stack.getItem() instanceof CombatWeaponItem weapon && weapon.combatWeapon().equals("sword") || isVanillaSword(stack.getItem()); }
    public static boolean isVanillaSword(Item item) { return item == Items.WOODEN_SWORD || item == Items.STONE_SWORD || item == Items.COPPER_SWORD || item == Items.IRON_SWORD || item == Items.GOLDEN_SWORD || item == Items.DIAMOND_SWORD || item == Items.NETHERITE_SWORD; }
    public static WeaponSpec swordSpec(ItemStack stack) {
        if (stack.getItem() instanceof CombatWeaponItem weapon && weapon.combatWeapon().equals("sword")) return WeaponSpecs.getOrLoadBuiltin(weapon.combatMaterial(), "sword");
        String[] materials = {"wood", "stone", "copper", "iron", "gold", "diamond", "netherite"};
        Item[] swords = {Items.WOODEN_SWORD, Items.STONE_SWORD, Items.COPPER_SWORD, Items.IRON_SWORD, Items.GOLDEN_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD};
        for (int i = 0; i < swords.length; i++) if (stack.getItem() == swords[i]) return WeaponSpecs.getOrLoadBuiltin(materials[i], "sword");
        return null;
    }
    public static boolean isShield(ItemStack stack) { return stack.is(Items.SHIELD); }
    public static boolean isMace(ItemStack stack) { return stack.is(Items.MACE); }

    /** True when the held weapon can't land normal (jump) crits; read by PlayerCritMixin. */
    public static boolean suppressesCrits(Player player) {
        return chargeOnlyCrits(player.getMainHandItem());
    }

    /**
     * Spears and tridents whose spec says critical_hits: false crit only on charged hits
     * (spear_charge_crits): normal crits are suppressed, and every charge hit plays the crit effects.
     */
    public static boolean chargeOnlyCrits(ItemStack stack) {
        if (!com.craisinlord.fightclub.CombatConfig.enabled(com.craisinlord.fightclub.CombatConfig.SPEAR_CHARGE_CRITS)) return false;
        String kind = CombatTooltips.kindOf(stack);
        if (!"spear".equals(kind) && !"trident".equals(kind)) return false;
        WeaponSpec spec = CombatTooltips.specFor(stack);
        return spec != null && !spec.criticalHits();
    }

    /**
     * Crit feedback for a charge hit: vanilla's crit sound and crit particles, server side. Only
     * feedback: the charge damage is the bonus, so there is no 1.5x on top.
     */
    public static void chargeCritEffects(Player player, Entity target) {
        if (player.level().isClientSide() || !chargeOnlyCrits(player.getMainHandItem())) return;
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_CRIT, player.getSoundSource(), 1.0f, 1.0f);
        player.crit(target);
    }

    /**
     * True while a spear's left-click charge (vanilla kinetic item use) is running on the server,
     * so PlayerCritMixin turns its kinetic stab hits into crits for feedback.
     */
    public static boolean spearChargeCrits(Player player) {
        return !player.level().isClientSide() && CombatChargeState.isLeftCharging(player) && player.isUsingItem()
                && CombatChargeState.usesItemForCharge(player.getUseItem().getItem()) && chargeOnlyCrits(player.getUseItem());
    }

    public static void applyLongswordRules(Player player) {
        AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeed != null) attackSpeed.removeModifier(LONGSWORD_OFFHAND);
        if (player.getMainHandItem().getItem() instanceof CombatWeaponItem weapon && weapon.combatWeapon().equals("longsword")) {
            player.resetFallDistance();
            if (attackSpeed != null) {
                WeaponSpec spec = WeaponSpecs.getOrLoadBuiltin(weapon.combatMaterial(), "longsword");
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
        // Same bash timer as the main bash below, not an item cooldown (that would stop blocking).
        long now = player.level().getGameTime();
        Long ready = BASH_READY.get(player.getUUID());
        if (ready != null && now < ready) return false;
        if (!frontal(player, target, 90.0f)) return false;
        CombatPlatform.knockback((LivingEntity) target, 1.6, player.getX() - target.getX(), player.getZ() - target.getZ(), player);
        bashLanded(player, now, WeaponSpecs.getOrLoadBuiltin("base", "shield"));
        return true;
    }

    private static final java.util.Map<java.util.UUID, Long> BASH_READY = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * A bash hit something: lock bashing (only bashing; blocking keeps working) for
     * bash_hit_cooldown_ticks, and tell the basher's client so its own bash timer matches.
     */
    private static void bashLanded(Player player, long now, WeaponSpec spec) {
        int hitCooldown = spec == null ? WeaponSpec.DEFAULT_BASH_HIT_COOLDOWN : spec.bashHitCooldownTicks();
        BASH_READY.merge(player.getUUID(), now + hitCooldown, Math::max);
        CombatPlatform.sendToPlayer(player, new CombatPosePayload(player.getId(), CombatPosePayload.BASH_HIT));
    }

    /**
     * Attack pressed while blocking with a shield (sent by the client, since vanilla swallows
     * attack clicks during item use): shove everything in front back. Ported from 1.21.1.
     */
    public static boolean shieldBash(Player player) {
        if (!com.craisinlord.fightclub.CombatConfig.enabled(com.craisinlord.fightclub.CombatConfig.SHIELD_BASH)
                || !(player.level() instanceof net.minecraft.server.level.ServerLevel level)
                || !player.isUsingItem() || !isShield(player.getUseItem())) return false;
        long now = level.getGameTime();
        Long ready = BASH_READY.get(player.getUUID());
        if (ready != null && now < ready) return false;
        WeaponSpec spec = WeaponSpecs.getOrLoadBuiltin("base", "shield");
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
