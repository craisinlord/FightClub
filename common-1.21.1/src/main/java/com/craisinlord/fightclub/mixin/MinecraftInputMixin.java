package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.client.CombatInputClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** While a hold-left-click charge (or a dagger pounce) is active, the key must not mine, re-attack or use items. */
@Mixin(Minecraft.class)
public abstract class MinecraftInputMixin {
    /**
     * Spear/trident: claim the attack click before vanilla or Better Combat see it, so the
     * tap-or-hold decision is ours alone (see CombatInputClient#beforeKeybinds).
     */
    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void fightClub$claimThrustClick(CallbackInfo ci) {
        CombatInputClient.beforeKeybinds((Minecraft) (Object) this);
    }

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void fightClub$noAttackWhileCharging(CallbackInfoReturnable<Boolean> cir) {
        if (CombatInputClient.blocksAttack()) cir.setReturnValue(false);
    }

    /** Vanilla went through with the attack (it wasn't dropped for a whiff lockout etc.): the tap landed. */
    @Inject(method = "startAttack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;)V"))
    private void fightClub$attackWentThrough(CallbackInfoReturnable<Boolean> cir) {
        CombatInputClient.markAttackWentThrough();
    }

    /**
     * startAttack only resets the attack timer when the swing hit nothing (air):
     * tell the server, which never hears about misses otherwise (longsword air sweep, mace streak).
     */
    @Inject(method = "startAttack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;resetAttackStrengthTicker()V"))
    private void fightClub$missSwing(CallbackInfoReturnable<Boolean> cir) {
        net.minecraft.client.player.LocalPlayer player = ((Minecraft) (Object) this).player;
        if (player != null) CombatInputClient.onMissSwing(player);
    }

    /**
     * While the key is held for a charge (or a spear tap is being decided), skip the per-tick
     * continueAttack call entirely. Skipping the call, rather than cancelling inside the method,
     * also skips Better Combat's hold-to-attack hook in there, whatever order the two mods' hooks
     * run in, so Better Combat doesn't need pausing (which would cancel a swing already in flight).
     * With the key up the call still runs: it clears vanilla's whiff lockout.
     */
    @com.llamalad7.mixinextras.injector.v2.WrapWithCondition(method = "handleKeybinds", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;continueAttack(Z)V"))
    private boolean fightClub$noMiningWhileCharging(Minecraft self, boolean leftClick) {
        return !(leftClick && CombatInputClient.blocksAttack());
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void fightClub$noUseWhileCharging(CallbackInfo ci) {
        if (CombatInputClient.isCharging()) ci.cancel();
    }
}
