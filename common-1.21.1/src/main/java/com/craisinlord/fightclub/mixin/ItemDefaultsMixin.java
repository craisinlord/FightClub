package com.craisinlord.fightclub.mixin;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import com.craisinlord.fightclub.combat.CombatVanillaRules;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import com.craisinlord.fightclub.combat.CombatRecovery;
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
    private void fightClub$vanillaSwordUse(Level level, Player player, InteractionHand hand,
                                                 CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        Item item = (Item) (Object) this;
        if (!fightClub$isBlockingSword(item)) return;
        if (hand == InteractionHand.MAIN_HAND && player.getOffhandItem().is(Items.SHIELD)) return;
        if (CombatRecovery.active(player)) cir.setReturnValue(InteractionResultHolder.fail(player.getItemInHand(hand)));
        else { player.startUsingItem(hand); cir.setReturnValue(InteractionResultHolder.consume(player.getItemInHand(hand))); }
    }

    @Inject(method = "getUseDuration", at = @At("HEAD"), cancellable = true)
    private void fightClub$vanillaSwordUseDuration(ItemStack stack, net.minecraft.world.entity.LivingEntity user,
                                                         CallbackInfoReturnable<Integer> cir) {
        if (fightClub$isBlockingSword((Item) (Object) this)) cir.setReturnValue(72000);
    }

    @Inject(method = "getUseAnimation", at = @At("HEAD"), cancellable = true)
    private void fightClub$vanillaSwordUseAnimation(ItemStack stack, CallbackInfoReturnable<UseAnim> cir) {
        if (fightClub$isBlockingSword((Item) (Object) this)) cir.setReturnValue(UseAnim.BLOCK);
    }

    // Only real swords block. Longswords, spears and daggers extend SwordItem but must not.
    private static boolean fightClub$isBlockingSword(Item item) {
        return com.craisinlord.fightclub.CombatConfig.enabled(com.craisinlord.fightclub.CombatConfig.SWORD_BLOCKING)
                && item instanceof net.minecraft.world.item.SwordItem
                && !(item instanceof com.craisinlord.fightclub.combat.CombatWeaponItem weapon
                     && !weapon.combatWeapon().equals("sword"));
    }

    @Inject(method = "inventoryTick", at = @At("HEAD"))
    private void fightClub$backportSpearPlayerStack(ItemStack stack, Level level, Entity entity, int slotId,
                                                    boolean isSelected, CallbackInfo ci) {
        if (entity instanceof Player) com.craisinlord.fightclub.combat.CombatWeapons.stripMobComponents(stack);
    }

    @Inject(method = "appendHoverText", at = @At("TAIL"))
    private void fightClub$vanillaWeaponTooltip(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag, CallbackInfo ci) {
        // Vanilla swords, axes, mace, trident and shield. Fight Club's own weapons add theirs in
        // CombatWeaponItem (which also reaches here through super), so skip them to avoid doubles.
        if ((Object) this instanceof com.craisinlord.fightclub.combat.CombatWeaponItem) return;
        com.craisinlord.fightclub.combat.CombatTooltips.append(stack, tooltip::add);
    }
}
