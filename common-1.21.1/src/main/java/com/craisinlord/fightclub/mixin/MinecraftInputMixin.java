package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.client.CombatInputClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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
        com.craisinlord.fightclub.client.LongswordSpinClient.onStartAttack();
        if (CombatInputClient.blocksAttack()
                || com.craisinlord.fightclub.client.LongswordSpinClient.attackNotReady((Minecraft) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "startAttack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;attack(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;)V"))
    private void fightClub$attackedEntity(CallbackInfoReturnable<Boolean> cir) {
        com.craisinlord.fightclub.client.LongswordSpinClient.onAttackEntity();
    }

    @Inject(method = "startAttack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;)V"))
    private void fightClub$attackWentThrough(CallbackInfoReturnable<Boolean> cir) {
        CombatInputClient.markAttackWentThrough();
        com.craisinlord.fightclub.client.LongswordSpinClient.onBlockSwing((Minecraft) (Object) this);
        net.minecraft.client.player.LocalPlayer player = ((Minecraft) (Object) this).player;
        if (player != null) CombatInputClient.onSwing(player);
    }

    @Inject(method = "startAttack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;resetAttackStrengthTicker()V"))
    private void fightClub$missSwing(CallbackInfoReturnable<Boolean> cir) {
        net.minecraft.client.player.LocalPlayer player = ((Minecraft) (Object) this).player;
        if (player != null) CombatInputClient.onMissSwing(player);
    }

    @com.llamalad7.mixinextras.injector.v2.WrapWithCondition(method = "handleKeybinds", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;continueAttack(Z)V"))
    private boolean fightClub$noMiningWhileCharging(Minecraft self, boolean leftClick) {
        if (com.craisinlord.fightclub.client.LongswordSpinClient.blocksMining(self, leftClick)) return false;
        return !(leftClick && CombatInputClient.blocksAttack());
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void fightClub$noUseWhileCharging(CallbackInfo ci) {
        if (CombatInputClient.isCharging()) ci.cancel();
    }
}
