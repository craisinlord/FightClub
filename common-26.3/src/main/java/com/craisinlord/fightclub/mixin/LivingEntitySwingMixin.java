package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.CombatConfig;
import com.craisinlord.fightclub.client.CombatPose;
import com.craisinlord.fightclub.client.CombatPoseConfig;
import com.craisinlord.fightclub.combat.WeaponKind;
import com.craisinlord.fightclub.compat.BetterCombatCompat;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Per-weapon arm swing length (vanilla is 6 ticks): a snappier dagger stab, a heavier longsword.
 * Purely visual: it only applies on the client, and never with Better Combat installed. Vanilla
 * 26.x spears keep their own stab timing. Haste and Mining Fatigue still scale it, as in vanilla.
 *
 * <p>This runs for every living entity every tick, so it uses an allocation-free
 * {@code @ModifyReturnValue} and bails out on the cheapest check first. It lives in the common
 * mixin list but only touches client-side players, and CombatPoseConfig has no client-only imports.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntitySwingMixin {
    @ModifyReturnValue(method = "getModifiedSwingDuration", at = @At("RETURN"))
    private int fightClub$weaponSwingDuration(int duration) {
        if (!((Object) this instanceof Player player) || !player.level().isClientSide() || BetterCombatCompat.isLoaded()
                || !CombatConfig.enabled(CombatConfig.ANIMATIONS)) {
            return duration;
        }
        CombatPose style = switch (WeaponKind.of(player.getMainHandItem())) {
            case DAGGER -> CombatPose.JAB_DAGGER;
            case TRIDENT -> CombatPose.JAB_SPEAR;
            case LONGSWORD -> CombatPose.HEAVY_LONGSWORD;
            default -> null;
        };
        if (style == null) return duration;
        return Math.max(2, Math.round(duration * CombatPoseConfig.get(style).blendIn() / 6.0F));
    }
}
