package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.CombatConfig;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ThrowablePotionItem.class)
public abstract class ThrowablePotionItemMixin {
    @Unique
    private static final int FIGHT_CLUB$COOLDOWN = 10;

    @Inject(method = "use", at = @At("RETURN"))
    private void fightClub$throwCooldown(Level level, Player player, InteractionHand hand,
                                         CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if (!cir.getReturnValue().getResult().consumesAction() || !CombatConfig.enabled(CombatConfig.POTION_COOLDOWN)) return;
        player.getCooldowns().addCooldown(Items.SPLASH_POTION, FIGHT_CLUB$COOLDOWN);
        player.getCooldowns().addCooldown(Items.LINGERING_POTION, FIGHT_CLUB$COOLDOWN);
    }
}
