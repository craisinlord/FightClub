package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.combat.KnifeThrowGoal;
import com.craisinlord.fightclub.combat.MobWeapons;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractSkeleton.class)
public abstract class SkeletonKnifeMixin extends Monster {
    protected SkeletonKnifeMixin(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    @Inject(method = "registerGoals", at = @At("TAIL"), require = 0)
    private void fightClub$knifeGoal(CallbackInfo ci) {
        try {
            if (MobWeapons.isKnifeSkeleton(this)) goalSelector.addGoal(3, new KnifeThrowGoal(this));
        } catch (RuntimeException e) {
            MobWeapons.warn(e);
        }
    }

    @Inject(method = "finalizeSpawn", at = @At("RETURN"), require = 0)
    private void fightClub$knife(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, SpawnGroupData data, CallbackInfoReturnable<SpawnGroupData> cir) {
        MobWeapons.equipSkeleton(this, getRandom());
    }
}
