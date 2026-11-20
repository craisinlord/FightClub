package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.CombatConfig;
import com.craisinlord.fightclub.client.CombatModels;
import com.craisinlord.fightclub.combat.WeaponKind;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Third-person spear throw wind-up: the arm is raised overhead, which leaves the normal grip
 * pointing the tip backward. Vanilla swaps the trident to a separate "throwing" model for this;
 * we spin the spear's model 180 degrees in its own plane instead, so the tip leads.
 *
 * <p>Only the right-click throw uses the THROW_TRIDENT arm pose; a left-click spear charge shows
 * vanilla's SPEAR pose (see AvatarRendererArmPoseMixin), so it is never flipped.
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
    private static final String SUBMIT = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V";

    @Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = SUBMIT))
    private void fightClub$beforeItem(ArmedEntityRenderState state, ItemStackRenderState item, ItemStack stack, HumanoidArm arm,
                                      PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        // Held poses and the longsword swing / spin can turn the item in the hand: roll twists it
        // around the arm's length (the 1.8-style sword block holds the blade flat across the body).
        org.joml.Vector3f turn = com.craisinlord.fightclub.client.CombatThirdPerson.itemRotation(state, arm);
        if (turn != null) {
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(turn.z()));
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(turn.x()));
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(turn.y()));
        }
        if (WeaponKind.of(stack) != WeaponKind.SPEAR || !CombatConfig.enabled(CombatConfig.ANIMATIONS)) return;
        HumanoidModel.ArmPose pose = arm == HumanoidArm.RIGHT ? state.rightArmPose : state.leftArmPose;
        if (pose == HumanoidModel.ArmPose.THROW_TRIDENT) CombatModels.setModelSpin(180.0F);
    }

    @Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = SUBMIT, shift = At.Shift.AFTER))
    private void fightClub$afterItem(ArmedEntityRenderState state, ItemStackRenderState item, ItemStack stack, HumanoidArm arm,
                                     PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        CombatModels.setModelSpin(0.0F);
    }
}
