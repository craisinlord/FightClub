package com.craisinlord.combatoverhaul.combat;

import com.craisinlord.combatoverhaul.CombatOverhaul;

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
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.TooltipContext;
import java.util.List;

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
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        WeaponSpec spec = WeaponSpecs.getOrLoadBuiltin(material, weapon);
        if (spec == null) return;
        tooltip.add(Component.literal("Reach: " + format(spec.reach()) + " blocks"));
        tooltip.add(Component.literal("Attack style: " + spec.attackStyle().name().toLowerCase(java.util.Locale.ROOT)));
        tooltip.add(Component.literal("Thrust width: " + format(spec.thrustWidth())));
        tooltip.add(Component.literal("Sweep: " + format(spec.sweepRadius()) + " blocks / " + format(spec.sweepAngleDegrees()) + "°"));
        tooltip.add(Component.literal("Penetration: " + spec.penetrationCount()));
        tooltip.add(Component.literal("Critical hits: " + (spec.criticalHits() ? "yes" : "no")));
        tooltip.add(Component.literal("Charge: " + spec.chargeTicks() + " ticks, " + format(spec.chargeDamage()) + " damage"));
        tooltip.add(Component.literal("Recovery: " + spec.recoveryTicks() + " ticks"));
        if (spec.throwing()) tooltip.add(Component.literal("Throw: " + format(spec.throwVelocity()) + " velocity / " + format(spec.throwRange()) + " blocks"));
    }

    private static String format(float value) { return String.format(java.util.Locale.ROOT, "%.2f", value); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        CombatOverhaul.LOGGER.info("[TEMP WEAPON] use item={} weapon={} material={} side={} hand={}",
                player.getItemInHand(hand), weapon, material, level.isClientSide() ? "client" : "server", hand);
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
        CombatOverhaul.LOGGER.info("[TEMP WEAPON] release item={} weapon={} material={} side={} timeLeft={}",
                stack, weapon, material, level.isClientSide() ? "client" : "server", timeLeft);
        if (level.isClientSide || !(user instanceof Player player)) return;
        if (CombatRecovery.active(player)) return;
        WeaponSpec spec = WeaponSpecs.getOrLoadBuiltin(material, weapon);
        CombatOverhaul.LOGGER.info("[TEMP WEAPON] release decision weapon={} specPresent={} throwing={} heldTicks={} chargeTicks={}",
                weapon, spec != null, spec != null && spec.throwing(),
                spec == null ? -1 : getUseDuration(stack, player) - timeLeft,
                spec == null ? -1 : spec.chargeTicks());
        if (spec == null) return;
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
