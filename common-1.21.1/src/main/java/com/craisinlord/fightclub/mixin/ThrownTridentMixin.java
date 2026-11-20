package com.craisinlord.fightclub.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownTrident;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Only player-thrown tridents play the Loyalty return sound (design doc: not ones thrown by Drowned). */
@Mixin(ThrownTrident.class)
public abstract class ThrownTridentMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/projectile/ThrownTrident;playSound(Lnet/minecraft/sounds/SoundEvent;FF)V"))
    private void fightClub$returnSoundForPlayersOnly(ThrownTrident trident, SoundEvent sound, float volume, float pitch, Operation<Void> original) {
        if (trident.getOwner() instanceof Player) original.call(trident, sound, volume, pitch);
    }
}
