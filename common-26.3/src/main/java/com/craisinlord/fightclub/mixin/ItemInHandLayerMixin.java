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

@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
    private static final String SUBMIT = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V";

    @Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = SUBMIT))
    private void fightClub$beforeItem(ArmedEntityRenderState state, ItemStackRenderState item, ItemStack stack, HumanoidArm arm,
                                      PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        com.craisinlord.fightclub.client.CombatThirdPerson.ItemTurn turn = com.craisinlord.fightclub.client.CombatThirdPerson.itemTurn(state, arm);
        if (turn != null) {
            if (turn.atFist()) poseStack.rotateAround(turn.rotation(), 0.0F, com.craisinlord.fightclub.client.CombatThirdPerson.FIST_PIVOT, 0.0F);
            else poseStack.rotate(turn.rotation());
        }
        if (!CombatConfig.enabled(CombatConfig.ANIMATIONS)) return;
        HumanoidModel.ArmPose pose = arm == HumanoidArm.RIGHT ? state.rightArmPose : state.leftArmPose;
        WeaponKind kind = WeaponKind.of(stack);
        if (kind == WeaponKind.DAGGER && pose == HumanoidModel.ArmPose.THROW_TRIDENT) {
            com.craisinlord.fightclub.client.CombatPoseConfig.Pose flip =
                    com.craisinlord.fightclub.client.CombatPoseConfig.get(com.craisinlord.fightclub.client.CombatPose.DAGGER_THROW_FLIP);
            float spin = com.craisinlord.fightclub.client.CombatPoseConfig.flip(flip, state.ticksUsingItem(arm) / flip.blendIn());
            if (spin != 0.0F) CombatModels.setModelSpin(spin);
            return;
        }
        if (kind != WeaponKind.SPEAR) return;
        if (pose == HumanoidModel.ArmPose.THROW_TRIDENT) CombatModels.setModelSpin(180.0F);
    }

    @Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = SUBMIT, shift = At.Shift.AFTER))
    private void fightClub$afterItem(ArmedEntityRenderState state, ItemStackRenderState item, ItemStack stack, HumanoidArm arm,
                                     PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        CombatModels.setModelSpin(0.0F);
    }
}
