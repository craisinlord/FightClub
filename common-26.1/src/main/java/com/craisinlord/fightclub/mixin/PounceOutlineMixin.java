package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.CombatConfig;
import com.craisinlord.fightclub.client.CombatInputClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Outlines the dagger's pounce lock (vanilla's glowing outline, seen only by the pouncing
 * player), so it's clear who a release will hit, or that nothing is locked.
 */
@Mixin(Minecraft.class)
public abstract class PounceOutlineMixin {
    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void fightClub$outlinePounceTarget(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity == CombatInputClient.outlinedTarget() && CombatConfig.enabled(CombatConfig.POUNCE_OUTLINE)) {
            cir.setReturnValue(true);
        }
    }
}
