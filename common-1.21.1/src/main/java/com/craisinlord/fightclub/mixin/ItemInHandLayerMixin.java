package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.CombatConfig;
import com.craisinlord.fightclub.client.CombatClientPoses;
import com.craisinlord.fightclub.client.CombatModels;
import com.craisinlord.fightclub.client.SpearChargeAnimation;
import com.craisinlord.fightclub.combat.WeaponKind;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Third-person held-item tweaks for spears:
 * <ul>
 *   <li>Hold-left-click charge: vanilla's newer spear hold-out item motion (levelled and pushed forward).</li>
 *   <li>Throw wind-up: the arm is raised overhead, which leaves the normal grip pointing the tip
 *   backward. Vanilla swaps the trident to a separate "throwing" model for this; we spin the
 *   spear's model 180 degrees in its own plane instead, so the tip leads.</li>
 * </ul>
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
    @Inject(method = "renderArmWithItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void fightClub$beforeItem(LivingEntity entity, ItemStack stack, ItemDisplayContext context, HumanoidArm arm,
                                      PoseStack poseStack, MultiBufferSource buffer, int light, CallbackInfo ci) {
        // Held poses and the longsword swing / spin can turn the item in the hand: roll twists it
        // around the arm's length (the 1.8-style sword block angles the blade outward).
        org.joml.Vector3f turn = com.craisinlord.fightclub.client.CombatThirdPerson.itemRotation(entity, arm,
                Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true));
        if (turn != null) {
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(turn.z()));
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(turn.x()));
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(turn.y()));
        }
        if (!(entity instanceof Player player) || WeaponKind.of(stack) != WeaponKind.SPEAR
                || !CombatConfig.enabled(CombatConfig.ANIMATIONS)) return;
        if (player.isUsingItem() && player.getUseItem() == stack && stack.getUseAnimation() == UseAnim.SPEAR) {
            CombatModels.setModelSpin(180.0F);
            return;
        }
        if (arm == player.getMainArm() && stack == player.getMainHandItem()) {
            float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
            float time = CombatClientPoses.spearChargeTicks(player, partialTick);
            if (time > 0.0F) SpearChargeAnimation.thirdPersonItem(poseStack, player, time, arm == HumanoidArm.RIGHT);
        }
    }

    @Inject(method = "renderArmWithItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            shift = At.Shift.AFTER))
    private void fightClub$afterItem(LivingEntity entity, ItemStack stack, ItemDisplayContext context, HumanoidArm arm,
                                     PoseStack poseStack, MultiBufferSource buffer, int light, CallbackInfo ci) {
        CombatModels.setModelSpin(0.0F);
    }
}
