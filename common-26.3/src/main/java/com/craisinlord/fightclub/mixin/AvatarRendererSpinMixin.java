package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.client.LongswordSpinClient;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.SwingAnimationType;
import net.minecraft.world.item.component.SwingAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Third-person longsword spin, plus the trident's STAB swing. The spin turns the rendered body once around. Only the render state's body
 * yaw changes (the head yaw is stored relative to it, so the head turns along); the player's real
 * rotation and the camera are never touched.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererSpinMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL"))
    private void fightClub$spin(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
        float spin = LongswordSpinClient.bodySpin(state);
        if (spin != 0.0F) state.bodyRot += spin;
        // Trident (without Better Combat): swing with vanilla's spear STAB, arm thrusting forward.
        LivingEntity.SwingDescription swing = state.currentSwing;
        if (swing != null && swing.hand() == InteractionHand.MAIN_HAND && swing.animation().type() != SwingAnimationType.STAB
                && entity instanceof Player player && LongswordSpinClient.tridentStab(player)) {
            state.currentSwing = new LivingEntity.SwingDescription(swing.hand(),
                    new SwingAnimation(SwingAnimationType.STAB, swing.animation().duration()), swing.durationTicks());
        }
    }
}
