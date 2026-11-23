package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.combat.MobReach;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class MobLongswordReachMixin {
    @Inject(method = "getAttackBoundingBox", at = @At("RETURN"), cancellable = true)
    private void fightClub$longswordReach(CallbackInfoReturnable<AABB> cir) {
        double bonus = MobReach.longswordBonus((Mob) (Object) this);
        if (bonus > 0.0) cir.setReturnValue(cir.getReturnValue().inflate(bonus, 0.0, bonus));
    }
}
