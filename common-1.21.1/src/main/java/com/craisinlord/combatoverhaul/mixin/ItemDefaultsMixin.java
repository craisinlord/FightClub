package com.craisinlord.combatoverhaul.mixin;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import com.craisinlord.combatoverhaul.combat.CombatRecovery;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.network.chat.Component;
import java.util.List;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public abstract class ItemDefaultsMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void combatOverhaul$vanillaSwordUse(Level level, Player player, InteractionHand hand,
                                                 CallbackInfoReturnable<InteractionResult> cir) {
        Item item = (Item) (Object) this;
        if (!(item instanceof net.minecraft.world.item.SwordItem)) return;
        if (hand == InteractionHand.MAIN_HAND && player.getOffhandItem().is(Items.SHIELD)) return;
        if (CombatRecovery.active(player)) cir.setReturnValue(InteractionResult.FAIL);
        else { player.startUsingItem(hand); cir.setReturnValue(InteractionResult.CONSUME); }
    }

    @Inject(method = "getUseDuration", at = @At("HEAD"), cancellable = true)
    private void combatOverhaul$vanillaSwordUseDuration(ItemStack stack, net.minecraft.world.entity.LivingEntity user,
                                                         CallbackInfoReturnable<Integer> cir) {
        if (((Object) this) instanceof net.minecraft.world.item.SwordItem) cir.setReturnValue(72000);
    }

    @Inject(method = "getUseAnimation", at = @At("HEAD"), cancellable = true)
    private void combatOverhaul$vanillaSwordUseAnimation(ItemStack stack, CallbackInfoReturnable<UseAnim> cir) {
        if (((Object) this) instanceof net.minecraft.world.item.SwordItem) cir.setReturnValue(UseAnim.BLOCK);
    }

    @Inject(method = "appendHoverText", at = @At("TAIL"))
    private void combatOverhaul$vanillaWeaponTooltip(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag, CallbackInfo ci) {
        Item item = (Item) (Object) this;
        String material = item == Items.MACE || item == Items.TRIDENT ? "base" : null;
        String weapon = item == Items.MACE ? "mace" : item == Items.TRIDENT ? "trident" : null;
        if (weapon == null) return;
        var spec = com.craisinlord.combatoverhaul.combat.WeaponSpecs.getOrLoadBuiltin(material, weapon);
        if (spec == null) return;
        tooltip.add(Component.literal("Reach: " + String.format(java.util.Locale.ROOT, "%.2f", spec.reach()) + " blocks"));
        tooltip.add(Component.literal("Attack style: " + spec.attackStyle().name().toLowerCase(java.util.Locale.ROOT)));
        tooltip.add(Component.literal("Thrust width: " + String.format(java.util.Locale.ROOT, "%.2f", spec.thrustWidth())));
        tooltip.add(Component.literal("Sweep: " + String.format(java.util.Locale.ROOT, "%.2f", spec.sweepRadius()) + " blocks / " + String.format(java.util.Locale.ROOT, "%.2f", spec.sweepAngleDegrees()) + "°"));
        tooltip.add(Component.literal("Penetration: " + spec.penetrationCount()));
        tooltip.add(Component.literal("Critical hits: " + (spec.criticalHits() ? "yes" : "no")));
        tooltip.add(Component.literal("Recovery: " + spec.recoveryTicks() + " ticks"));
        if (spec.throwing()) tooltip.add(Component.literal("Throw: " + String.format(java.util.Locale.ROOT, "%.2f", spec.throwVelocity()) + " velocity / " + String.format(java.util.Locale.ROOT, "%.2f", spec.throwRange()) + " blocks"));
    }
    @Inject(method = "getDefaultMaxStackSize", at = @At("RETURN"), cancellable = true)
    private void combatOverhaul$potionStackSize(CallbackInfoReturnable<Integer> cir) {
        Item item = (Item) (Object) this;
        if (item == Items.POTION || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION) cir.setReturnValue(16);
    }

    @Inject(method = "inventoryTick", at = @At("TAIL"))
    private void combatOverhaul$tridentLoyalty(ItemStack stack, Level level, Entity entity, int slotId,
                                               boolean isSelected, CallbackInfo ci) {
        if ((Object) this != Items.TRIDENT || level.isClientSide) return;

        HolderLookup.Provider registries = level.registryAccess();
        var loyalty = registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOYALTY);
        if (EnchantmentHelper.getItemEnchantmentLevel(loyalty, stack) >= 3) return;

        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(stack.getEnchantments());
        enchantments.set(loyalty, 3);
        EnchantmentHelper.setEnchantments(stack, enchantments.toImmutable().withTooltip(true));
    }
}
