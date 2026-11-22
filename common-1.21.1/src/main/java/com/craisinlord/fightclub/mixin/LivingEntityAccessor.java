package com.craisinlord.fightclub.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("attackStrengthTicker")
    void fightClub$setAttackStrengthTicker(int ticks);

    @Invoker("getCurrentSwingDuration")
    int fightClub$currentSwingDuration();
}
