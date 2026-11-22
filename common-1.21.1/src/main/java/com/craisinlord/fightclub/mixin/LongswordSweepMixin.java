package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.combat.LongswordSpin;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class LongswordSweepMixin {
    @Unique
    private float fightClub$sweepStrength = -1.0F;

    @Inject(method = "attack", at = @At("HEAD"))
    private void fightClub$recordStrength(Entity target, CallbackInfo ci) {
        Player player = (Player) (Object) this;
        fightClub$sweepStrength = player.level() instanceof ServerLevel && LongswordSpin.sweepsArc(player)
                ? player.getAttackStrengthScale(0.5F) : -1.0F;
    }

    @Inject(method = "attack", at = @At("RETURN"))
    private void fightClub$arcSweep(Entity target, CallbackInfo ci) {
        float strength = fightClub$sweepStrength;
        fightClub$sweepStrength = -1.0F;
        if (strength >= 0.0F) LongswordSpin.arcSweep((Player) (Object) this, strength, target);
    }
}
