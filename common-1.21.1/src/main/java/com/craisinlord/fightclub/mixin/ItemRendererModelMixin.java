package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.client.CombatModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererModelMixin {
    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true)
    private BakedModel fightClub$inHandModel(BakedModel model, ItemStack stack, ItemDisplayContext context,
                                                  boolean leftHand, PoseStack poseStack, MultiBufferSource buffer,
                                                  int light, int overlay, BakedModel original) {
        if (stack.isEmpty()) return model;
        if (CombatModels.usesIcon(context)) {
            BakedModel icon = CombatModels.icon(stack);
            return icon != null ? icon : model;
        }
        BakedModel inHand = CombatModels.inHand(stack);
        return inHand != null ? inHand : model;
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 0))
    private void fightClub$modelSpin(ItemStack stack, ItemDisplayContext context, boolean leftHand, PoseStack poseStack,
                                     MultiBufferSource buffer, int light, int overlay, BakedModel model, CallbackInfo ci) {
        float roll = CombatModels.modelRoll();
        if (roll != 0.0F) {
            float px = CombatModels.rollPivotX(), pz = CombatModels.rollPivotZ();
            poseStack.translate(px, 0.0F, pz);
            poseStack.mulPose(Axis.YP.rotationDegrees(roll));
            poseStack.translate(-px, 0.0F, -pz);
        }
        float spin = CombatModels.modelSpin();
        if (spin != 0.0F) poseStack.mulPose(Axis.ZP.rotationDegrees(spin));
    }
}
