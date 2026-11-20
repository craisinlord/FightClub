package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.combat.MaceHitStreak;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Counts landed mace hits for the hit streak (see MaceHitStreak). Runs on every hit, with or without Better Combat. */
@Mixin(MaceItem.class)
public abstract class MaceHitStreakMixin {
    @Inject(method = "hurtEnemy", at = @At("HEAD"))
    private void fightClub$hitStreak(ItemStack stack, LivingEntity target, LivingEntity attacker, CallbackInfo ci) {
        if (attacker instanceof Player player && !player.level().isClientSide()) MaceHitStreak.onHit(player);
    }
}
