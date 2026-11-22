package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.CombatConfig;
import com.craisinlord.fightclub.combat.CombatRecovery;
import com.craisinlord.fightclub.combat.CombatVanillaRules;
import com.craisinlord.fightclub.combat.CombatWeaponItem;
import com.craisinlord.fightclub.combat.CombatWeapons;
import com.craisinlord.fightclub.combat.ExternalWeapons;
import com.craisinlord.fightclub.combat.LongswordSpin;
import com.craisinlord.fightclub.combat.WeaponSpec;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ItemStackWeaponItemsMixin {
    @Unique
    private static boolean fightClub$ownsUse(ItemStack stack) {
        ExternalWeapons.Entry external = CombatWeapons.external(stack.getItem());
        if (external == null || !external.rightClick()) return false;
        return switch (external.weapon()) {
            case "longsword" -> LongswordSpin.hasSpin(stack);
            case "sword" -> {
                WeaponSpec spec = CombatVanillaRules.swordSpec(stack);
                yield CombatConfig.enabled(CombatConfig.SWORD_BLOCKING) && spec != null && spec.blocking();
            }
            default -> CombatWeaponItem.canThrow(external.material(), external.weapon());
        };
    }

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void fightClub$mappedUse(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        if (!fightClub$ownsUse(stack)) return;
        ExternalWeapons.Entry external = CombatWeapons.external(stack.getItem());
        if (external.weapon().equals("sword")) {
            if (hand == InteractionHand.MAIN_HAND && CombatWeapons.isShield(player.getOffhandItem())) {
                cir.setReturnValue(InteractionResult.PASS);
            } else if (CombatRecovery.active(player)) {
                cir.setReturnValue(InteractionResult.FAIL);
            } else {
                player.startUsingItem(hand);
                cir.setReturnValue(InteractionResult.CONSUME);
            }
            return;
        }
        InteractionResult result = CombatWeaponItem.use(external.material(), external.weapon(), player, hand);
        if (result != null) cir.setReturnValue(result);
    }

    @Inject(method = "getUseDuration", at = @At("HEAD"), cancellable = true)
    private void fightClub$mappedUseDuration(LivingEntity user, CallbackInfoReturnable<Integer> cir) {
        if (fightClub$ownsUse((ItemStack) (Object) this)) cir.setReturnValue(CombatWeaponItem.USE_DURATION);
    }

    @Inject(method = "getUseAnimation", at = @At("HEAD"), cancellable = true)
    private void fightClub$mappedUseAnimation(CallbackInfoReturnable<ItemUseAnimation> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        if (!fightClub$ownsUse(stack)) return;
        cir.setReturnValue(switch (CombatWeapons.weapon(stack.getItem())) {
            case "longsword" -> ItemUseAnimation.NONE;
            case "sword" -> ItemUseAnimation.BLOCK;
            default -> ItemUseAnimation.TRIDENT;
        });
    }

    @Inject(method = "releaseUsing", at = @At("HEAD"), cancellable = true)
    private void fightClub$mappedRelease(Level level, LivingEntity user, int timeLeft, CallbackInfo ci) {
        ItemStack stack = (ItemStack) (Object) this;
        if (!(user instanceof Player) || !fightClub$ownsUse(stack)) return;
        ExternalWeapons.Entry external = CombatWeapons.external(stack.getItem());
        if (!external.weapon().equals("sword")) {
            CombatWeaponItem.releaseUsing(external.material(), external.weapon(), stack, level, user, timeLeft);
        }
        ci.cancel();
    }

    @Inject(method = "onUseTick", at = @At("HEAD"), cancellable = true)
    private void fightClub$mappedUseTick(Level level, LivingEntity user, int remaining, CallbackInfo ci) {
        if (user instanceof Player && fightClub$ownsUse((ItemStack) (Object) this)) ci.cancel();
    }

    @Inject(method = "hurtEnemy", at = @At("HEAD"), cancellable = true)
    private void fightClub$mappedRecovery(LivingEntity target, LivingEntity attacker, CallbackInfoReturnable<Boolean> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        if (attacker instanceof Player player && CombatWeapons.external(stack.getItem()) != null && CombatRecovery.active(player)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "hurtEnemy", at = @At("RETURN"))
    private void fightClub$mappedHit(LivingEntity target, LivingEntity attacker, CallbackInfoReturnable<Boolean> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        ExternalWeapons.Entry external = CombatWeapons.external(stack.getItem());
        if (external != null) CombatWeaponItem.afterHit(external.material(), external.weapon(), target, attacker);
    }
}
