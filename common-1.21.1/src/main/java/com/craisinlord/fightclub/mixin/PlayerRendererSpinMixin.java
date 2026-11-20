package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.client.LongswordSpinClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Third-person longsword spin: turns the rendered body once around by adding to the body yaw used
 * for the model's rotation. The player's real rotation and the camera are never touched.
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererSpinMixin {
    // setupRotations(AbstractClientPlayer, PoseStack, float bob, float yBodyRot, float partialTick, float scale)
    @ModifyVariable(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private float fightClub$spin(float yBodyRot, AbstractClientPlayer player, PoseStack poseStack, float bob, float bodyRot,
                                 float partialTick, float scale) {
        return yBodyRot + LongswordSpinClient.bodySpin(player, partialTick);
    }
}
