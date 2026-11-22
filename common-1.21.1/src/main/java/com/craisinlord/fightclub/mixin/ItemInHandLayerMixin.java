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

@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
    @Inject(method = "renderArmWithItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void fightClub$beforeItem(LivingEntity entity, ItemStack stack, ItemDisplayContext context, HumanoidArm arm,
                                      PoseStack poseStack, MultiBufferSource buffer, int light, CallbackInfo ci) {
        com.craisinlord.fightclub.client.CombatThirdPerson.ItemTurn turn = com.craisinlord.fightclub.client.CombatThirdPerson.itemTurn(entity, arm,
                Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true));
        if (turn != null) {
            if (turn.atFist()) poseStack.rotateAround(turn.rotation(), 0.0F, com.craisinlord.fightclub.client.CombatThirdPerson.FIST_PIVOT, 0.0F);
            else poseStack.mulPose(turn.rotation());
        }
        if (!(entity instanceof Player player) || !CombatConfig.enabled(CombatConfig.ANIMATIONS)) return;
        WeaponKind kind = WeaponKind.of(stack);
        if (kind == WeaponKind.DAGGER && player.isUsingItem() && player.getUseItem() == stack
                && stack.getUseAnimation() == UseAnim.SPEAR) {
            float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
            float timeHeld = Math.max(0.0F, stack.getUseDuration(player) - (player.getUseItemRemainingTicks() - partialTick + 1.0F));
            com.craisinlord.fightclub.client.CombatPoseConfig.Pose flip =
                    com.craisinlord.fightclub.client.CombatPoseConfig.get(com.craisinlord.fightclub.client.CombatPose.DAGGER_THROW_FLIP);
            float spin = com.craisinlord.fightclub.client.CombatPoseConfig.flip(flip, timeHeld / flip.blendIn());
            if (spin != 0.0F) CombatModels.setModelSpin(spin);
            return;
        }
        if (kind != WeaponKind.SPEAR) return;
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
