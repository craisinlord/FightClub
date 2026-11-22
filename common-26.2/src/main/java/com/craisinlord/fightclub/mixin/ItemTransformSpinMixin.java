package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.client.CombatModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemTransform.class)
public abstract class ItemTransformSpinMixin {
    @Inject(method = "apply", at = {
            @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack$Pose;translate(FFF)Lorg/joml/Matrix4f;", ordinal = 0),
            @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack$Pose;translate(FFF)Lorg/joml/Matrix4f;", ordinal = 2)
    })
    private void fightClub$modelSpin(boolean applyLeftHandFix, PoseStack.Pose pose, CallbackInfo ci) {
        float roll = CombatModels.modelRoll();
        if (roll != 0.0F) {
            float px = CombatModels.rollPivotX(), pz = CombatModels.rollPivotZ();
            pose.translate(px, 0.0F, pz);
            pose.rotate(Axis.YP.rotationDegrees(roll));
            pose.translate(-px, 0.0F, -pz);
        }
        float spin = CombatModels.modelSpin();
        if (spin != 0.0F) pose.rotate(Axis.ZP.rotationDegrees(spin));
    }
}
