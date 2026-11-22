package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.combat.CombatEnchanting;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Enchantment.class)
public abstract class WeaponEnchantableMixin {
    @Inject(method = {"isSupportedItem", "canEnchant"}, at = @At("HEAD"), cancellable = true)
    private void fightClub$supported(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (CombatEnchanting.supports((Enchantment) (Object) this, stack)) cir.setReturnValue(true);
    }

    @Inject(method = "isPrimaryItem", at = @At("HEAD"), cancellable = true)
    private void fightClub$primary(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (CombatEnchanting.primary((Enchantment) (Object) this, stack)) cir.setReturnValue(true);
    }
}
