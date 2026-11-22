package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.combat.MobWeapons;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Zombie.class)
public abstract class ZombieWeaponsMixin {
    @Inject(method = "populateDefaultEquipmentSlots", at = @At("TAIL"), require = 0)
    private void fightClub$longsword(RandomSource random, DifficultyInstance difficulty, CallbackInfo ci) {
        MobWeapons.equipZombie((Mob) (Object) this, random, difficulty);
    }
}
