package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.CombatConfig;
import com.craisinlord.fightclub.combat.WeaponKind;
import com.craisinlord.fightclub.compat.BetterCombatCompat;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FirstPersonHandsAndItems.class)
public abstract class FirstPersonHandsSwapMixin {
    @ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;getItemSwapScale(F)F"))
    private float fightClub$steadyThrust(float scale, LocalPlayer player) {
        if (BetterCombatCompat.isLoaded() || !CombatConfig.enabled(CombatConfig.ANIMATIONS)) return scale;
        return WeaponKind.of(player.getMainHandItem()).thrusts() ? 1.0F : scale;
    }
}
