package com.craisinlord.fightclub.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A running spear charge strikes each mob it runs through at full strength, not a reset swing. */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("attackStrengthTicker")
    void fightClub$setAttackStrengthTicker(int ticks);
}
