package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.client.CombatThirdPerson;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class HumanoidModelPoseMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void fightClub$pose(HumanoidRenderState state, CallbackInfo ci) {
        if (state instanceof AvatarRenderState avatar) {
            CombatThirdPerson.apply((HumanoidModel<?>) (Object) this, avatar);
        }
    }
}
