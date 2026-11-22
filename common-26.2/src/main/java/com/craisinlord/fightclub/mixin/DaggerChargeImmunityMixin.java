package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.combat.CombatChargeState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class DaggerChargeImmunityMixin {
    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void fightClub$daggerChargeImmunity(ServerLevel level, DamageSource source, float amount,
                                                CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player player && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                && CombatChargeState.isDaggerCharging(player)) {
            cir.setReturnValue(false);
        }
    }
}
