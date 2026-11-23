package com.craisinlord.fightclub.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface AttackTickerAccessor {
    @Accessor("attackStrengthTicker")
    int fightClub$getAttackStrengthTicker();

    @Accessor("attackStrengthTicker")
    void fightClub$setAttackStrengthTicker(int ticks);
}
