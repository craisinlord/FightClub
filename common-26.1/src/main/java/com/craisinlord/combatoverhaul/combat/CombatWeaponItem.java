package com.craisinlord.combatoverhaul.combat;

import com.craisinlord.combatoverhaul.CombatOverhaul;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

public final class CombatWeaponItem extends Item {
    private final String material;
    private final String weapon;

    public CombatWeaponItem(String material, String weapon, ToolMaterial tier) {
        super(properties(material, weapon, tier));
        this.material = material;
        this.weapon = weapon;
    }

    private static Item.Properties properties(String material, String weapon, ToolMaterial tier) {
        WeaponSpec spec = WeaponSpecs.getOrLoadBuiltin(material, weapon);
        float damage = spec == null ? 1.0f : spec.attackDamage() - tier.attackDamageBonus();
        float speed = spec == null ? 0.0f : spec.attackSpeed() - 4.0f;
        return new Item.Properties().stacksTo(1).sword(tier, damage, speed);
    }

    public String combatMaterial() { return material; }
    public String combatWeapon() { return weapon; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        WeaponSpec spec = WeaponSpecs.getOrLoadBuiltin(material, weapon);
        if (spec == null) return;
        tooltip.accept(Component.literal("Reach: " + format(spec.reach()) + " blocks"));
        tooltip.accept(Component.literal("Attack style: " + spec.attackStyle().name().toLowerCase(java.util.Locale.ROOT)));
        tooltip.accept(Component.literal("Thrust width: " + format(spec.thrustWidth())));
        tooltip.accept(Component.literal("Sweep: " + format(spec.sweepRadius()) + " blocks / " + format(spec.sweepAngleDegrees()) + "°"));
        tooltip.accept(Component.literal("Penetration: " + spec.penetrationCount()));
        tooltip.accept(Component.literal("Critical hits: " + (spec.criticalHits() ? "yes" : "no")));
        tooltip.accept(Component.literal("Charge: " + spec.chargeTicks() + " ticks, " + format(spec.chargeDamage()) + " damage"));
        tooltip.accept(Component.literal("Recovery: " + spec.recoveryTicks() + " ticks"));
        if (spec.throwing()) tooltip.accept(Component.literal("Throw: " + format(spec.throwVelocity()) + " velocity / " + format(spec.throwRange()) + " blocks"));
    }

    private static String format(float value) { return String.format(java.util.Locale.ROOT, "%.2f", value); }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        CombatOverhaul.LOGGER.info("[TEMP WEAPON] use item={} weapon={} material={} side={} hand={}",
                player.getItemInHand(hand), weapon, material, level.isClientSide() ? "client" : "server", hand);
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
        CombatOverhaul.LOGGER.info("[TEMP WEAPON] release item={} weapon={} material={} side={} timeLeft={}",
                stack, weapon, material, level.isClientSide() ? "client" : "server", timeLeft);
        if (level.isClientSide() || !(user instanceof Player player) || CombatRecovery.active(player)) return false;
        WeaponSpec spec = WeaponSpecs.getOrLoadBuiltin(material, weapon);
        CombatOverhaul.LOGGER.info("[TEMP WEAPON] release decision weapon={} specPresent={} throwing={} heldTicks={} chargeTicks={}",
                weapon, spec != null, spec != null && spec.throwing(),
                spec == null ? -1 : getUseDuration(stack, player) - timeLeft,
                spec == null ? -1 : spec.chargeTicks());
        if (spec == null) return false;
        int heldTicks = getUseDuration(stack, player) - timeLeft;
        if (spec.throwing() && (weapon.equals("spear") || weapon.equals("dagger"))) {
            ItemStack thrownStack = stack.copy();
            thrownStack.setCount(1);
            var projectile = weapon.equals("spear")
                    ? new SpearProjectile(level, player, thrownStack)
                    : new DaggerProjectile(level, player, thrownStack);
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
        if (spec.chargeKnockback() > 0.0f) target.knockback(spec.chargeKnockback(), player.getX() - target.getX(), player.getZ() - target.getZ());
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
