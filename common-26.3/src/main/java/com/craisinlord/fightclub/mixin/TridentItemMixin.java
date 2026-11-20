package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.combat.CombatRecovery;
import com.craisinlord.fightclub.combat.CombatChargeState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * As on 1.21.1, right-click stays vanilla: use() keeps the durability and Riptide checks and the
 * release spawns a real ThrownTrident, so Loyalty, Channeling, Impaling and Riptide all work and it
 * renders with vanilla's trident renderer. Only recovery blocks it, and a right-click release that
 * arrives during a left-click charge ends that charge instead of throwing.
 */
@Mixin(TridentItem.class)
public abstract class TridentItemMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void fightClub$use(Level level, Player player, InteractionHand hand,
                                    CallbackInfoReturnable<InteractionResult> cir) {
        if (CombatRecovery.active(player)) cir.setReturnValue(InteractionResult.FAIL);
    }

    @Inject(method = "getUseDuration", at = @At("HEAD"), cancellable = true)
    private void fightClub$useDuration(ItemStack stack, LivingEntity user,
                                             CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(72000);
    }

    @Inject(method = "getUseAnimation", at = @At("HEAD"), cancellable = true)
    private void fightClub$useAnimation(ItemStack stack,
                                              CallbackInfoReturnable<ItemUseAnimation> cir) {
        cir.setReturnValue(ItemUseAnimation.TRIDENT);
    }

    @Inject(method = "releaseUsing", at = @At("HEAD"), cancellable = true)
    private void fightClub$releaseUsing(ItemStack stack, Level level, LivingEntity user, int timeLeft,
                                              CallbackInfoReturnable<Boolean> cir) {
        if (level.isClientSide() || !(user instanceof Player player)) return;
        if (CombatChargeState.isLeftCharging(player)) {
            CombatChargeState.releaseLeft(player);
            cir.setReturnValue(true);
            return;
        }
        if (CombatRecovery.active(player)) cir.setReturnValue(false);
    }
}
