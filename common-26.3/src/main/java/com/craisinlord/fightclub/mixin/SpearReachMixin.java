package com.craisinlord.fightclub.mixin;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.AttackRange;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Item.Properties.class)
public abstract class SpearReachMixin {
    @ModifyArg(method = "spear", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/Item$Properties;component(Lnet/minecraft/core/component/DataComponentType;Ljava/lang/Object;)Lnet/minecraft/world/item/Item$Properties;"),
            index = 1)
    private Object fightClub$noMinimumReach(Object value) {
        if (!(value instanceof AttackRange range)) return value;
        return new AttackRange(0.0F, range.maxReach(), 0.0F, range.maxCreativeReach(), range.hitboxMargin(), range.mobFactor());
    }
}
