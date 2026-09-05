package com.craisinlord.combatoverhaul.mixin;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public abstract class ItemDefaultsMixin {
    @Inject(method = "getDefaultMaxStackSize", at = @At("RETURN"), cancellable = true)
    private void combatOverhaul$potionStackSize(CallbackInfoReturnable<Integer> cir) {
        Item item = (Item) (Object) this;
        if (item == Items.POTION || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION) cir.setReturnValue(16);
    }

    @Inject(method = "inventoryTick", at = @At("TAIL"))
    private void combatOverhaul$tridentLoyalty(ItemStack stack, ServerLevel level, Entity entity,
                                               EquipmentSlot slot, CallbackInfo ci) {
        if ((Object) this != Items.TRIDENT) return;
        HolderLookup.Provider registries = level.registryAccess();
        var loyalty = registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOYALTY);
        if (EnchantmentHelper.getItemEnchantmentLevel(loyalty, stack) >= 3) return;
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(stack.getEnchantments());
        enchantments.set(loyalty, 3);
        EnchantmentHelper.setEnchantments(stack, enchantments.toImmutable());
    }
}
