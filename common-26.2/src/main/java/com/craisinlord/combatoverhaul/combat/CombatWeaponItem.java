package com.craisinlord.combatoverhaul.combat;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceKey;

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
        float damage = spec == null ? 1.0f : spec.attackDamage() - tier.attackDamageBonus();
        float speed = spec == null ? 0.0f : spec.attackSpeed() - 4.0f;
        return new Item.Properties().setId(id).stacksTo(1).sword(tier, damage, speed);
    }

    public String combatMaterial() { return material; }
    public String combatWeapon() { return weapon; }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (CombatRecovery.active(player)) return InteractionResult.FAIL;
        if (hand == InteractionHand.MAIN_HAND && player.getOffhandItem().is(net.minecraft.world.item.Items.SHIELD)) return InteractionResult.PASS;
        if (weapon.equals("dagger") || weapon.equals("spear")) {
            player.startUsingItem(hand);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return weapon.equals("dagger") || weapon.equals("spear") ? 72000 : super.getUseDuration(stack, user);
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return weapon.equals("dagger") || weapon.equals("spear") ? ItemUseAnimation.SPEAR : super.getUseAnimation(stack);
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int timeLeft) {
        if (level.isClientSide() || !(user instanceof Player player) || CombatRecovery.active(player)) return false;
        WeaponSpec spec = WeaponSpecs.getOrLoadBuiltin(material, weapon);
        if (spec == null) return false;
        int heldTicks = getUseDuration(stack, player) - timeLeft;
        if (spec.throwing() && (weapon.equals("spear") || weapon.equals("dagger")
                && heldTicks < Math.max(1, spec.chargeTicks()))) {
            var projectile = weapon.equals("spear")
                    ? new SpearProjectile(level, player, stack.copy())
                    : new DaggerProjectile(level, player, stack.copy());
            projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, spec.throwVelocity(), 1.0f);
            level.addFreshEntity(projectile);
            if (!player.getAbilities().instabuild) stack.shrink(1);
            CombatRecovery.start(player, spec.recoveryTicks());
            return true;
        }
        if (heldTicks < spec.chargeTicks()) return false;
        LivingEntity target = ThrustTargeting.find(player, null, spec).stream().findFirst().orElse(null);
        if (target == null) return false;
        player.attack(target);
        float bonus = Math.max(0.0f, spec.chargeDamage() - spec.attackDamage());
        if (bonus > 0.0f) target.hurtServer((net.minecraft.server.level.ServerLevel) level, player.damageSources().playerAttack(player), bonus);
        if (spec.chargeKnockback() > 0.0f) CombatPlatform.knockback(target, spec.chargeKnockback(), player.getX() - target.getX(), player.getZ() - target.getZ(), player);
        CombatRecovery.start(player, spec.recoveryTicks());
        return true;
    }

    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker instanceof Player player && CombatRecovery.active(player)) return;
        super.hurtEnemy(stack, target, attacker);
        if (!attacker.level().isClientSide() && attacker instanceof Player player) {
            WeaponSpec spec = WeaponSpecs.getOrLoadBuiltin(material, weapon);
            if (spec != null && spec.attackStyle() == WeaponSpec.AttackStyle.THRUST) {
                for (LivingEntity pierced : ThrustTargeting.find(player, target, spec)) player.attack(pierced);
            }
        }
    }
}
