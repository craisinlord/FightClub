package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.combat.CombatTooltips;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ShieldItem.class)
public abstract class ShieldItemMixin {
    @Inject(method = "appendHoverText", at = @At("TAIL"))
    private void fightClub$shieldTooltip(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                              TooltipFlag flag, CallbackInfo ci) {
        CombatTooltips.append(stack, tooltip::add);
    }
}
