package com.craisinlord.fightclub.combat;

import com.craisinlord.fightclub.FightClub;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceKey;

import java.util.function.Consumer;

public final class CombatWeaponItem extends Item {
    private final String material;
    private final String weapon;

    public CombatWeaponItem(String material, String weapon, ToolMaterial tier, ResourceKey<Item> id) {
        super(properties(material, weapon, tier, id));
        this.material = material;
        this.weapon = weapon;
    }

    private static Item.Properties properties(String material, String weapon, ToolMaterial tier, ResourceKey<Item> id) {
        WeaponSpec spec = WeaponSpecs.getOrLoadBuiltin(material, weapon);
        float damage = spec == null ? 0.0f : spec.attackDamage() - 1.0f - tier.attackDamageBonus();
        float speed = spec == null ? 0.0f : spec.attackSpeed() - 4.0f;
        Item.Properties properties = new Item.Properties().setId(id);
        if (weapon.equals("spear")) {
            float[] values = spearValues(material);
            return tier.applySwordProperties(properties.spear(tier, values[0], values[1], values[2], values[3], values[4], values[5], values[6], values[7], values[8]), damage, speed).stacksTo(1);
        }
        return properties.stacksTo(1).sword(tier, damage, speed);
    }

    private static float[] spearValues(String material) {
        return switch (material) {
            case "wood" -> new float[]{0.65f, 0.70f, 0.75f, 5.0f, 14.0f, 10.0f, 5.1f, 15.0f, 4.6f};
            case "stone" -> new float[]{0.75f, 0.82f, 0.70f, 4.5f, 13.0f, 9.0f, 5.1f, 13.75f, 4.6f};
            case "copper" -> new float[]{0.85f, 0.82f, 0.65f, 4.0f, 12.0f, 8.25f, 5.1f, 12.5f, 4.6f};
            case "iron" -> new float[]{0.95f, 0.95f, 0.60f, 2.5f, 11.0f, 6.75f, 5.1f, 11.25f, 4.6f};
            case "gold" -> new float[]{0.95f, 0.70f, 0.70f, 3.5f, 13.0f, 8.5f, 5.1f, 13.75f, 4.6f};
            case "diamond" -> new float[]{1.05f, 1.075f, 0.50f, 3.0f, 10.0f, 6.5f, 5.1f, 10.0f, 4.6f};
            default -> new float[]{1.15f, 1.20f, 0.40f, 2.5f, 9.0f, 5.5f, 5.1f, 8.75f, 4.6f};
        };
    }

    public String combatMaterial() { return material; }
    public String combatWeapon() { return weapon; }

    public boolean isPrimaryItemFor(ItemStack stack, net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> enchantment) {
        return enchantment.value().isPrimaryItem(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        CombatTooltips.append(stack, tooltip);
    }

    public static final int USE_DURATION = 72000;

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        InteractionResult result = use(material, weapon, player, hand);
        return result != null ? result : InteractionResult.PASS;
    }

    public static InteractionResult use(String material, String weapon, Player player, InteractionHand hand) {
        if (CombatRecovery.active(player)) return InteractionResult.FAIL;
        if (hand == InteractionHand.MAIN_HAND && CombatWeapons.isShield(player.getOffhandItem())) return InteractionResult.PASS;
        if (weapon.equals("dagger") || weapon.equals("spear")) {
            player.startUsingItem(hand);
            return InteractionResult.CONSUME;
        }
        if (weapon.equals("longsword") && hand == InteractionHand.MAIN_HAND && LongswordSpin.hasSpin(player.getItemInHand(hand))
                && !LongswordSpin.onSpinCooldown(player)) {
            player.startUsingItem(hand);
            return InteractionResult.CONSUME;
        }
        return null;
    }

    public static boolean canThrow(String material, String weapon) {
        if (!weapon.equals("dagger") && !weapon.equals("spear")) return false;
        WeaponSpec spec = WeaponSpecs.getOrLoadBuiltin(material, weapon);
        return spec != null && spec.throwing()
                && com.craisinlord.fightclub.CombatConfig.enabled(com.craisinlord.fightclub.CombatConfig.THROWING);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return weapon.equals("dagger") || weapon.equals("spear") || weapon.equals("longsword") ? USE_DURATION : super.getUseDuration(stack, user);
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        if (weapon.equals("spear") || weapon.equals("dagger")) return ItemUseAnimation.TRIDENT;
        return super.getUseAnimation(stack);
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int timeLeft) {
        return releaseUsing(material, weapon, stack, level, user, timeLeft);
    }

    public static boolean releaseUsing(String material, String weapon, ItemStack stack, Level level, LivingEntity user, int timeLeft) {
        if (level.isClientSide() || !(user instanceof Player player)) return false;
        if ((weapon.equals("dagger") || weapon.equals("spear")) && CombatChargeState.isLeftCharging(player)) {
            CombatChargeState.releaseLeft(player);
            return true;
        }
        if (weapon.equals("longsword")) {
            LongswordSpin.release(player, stack, USE_DURATION - timeLeft);
            return true;
        }
        if (CombatRecovery.active(player)) return false;
        WeaponSpec spec = WeaponSpecs.getOrLoadBuiltin(material, weapon);
        if (spec == null) return false;
        int heldTicks = USE_DURATION - timeLeft;
        if (spec.throwing() && (weapon.equals("spear") || weapon.equals("dagger"))) {
            if (heldTicks < spec.throwChargeTicks()) return false;
            ItemStack thrownStack = stack.copy();
            thrownStack.setCount(1);
            var projectile = weapon.equals("spear")
                    ? new SpearProjectile(level, player, thrownStack)
                    : new DaggerProjectile(level, player, thrownStack);
            projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, spec.throwVelocity(), 1.0f);
            level.addFreshEntity(projectile);
            level.playSound(null, player, SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0f, 1.0f);
            if (!player.getAbilities().instabuild) stack.shrink(1);
            return true;
        }
        if (heldTicks < spec.chargeTicks()) return false;
        LivingEntity target = ThrustTargeting.find(player, null, spec).stream().findFirst().orElse(null);
        if (target == null) return false;
        player.attack(target);
        float bonus = Math.max(0.0f, spec.chargeDamage() - CombatChargeState.hitDamage(player, spec));
        if (bonus > 0.0f) target.hurtServer((net.minecraft.server.level.ServerLevel) level, player.damageSources().playerAttack(player), bonus);
        if (spec.chargeKnockback() > 0.0f) CombatPlatform.knockback(target, spec.chargeKnockback(), player.getX() - target.getX(), player.getZ() - target.getZ(), player);
        CombatRecovery.start(player, spec.recoveryTicks());
        return true;
    }

    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker instanceof Player player && CombatRecovery.active(player)) return;
        super.hurtEnemy(stack, target, attacker);
        afterHit(material, weapon, target, attacker);
    }

    private static boolean piercing;

    public static void afterHit(String material, String weapon, LivingEntity target, LivingEntity attacker) {
        if (!attacker.level().isClientSide() && attacker instanceof Player player) {
            WeaponSpec spec = WeaponSpecs.getOrLoadBuiltin(material, weapon);
            if (spec != null && spec.attackStyle() == WeaponSpec.AttackStyle.THRUST && !piercing) {
                piercing = true;
                try {
                    for (LivingEntity pierced : ThrustTargeting.find(player, target, spec)) player.attack(pierced);
                } finally {
                    piercing = false;
                }
            }
            if (spec != null && weapon.equals("longsword") && !LongswordSpin.onSpinCooldown(player)) CombatRecovery.start(player, spec.recoveryTicks());
        }
    }
}
