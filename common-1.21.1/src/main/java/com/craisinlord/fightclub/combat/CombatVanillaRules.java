package com.craisinlord.fightclub.combat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import com.craisinlord.fightclub.CombatConfig;

public final class CombatVanillaRules {
    private static final ResourceLocation LONGSWORD_OFFHAND = ResourceLocation.fromNamespaceAndPath("fight_club", "longsword_offhand");
    private CombatVanillaRules() {}

    public static boolean isSword(ItemStack stack) { return stack.getItem() instanceof SwordItem; }
    public static WeaponSpec swordSpec(ItemStack stack) {
        if (stack.getItem() instanceof CombatWeaponItem weapon && weapon.combatWeapon().equals("sword")) {
            return WeaponSpecs.getOrLoadBuiltin(weapon.combatMaterial(), "sword");
        }
        String[] materials = {"wood", "stone", "iron", "gold", "diamond", "netherite"};
        Item[] swords = {Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD,
                Items.GOLDEN_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD};
        for (int i = 0; i < swords.length; i++) if (stack.getItem() == swords[i])
            return WeaponSpecs.getOrLoadBuiltin(materials[i], "sword");
        return null;
    }
    public static boolean isShield(ItemStack stack) { return stack.is(Items.SHIELD); }
    public static boolean isMace(ItemStack stack) { return stack.is(Items.MACE); }

    /** Drops all temporary combat state for a player (logout, death, respawn). */
    public static void clearPlayerState(Player player) {
        CombatRecovery.clear(player);
        CombatChargeState.clear(player);
        CombatAttacks.clear(player);
        BASH_READY.remove(player.getUUID());
    }

    /** Runs from the loader attack events, before vanilla's attack math. */
    public static void beforeAttack(Player player) {
        CombatAttacks.captureStrength(player);
        applyLongswordRules(player);
    }

    /**
     * True when the held weapon never lands normal (jump) critical hits: longswords, and spears and
     * tridents, which crit only on charged hits (see {@link #chargeOnlyCrits}).
     */
    public static boolean suppressesCrits(Player player) {
        if (chargeOnlyCrits(player.getMainHandItem())) return true;
        return CombatConfig.enabled(CombatConfig.LONGSWORD_NO_CRITS)
                && player.getMainHandItem().getItem() instanceof CombatWeaponItem weapon
                && weapon.combatWeapon().equals("longsword");
    }

