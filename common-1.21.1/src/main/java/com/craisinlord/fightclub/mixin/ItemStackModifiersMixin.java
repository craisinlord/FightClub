package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.combat.CombatWeaponStats;
import com.craisinlord.fightclub.compat.CompatAbilities;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BiConsumer;

@Mixin(ItemStack.class)
public abstract class ItemStackModifiersMixin {
    @Inject(method = "forEachModifier(Lnet/minecraft/world/entity/EquipmentSlotGroup;Ljava/util/function/BiConsumer;)V", at = @At("TAIL"))
    private void fightClub$groupModifiers(EquipmentSlotGroup group, BiConsumer<Holder<Attribute>, AttributeModifier> action, CallbackInfo ci) {
        if (group != EquipmentSlotGroup.MAINHAND) return;
        CombatWeaponStats.forEachMainHand((ItemStack) (Object) this, action);
        CompatAbilities.forEachMainHand((ItemStack) (Object) this, action);
    }

    @Inject(method = "forEachModifier(Lnet/minecraft/world/entity/EquipmentSlot;Ljava/util/function/BiConsumer;)V", at = @At("TAIL"))
    private void fightClub$slotModifiers(EquipmentSlot slot, BiConsumer<Holder<Attribute>, AttributeModifier> action, CallbackInfo ci) {
        if (slot != EquipmentSlot.MAINHAND) return;
        CombatWeaponStats.forEachMainHand((ItemStack) (Object) this, action);
        CompatAbilities.forEachMainHand((ItemStack) (Object) this, action);
    }
}
