package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.combat.CombatRecovery;
import com.craisinlord.fightclub.combat.CombatChargeState;
import com.craisinlord.fightclub.combat.SpearProjectile;
import com.craisinlord.fightclub.combat.WeaponSpec;
import com.craisinlord.fightclub.combat.WeaponSpecs;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TridentItem.class)
public abstract class TridentItemMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void fightClub$use(Level level, Player player, InteractionHand hand,
                                    CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if (CombatRecovery.active(player)) {
            cir.setReturnValue(InteractionResultHolder.fail(player.getItemInHand(hand)));
        }
    }

    @Inject(method = "hurtEnemy", at = @At("HEAD"))
    private void fightClub$pierce(ItemStack stack, LivingEntity target, LivingEntity attacker,
                                       CallbackInfoReturnable<Boolean> cir) {
        if (attacker.level().isClientSide || !(attacker instanceof Player player) || CombatRecovery.active(player)) return;
        WeaponSpec spec = WeaponSpecs.getOrLoadBuiltin("base", "trident");
        if (spec != null && spec.attackStyle() == WeaponSpec.AttackStyle.THRUST) {
            com.craisinlord.fightclub.combat.CombatAttacks.pierce(player, target, spec);
        }
    }

    @Inject(method = "releaseUsing", at = @At("HEAD"), cancellable = true)
    private void fightClub$releaseUsing(ItemStack stack, Level level, LivingEntity user, int timeLeft,
                                              CallbackInfo ci) {
        if (!level.isClientSide && user instanceof Player player && CombatRecovery.active(player)) ci.cancel();
    }
}
