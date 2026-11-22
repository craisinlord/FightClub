package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.compat.CompatAbilities;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityCompatDamageMixin {
    @ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true)
    private float fightClub$compatIncomingDamage(float amount, DamageSource source) {
        return CompatAbilities.incomingDamage((LivingEntity) (Object) this, source, amount);
    }
}
