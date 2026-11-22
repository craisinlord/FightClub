package com.craisinlord.fightclub.combat;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

public final class CombatWeaponItem extends SwordItem {
    private final String material;
    private final String weapon;

    public CombatWeaponItem(String material, String weapon, Tier tier) {
        this(material, weapon, tier, new Item.Properties());
    }

    public CombatWeaponItem(String material, String weapon, Tier tier, Item.Properties properties) {
        super(tier, properties.stacksTo(1));
        this.material = material;
        this.weapon = weapon;
    }

    public String combatMaterial() { return material; }
    public String combatWeapon() { return weapon; }

    public boolean isPrimaryItemFor(ItemStack stack, net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> enchantment) {
        return enchantment.value().isPrimaryItem(stack);
    }

    @Override
    @SuppressWarnings("deprecation")
    public net.minecraft.world.item.component.ItemAttributeModifiers getDefaultAttributeModifiers() {
        return CombatWeaponStats.defaultModifiers(this);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        CombatTooltips.append(stack, tooltip::add);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        InteractionResultHolder<ItemStack> result = use(material, weapon, player, hand);
        return result != null ? result : super.use(level, player, hand);
    }

    public static InteractionResultHolder<ItemStack> use(String material, String weapon, Player player, InteractionHand hand) {
        if (CombatRecovery.active(player)) {
            return InteractionResultHolder.fail(player.getItemInHand(hand));
        }
        if (hand == InteractionHand.MAIN_HAND && CombatWeapons.isShield(player.getOffhandItem())) {
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        }
        if (canThrow(material, weapon)) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.isDamageableItem() && stack.getDamageValue() >= stack.getMaxDamage() - 1) return InteractionResultHolder.fail(stack);
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }
        if (weapon.equals("longsword") && hand == InteractionHand.MAIN_HAND && LongswordSpin.hasSpin(player.getItemInHand(hand))
                && !LongswordSpin.onSpinCooldown(player)) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(player.getItemInHand(hand));
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
        return canThrow(material, weapon) || weapon.equals("longsword") ? 72000 : super.getUseDuration(stack, user);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return canThrow(material, weapon) ? UseAnim.SPEAR : super.getUseAnimation(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int timeLeft) {
        releaseUsing(material, weapon, stack, level, user, timeLeft);
    }

    public static void releaseUsing(String material, String weapon, ItemStack stack, Level level, LivingEntity user, int timeLeft) {
        if (weapon.equals("longsword")) {
            if (!level.isClientSide && user instanceof Player player) LongswordSpin.release(player, stack, 72000 - timeLeft);
            return;
        }
        if (level.isClientSide || !(user instanceof Player player) || CombatRecovery.active(player) || !canThrow(material, weapon)) return;
        WeaponSpec spec = WeaponSpecs.getOrLoadBuiltin(material, weapon);
        if (spec == null || 72000 - timeLeft < spec.throwChargeTicks()) return;
        stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(player.getUsedItemHand()));
        ItemStack thrownStack = stack.copyWithCount(1);
        CombatProjectile projectile = weapon.equals("spear")
                ? new SpearProjectile(level, player, thrownStack)
                : new DaggerProjectile(level, player, thrownStack);
        projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, spec.throwVelocity(), 1.0f);
        level.addFreshEntity(projectile);
        level.playSound(null, player, weapon.equals("spear") ? CombatSounds.SPEAR_THROW : CombatSounds.DAGGER_THROW,
                SoundSource.PLAYERS, 1.0f, 0.95f + level.random.nextFloat() * 0.1f);
        Item item = stack.getItem();
        if (!player.getAbilities().instabuild) stack.shrink(1);
        CombatRecovery.start(player, spec.recoveryTicks(), item);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker instanceof Player player && CombatRecovery.active(player)) return false;
        boolean result = super.hurtEnemy(stack, target, attacker);
        com.craisinlord.fightclub.compat.CompatAbilities.onHit(stack, target, attacker, com.craisinlord.fightclub.compat.CompatAbilities.Hit.SWING);
        afterHit(material, weapon, target, attacker);
        return result;
    }

    public static void afterHit(String material, String weapon, LivingEntity target, LivingEntity attacker) {
        if (attacker.level().isClientSide || !(attacker instanceof Player player)) return;
        WeaponSpec spec = WeaponSpecs.getOrLoadBuiltin(material, weapon);
        if (spec == null) return;
        if (spec.attackStyle() == WeaponSpec.AttackStyle.THRUST) CombatAttacks.pierce(player, target, spec);
        if (spec.attackStyle() == WeaponSpec.AttackStyle.SWEEP && !(weapon.equals("longsword") && LongswordSpin.sweepsArc(player))) {
            CombatAttacks.sweep(player, target, spec);
        }
        if (weapon.equals("longsword") && !LongswordSpin.onSpinCooldown(player)) CombatRecovery.start(player, spec.recoveryTicks());
    }
}