    /**
     * Spears and tridents whose spec says critical_hits: false crit only on charged hits
     * (spear_charge_crits): normal crits are suppressed, and every charge hit plays the crit effects.
     */
    public static boolean chargeOnlyCrits(ItemStack stack) {
        if (!CombatConfig.enabled(CombatConfig.SPEAR_CHARGE_CRITS)) return false;
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
        if (player.level().isClientSide || !chargeOnlyCrits(player.getMainHandItem())) return;
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_CRIT, player.getSoundSource(), 1.0f, 1.0f);
        player.crit(target);
    }

    /**
     * Vanilla's fixed sweep is replaced by CombatAttacks.sweep for every weapon with a spec (it
     * sweeps by the spec's radius and angle, and not at all for thrusting weapons).
     */
    public static boolean blocksVanillaSweep(Player player) {
        ItemStack stack = player.getMainHandItem();
        if (CombatConfig.enabled(CombatConfig.DATA_SWEEPS)) return CombatTooltips.specFor(stack) != null;
        // Data-driven sweeps off: vanilla sweeps for sweeping weapons, still none for daggers and spears.
        return CombatWeapons.isCombatWeapon(stack.getItem()) && !CombatWeapons.is(stack.getItem(), "longsword");
    }

    /**
     * Longsword offhand penalty. Runs every server tick, so it only touches the attribute when the
     * wanted value changes (each change marks the attribute dirty and syncs it to the client).
     */
    public static void applyLongswordRules(Player player) {
        AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeed == null) return;
        double wanted = 0.0;
        if (CombatConfig.enabled(CombatConfig.LONGSWORD_OFFHAND)
                && player.getMainHandItem().getItem() instanceof CombatWeaponItem weapon && weapon.combatWeapon().equals("longsword")
                && !player.getOffhandItem().isEmpty()) {
            WeaponSpec spec = WeaponSpecs.getOrLoadBuiltin(weapon.combatMaterial(), "longsword");
            if (spec != null) wanted = spec.offhandSpeedMultiplier() - 1.0;
        }
        AttributeModifier current = attackSpeed.getModifier(LONGSWORD_OFFHAND);
        if (current == null ? wanted == 0.0 : current.amount() == wanted) return;
        attackSpeed.removeModifier(LONGSWORD_OFFHAND);
        if (wanted != 0.0) {
            attackSpeed.addTransientModifier(new AttributeModifier(LONGSWORD_OFFHAND, wanted, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    public static boolean swordBlocks(Player defender, Entity attacker) {
        if (!CombatConfig.enabled(CombatConfig.SWORD_BLOCKING) || !defender.isUsingItem() || !isSword(defender.getUseItem())
                || !(attacker instanceof LivingEntity)) return false;
        WeaponSpec spec = swordSpec(defender.getUseItem());
        return spec != null && spec.blocking() && frontal(defender, attacker, spec.blockingAngleDegrees());
    }
    /** Clang and sparks where the blade caught the hit. Called after a sword block reduced damage. */
    public static void playSwordBlockEffects(Player defender, Entity attacker) {
        if (!(defender.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        level.playSound(null, defender.getX(), defender.getY(), defender.getZ(), CombatSounds.SWORD_BLOCK,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.95f + level.random.nextFloat() * 0.1f);
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
     * Shield bash: knocks back everything in a short frontal cone. A whiff only waits recovery_ticks
     * (tracked here); a bash that hits waits bash_hit_cooldown_ticks instead. Only the bash is
     * locked: it is not an item cooldown, so the shield can still be raised and block.
     */
    public static boolean shieldBash(Player player) {
        if (!CombatConfig.enabled(CombatConfig.SHIELD_BASH) || player.level().isClientSide || !player.isUsingItem()
                || !isShield(player.getUseItem())) return false;
        long now = player.level().getGameTime();
        Long ready = BASH_READY.get(player.getUUID());
        if (ready != null && now < ready) return false;
        WeaponSpec spec = WeaponSpecs.getOrLoadBuiltin("base", "shield");
        double range = spec == null ? 2.5 : spec.reach();
        float angle = spec == null ? 100.0f : spec.blockingAngleDegrees();
        double strength = spec == null ? 1.6 : spec.chargeKnockback();
        float damage = spec == null ? 0.0f : spec.attackDamage();
        int cooldown = spec == null || spec.recoveryTicks() <= 0 ? 25 : spec.recoveryTicks();
        BASH_READY.put(player.getUUID(), now + cooldown);
        CombatPlatform.sendPoseToTracking(player, new CombatPosePayload(player.getId(), CombatPosePayload.BASH));

        boolean hit = false;
        for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range))) {
            if (target == player || !target.isAlive() || player.isAlliedTo(target)) continue;
            if (player.distanceTo(target) > range + target.getBbWidth() * 0.5 || !frontal(player, target, angle)) continue;
            if (damage > 0.0f) target.hurt(player.damageSources().playerAttack(player), damage);
            target.knockback(strength, player.getX() - target.getX(), player.getZ() - target.getZ());
            if (player.level() instanceof net.minecraft.server.level.ServerLevel level) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF,
                        target.getX(), target.getY() + 0.1, target.getZ(), 4, 0.25, 0.05, 0.25, 0.02);
            }
            hit = true;
        }
        if (hit) bashLanded(player, now, spec);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                hit ? CombatSounds.SHIELD_BASH : net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_NODAMAGE,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.95f + player.getRandom().nextFloat() * 0.1f);
        return hit;
    }

    public static LivingEntity crosshairTarget(Player player, double reach) {
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(reach));
        net.minecraft.world.phys.AABB search = player.getBoundingBox()
                .expandTowards(player.getLookAngle().scale(reach)).inflate(1.0);
        return player.level().getEntitiesOfClass(LivingEntity.class, search, target -> {
                    if (target == player || target.isRemoved() || target.isDeadOrDying()) return false;
                    return target.getBoundingBox().clip(start, end).isPresent();
                }).stream()
                .min(java.util.Comparator.comparingDouble(target -> target.distanceToSqr(player)))
                .orElse(null);
    }
}
