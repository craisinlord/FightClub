package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.CombatConfig;
import com.craisinlord.fightclub.client.CombatInputClient;
import com.craisinlord.fightclub.client.PounceClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class PounceFovMixin {
    @Unique
    private static final float READY = 0.92F;
    @Unique
    private static final float DASH = 1.12F;
    @Unique
    private static final float IMPACT = 0.95F;

    @Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
    private void fightClub$pounceFov(boolean firstPerson, float fovEffectScale, CallbackInfoReturnable<Float> cir) {
        Minecraft client = Minecraft.getInstance();
        if ((Object) this != client.player || !CombatConfig.enabled(CombatConfig.POUNCE_FOV)) return;
        float target;
        if (PounceClient.active()) target = DASH;
        else if (PounceClient.impactTicks() > 0) target = IMPACT;
        else if (CombatInputClient.pounceReady(client)) target = READY;
        else return;
        cir.setReturnValue(cir.getReturnValueF() * Mth.lerp(fovEffectScale, 1.0F, target));
    }
}
