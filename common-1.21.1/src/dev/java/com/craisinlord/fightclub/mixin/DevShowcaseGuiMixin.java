package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.client.DevShowcase;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class DevShowcaseGuiMixin {
    @Shadow protected abstract void renderCrosshair(GuiGraphics graphics, DeltaTracker deltaTracker);

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void fightClub$showcaseHud(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (DevShowcase.hud == 0) return;
        if (DevShowcase.hud == 2) renderCrosshair(graphics, deltaTracker);
        ci.cancel();
    }
}
