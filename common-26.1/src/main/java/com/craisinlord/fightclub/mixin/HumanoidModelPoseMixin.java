package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.client.CombatThirdPerson;
import com.craisinlord.fightclub.compat.EmfCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class HumanoidModelPoseMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void fightClub$pose(HumanoidRenderState state, CallbackInfo ci) {
        if (state instanceof AvatarRenderState avatar) {
            HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
            float[] before = EmfCompat.isLoaded() ? EmfCompat.snapshot(model) : null;
            CombatThirdPerson.apply(model, avatar);
            if (before != null) {
                Minecraft client = Minecraft.getInstance();
                if (client.level != null && client.level.getEntity(avatar.id) instanceof Player player) {
                    EmfCompat.update(player, model, EmfCompat.changed(before, model));
                }
            }
        }
    }
}
