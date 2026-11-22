package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.client.DevShowcase;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class DevShowcaseCameraMixin {
    @Shadow protected abstract void setPosition(double x, double y, double z);
    @Shadow protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup", at = @At("TAIL"))
    private void fightClub$showcaseCamera(BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partialTick, CallbackInfo ci) {
        if (!DevShowcase.cameraOn || !detached) return;
        setRotation(DevShowcase.camYaw, DevShowcase.camPitch);
        setPosition(DevShowcase.camX, DevShowcase.camY, DevShowcase.camZ);
    }
}
