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

/**
 * While a hold-left-click charge is active, the key must not mine, re-attack or use items, and a
 * spear charge's item use (held by the attack key, not the use key) must not be released.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftInputMixin {
    /**
     * Spear/trident: claim the attack click before vanilla sees it, so the tap-or-hold decision
     * is ours alone (see CombatInputClient#beforeKeybinds).
     */
    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void fightClub$claimThrustClick(CallbackInfo ci) {
        CombatInputClient.beforeKeybinds((Minecraft) (Object) this);
    }

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void fightClub$noAttackWhileCharging(CallbackInfoReturnable<Boolean> cir) {
        if (CombatInputClient.blocksAttack()) cir.setReturnValue(false);
    }

    /**
     * Vanilla went through with the attack (not dropped for an unready weapon etc.): the tap landed.
     * In 26.3 a spear jab goes through piercingAttack (which swings itself), anything else through swing.
     */
    @Inject(method = "startAttack", at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;piercingAttack(Lnet/minecraft/world/item/component/SwingAnimation;Lnet/minecraft/world/item/component/PiercingWeapon;)V"),
            @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/component/SwingAnimation;Z)Z")})
    private void fightClub$attackWentThrough(CallbackInfoReturnable<Boolean> cir) {
        CombatInputClient.markAttackWentThrough();
    }

    /**
     * startAttack only resets the attack timer when the swing hit nothing (air, or an air block):
     * tell the server, which never hears about misses otherwise (longsword air sweep, mace streak).
     */
    @Inject(method = "startAttack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;resetAttackStrengthTicker()V"))
    private void fightClub$missSwing(CallbackInfoReturnable<Boolean> cir) {
        net.minecraft.client.player.LocalPlayer player = ((Minecraft) (Object) this).player;
        if (player != null) CombatInputClient.onMissSwing(player);
    }

    /** With the key up continueAttack still runs: it clears vanilla's whiff lockout. */
    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void fightClub$noMiningWhileCharging(boolean leftClick, CallbackInfo ci) {
        if (leftClick && CombatInputClient.blocksAttack()) ci.cancel();
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void fightClub$noUseWhileCharging(CallbackInfo ci) {
        if (CombatInputClient.isCharging()) ci.cancel();
    }

    @Redirect(method = "handleKeybinds", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;releaseUsingItem(Lnet/minecraft/world/entity/player/Player;)V"))
    private void fightClub$keepSpearCharge(MultiPlayerGameMode gameMode, Player player) {
        if (!CombatInputClient.isCharging()) gameMode.releaseUsingItem(player);
    }
}
