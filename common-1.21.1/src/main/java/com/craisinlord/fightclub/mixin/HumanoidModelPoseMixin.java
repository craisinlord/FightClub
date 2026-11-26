package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.client.CombatThirdPerson;
import com.craisinlord.fightclub.compat.EmfCompat;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class HumanoidModelPoseMixin {
    @Unique
    private boolean fightClub$stabbed;

    @Inject(method = "setupAttackAnimation(Lnet/minecraft/world/entity/LivingEntity;F)V", at = @At("HEAD"), cancellable = true)
    private void fightClub$stab(LivingEntity entity, float ageInTicks, CallbackInfo ci) {
        if (entity instanceof Player player && CombatThirdPerson.stab((HumanoidModel<?>) (Object) this, player)) {
            fightClub$stabbed = true;
            ci.cancel();
        }
    }

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void fightClub$pose(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                     float netHeadYaw, float headPitch, CallbackInfo ci) {
        boolean stabbed = fightClub$stabbed;
        fightClub$stabbed = false;
        if (entity instanceof Player player) {
            HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
            float[] before = EmfCompat.isLoaded() ? EmfCompat.snapshot(model) : null;
            CombatThirdPerson.apply(model, player, limbSwing, limbSwingAmount, ageInTicks);
            if (before != null) EmfCompat.update(player, model, stabbed || EmfCompat.changed(before, model));
        }
    }
}
