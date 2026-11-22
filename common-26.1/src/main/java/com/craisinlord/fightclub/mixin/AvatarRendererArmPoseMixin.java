package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.CombatConfig;
import com.craisinlord.fightclub.client.CombatClientPoses;
import com.craisinlord.fightclub.combat.CombatChargeState;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererArmPoseMixin {
    @Redirect(method = "getArmPose(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getUseAnimation()Lnet/minecraft/world/item/ItemUseAnimation;"))
    private static ItemUseAnimation fightClub$spearChargePose(ItemStack stack, Avatar avatar, ItemStack itemInHand, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND && avatar instanceof Player player && CombatConfig.enabled(CombatConfig.ANIMATIONS)
                && CombatChargeState.usesItemForCharge(stack.getItem()) && CombatClientPoses.isCharging(player)) {
            return ItemUseAnimation.SPEAR;
        }
        return stack.getUseAnimation();
    }
}
