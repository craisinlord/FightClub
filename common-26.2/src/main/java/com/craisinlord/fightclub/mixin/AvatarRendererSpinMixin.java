package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.client.LongswordSpinClient;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.SwingAnimationType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererSpinMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL"))
    private void fightClub$spin(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
        float spin = LongswordSpinClient.bodySpin(state);
        if (spin != 0.0F) state.bodyRot += spin;
        if (state.attackArm == state.mainArm && entity instanceof Player player && LongswordSpinClient.tridentStab(player)) {
            state.swingAnimationType = SwingAnimationType.STAB;
        }
    }
}
