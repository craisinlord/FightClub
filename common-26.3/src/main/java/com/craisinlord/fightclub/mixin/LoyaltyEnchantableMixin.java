package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.combat.CombatChargeState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Spears and daggers take Loyalty (enchanting table and anvil) so thrown ones fly back, without
 * joining #minecraft:enchantable/trident, which would also let Riptide, Impaling and Channeling on.
 * Loyalty is recognised by its effect (trident return acceleration), so data packs that tweak it
 * still match.
 */
@Mixin(Enchantment.class)
public abstract class LoyaltyEnchantableMixin {
    @Shadow
    public abstract net.minecraft.core.component.DataComponentMap effects();

    @Inject(method = {"isSupportedItem", "isPrimaryItem", "canEnchant"}, at = @At("HEAD"), cancellable = true)
    private void fightClub$loyaltyOnThrownWeapons(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (effects().has(EnchantmentEffectComponents.TRIDENT_RETURN_ACCELERATION)
                && (CombatChargeState.isSpearWeapon(stack.getItem()) || CombatChargeState.isDagger(stack.getItem()))) {
            cir.setReturnValue(true);
        }
    }
}
