package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.client.CombatInputClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftInputMixin {
    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void fightClub$claimThrustClick(CallbackInfo ci) {
        CombatInputClient.beforeKeybinds((Minecraft) (Object) this);
    }

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void fightClub$noAttackWhileCharging(CallbackInfoReturnable<Boolean> cir) {
        if (CombatInputClient.blocksAttack()
                || com.craisinlord.fightclub.client.LongswordSpinClient.attackNotReady((Minecraft) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "startAttack", at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;piercingAttack(Lnet/minecraft/world/item/component/SwingAnimation;Lnet/minecraft/world/item/component/PiercingWeapon;)V"),
            @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/component/SwingAnimation;Z)Z")})
    private void fightClub$attackWentThrough(CallbackInfoReturnable<Boolean> cir) {
        CombatInputClient.markAttackWentThrough();
        com.craisinlord.fightclub.client.LongswordSpinClient.onBlockSwing((Minecraft) (Object) this);
    }

    @Inject(method = "startAttack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;resetAttackStrengthTicker()V"))
    private void fightClub$missSwing(CallbackInfoReturnable<Boolean> cir) {
        net.minecraft.client.player.LocalPlayer player = ((Minecraft) (Object) this).player;
        if (player != null) CombatInputClient.onMissSwing(player);
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void fightClub$noMiningWhileCharging(boolean leftClick, CallbackInfo ci) {
        if (com.craisinlord.fightclub.client.LongswordSpinClient.blocksMining((Minecraft) (Object) this, leftClick)
                || (leftClick && CombatInputClient.blocksAttack())) ci.cancel();
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void fightClub$noUseWhileCharging(CallbackInfo ci) {
        if (CombatInputClient.blocksUse()) ci.cancel();
    }

    @Inject(method = "startUseItem", cancellable = true, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;useItem(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;"))
    private void fightClub$offhandBeforeWeapon(CallbackInfo ci, @com.llamalad7.mixinextras.sugar.Local net.minecraft.world.InteractionHand hand) {
        if (hand == net.minecraft.world.InteractionHand.MAIN_HAND
                && com.craisinlord.fightclub.client.OffhandPlacing.tryPlace((Minecraft) (Object) this)) ci.cancel();
    }

    @Redirect(method = "handleKeybinds", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;releaseUsingItem(Lnet/minecraft/world/entity/player/Player;)V"))
    private void fightClub$keepSpearCharge(MultiPlayerGameMode gameMode, Player player) {
        if (!CombatInputClient.isCharging()) gameMode.releaseUsingItem(player);
    }
}
