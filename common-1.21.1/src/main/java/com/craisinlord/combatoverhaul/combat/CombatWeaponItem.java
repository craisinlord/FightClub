package com.craisinlord.combatoverhaul.combat;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.entity.LivingEntity;

public final class CombatWeaponItem extends SwordItem {
    private final String material;
    private final String weapon;

    public CombatWeaponItem(String material, String weapon, Tier tier) {
        super(tier, new Item.Properties().stacksTo(1).attributes(SwordItem.createAttributes(
                tier, Math.round(damage(material, weapon)), speed(material, weapon))));
        this.material = material;
        this.weapon = weapon;
    }

    public String combatMaterial() { return material; }
    public String combatWeapon() { return weapon; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (CombatRecovery.active(player)) {
            return InteractionResultHolder.fail(player.getItemInHand(hand));
        }
        if (hand == InteractionHand.MAIN_HAND && player.getOffhandItem().is(Items.SHIELD)) {
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        }
        if (weapon.equals("dagger") || weapon.equals("spear")) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(player.getItemInHand(hand));
        }
        return super.use(level, player, hand);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return weapon.equals("dagger") || weapon.equals("spear") ? 72000 : super.getUseDuration(stack, user);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return weapon.equals("dagger") || weapon.equals("spear") ? UseAnim.SPEAR : super.getUseAnimation(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int timeLeft) {
        if (level.isClientSide || !(user instanceof Player player)) return;
        if (CombatRecovery.active(player)) return;
        WeaponSpec spec = WeaponSpecs.getOrLoadBuiltin(material, weapon);
        if (spec == null) return;
        int heldTicks = getUseDuration(stack, player) - timeLeft;
        if (spec.throwing() && (weapon.equals("spear") || heldTicks < Math.max(1, spec.chargeTicks()))) {
            var projectile = weapon.equals("spear")
                    ? new SpearProjectile(level, player, stack.copy())
                    : new DaggerProjectile(level, player, stack.copy());
            projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, spec.throwVelocity(), 1.0f);
            level.addFreshEntity(projectile);
            if (!player.getAbilities().instabuild) stack.shrink(1);
            CombatRecovery.start(player, spec.recoveryTicks());
            return;
        }
        if (heldTicks < spec.chargeTicks()) return;
        LivingEntity target = ThrustTargeting.find(player, null, spec).stream().findFirst().orElse(null);
        if (target == null) return;
        player.attack(target);
        float bonus = Math.max(0.0f, spec.chargeDamage() - spec.attackDamage());
        if (bonus > 0.0f) target.hurt(player.damageSources().playerAttack(player), bonus);
        if (spec.chargeKnockback() > 0.0f) {
            target.knockback(spec.chargeKnockback(), player.getX() - target.getX(), player.getZ() - target.getZ());
        }
        CombatRecovery.start(player, spec.recoveryTicks());
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, net.minecraft.world.entity.LivingEntity target,
                             net.minecraft.world.entity.LivingEntity attacker) {
        if (attacker instanceof Player player && CombatRecovery.active(player)) return false;
        boolean result = super.hurtEnemy(stack, target, attacker);
        if (!attacker.level().isClientSide && attacker instanceof Player player) {
            WeaponSpec spec = WeaponSpecs.getOrLoadBuiltin(material, weapon);
            if (spec != null && spec.attackStyle() == WeaponSpec.AttackStyle.THRUST) {
                for (net.minecraft.world.entity.LivingEntity pierced : ThrustTargeting.find(player, target, spec)) {
                    player.attack(pierced);
                }
            }
        }
        return result;
    }

    private static float damage(String material, String weapon) {
        WeaponSpec spec = WeaponSpecs.getOrLoadBuiltin(material, weapon);
        return spec == null ? 1.0f : spec.attackDamage();
    }

    private static float speed(String material, String weapon) {
        WeaponSpec spec = WeaponSpecs.getOrLoadBuiltin(material, weapon);
        return spec == null ? 1.0f : spec.attackSpeed() - 4.0f;
    }
}
