package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.client.OffhandPlacing;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityDamageEventMixin {
    @Inject(method = "handleDamageEvent", at = @At("HEAD"))
    private void fightClub$trackCombat(DamageSource source, CallbackInfo ci) {
        OffhandPlacing.onDamage((LivingEntity) (Object) this, source);
    }
}
