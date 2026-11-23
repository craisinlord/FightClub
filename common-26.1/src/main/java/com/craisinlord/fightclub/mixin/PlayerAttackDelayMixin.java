package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.combat.DelayedAttacks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Player.class, priority = 900)
public abstract class PlayerAttackDelayMixin {
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void fightClub$delayImpact(Entity target, CallbackInfo ci) {
        if (DelayedAttacks.defer((Player) (Object) this, target)) ci.cancel();
    }

    @Inject(method = "resetAttackStrengthTicker", at = @At("HEAD"))
    private void fightClub$rememberStrength(CallbackInfo ci) {
        com.craisinlord.fightclub.combat.LongswordSpin.recordReset((Player) (Object) this);
    }
}
