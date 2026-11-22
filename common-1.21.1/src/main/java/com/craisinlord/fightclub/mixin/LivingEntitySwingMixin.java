package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.client.CombatPose;
import com.craisinlord.fightclub.client.CombatPoseConfig;
import com.craisinlord.fightclub.combat.WeaponKind;
import com.craisinlord.fightclub.compat.BetterCombatCompat;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public abstract class LivingEntitySwingMixin {
    @ModifyReturnValue(method = "getCurrentSwingDuration", at = @At("RETURN"))
    private int fightClub$weaponSwingDuration(int duration) {
        if (!((Object) this instanceof Player player) || !player.level().isClientSide || BetterCombatCompat.isLoaded()
                || !com.craisinlord.fightclub.CombatConfig.enabled(com.craisinlord.fightclub.CombatConfig.ANIMATIONS)) {
            return duration;
        }
        CombatPose style = switch (WeaponKind.of(player.getMainHandItem())) {
            case DAGGER -> CombatPose.JAB_DAGGER;
            case SPEAR, TRIDENT -> CombatPose.JAB_SPEAR;
            case LONGSWORD -> CombatPose.HEAVY_LONGSWORD;
            default -> null;
        };
        if (style == null) return duration;
        return Math.max(2, Math.round(duration * CombatPoseConfig.get(style).blendIn() / 6.0F));
    }
}
