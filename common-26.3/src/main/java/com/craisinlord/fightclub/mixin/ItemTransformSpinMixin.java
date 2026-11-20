package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.client.CombatModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Model spin (see {@link CombatModels#setModelSpin}): right after the display transform and before
 * the model is shifted by -0.5 to center it, the origin is the model's center, so a Z rotation
 * spins it in its own plane. Ordinal 0 is the final shift of the no-transform branch, ordinal 2
 * the final shift after a real display transform.
 */
@Mixin(ItemTransform.class)
public abstract class ItemTransformSpinMixin {
    @Inject(method = "apply", at = {
            @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack$Pose;translate(FFF)Lorg/joml/Matrix4f;", ordinal = 0),
            @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack$Pose;translate(FFF)Lorg/joml/Matrix4f;", ordinal = 2)
    })
    private void fightClub$modelSpin(boolean applyLeftHandFix, PoseStack.Pose pose, CallbackInfo ci) {
        float spin = CombatModels.modelSpin();
        if (spin != 0.0F) pose.rotate(Axis.ZP.rotationDegrees(spin));
    }
}
