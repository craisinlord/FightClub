package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.combat.CombatRecovery;
import com.craisinlord.fightclub.combat.CombatWeaponItem;
import com.craisinlord.fightclub.combat.CombatWeapons;
import com.craisinlord.fightclub.combat.ExternalWeapons;
import com.craisinlord.fightclub.combat.LongswordSpin;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ItemStackBackportSpearMixin {
    private static boolean fightClub$ownsUse(ItemStack stack) {
        ExternalWeapons.Entry external = CombatWeapons.external(stack.getItem());
        if (external == null || !external.rightClick()) return false;
        if (external.weapon().equals("longsword")) return LongswordSpin.hasSpin(stack);
        return CombatWeaponItem.canThrow(external.material(), external.weapon());
    }

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void fightClub$backportSpearUse(Level level, Player player, InteractionHand hand,
                                            CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        String material = CombatWeapons.backportSpearMaterial(stack.getItem());
        if (material != null) {
            CombatWeapons.stripMobComponents(stack);
            InteractionResultHolder<ItemStack> result = CombatWeaponItem.use(material, "spear", player, hand);
            cir.setReturnValue(result != null ? result : InteractionResultHolder.pass(stack));
            return;
        }
        if (!fightClub$ownsUse(stack)) return;
        ExternalWeapons.Entry external = CombatWeapons.external(stack.getItem());
        InteractionResultHolder<ItemStack> result = CombatWeaponItem.use(external.material(), external.weapon(), player, hand);
        if (result != null) cir.setReturnValue(result);
    }

    @Inject(method = "getUseDuration", at = @At("HEAD"), cancellable = true)
    private void fightClub$backportSpearUseDuration(LivingEntity user, CallbackInfoReturnable<Integer> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        if (CombatWeapons.isPlayerBackportSpear(stack)) {
            cir.setReturnValue(CombatWeaponItem.canThrow(CombatWeapons.material(stack.getItem()), "spear") ? 72000 : 0);
        } else if (fightClub$ownsUse(stack)) {
            cir.setReturnValue(72000);
        }
    }

    @Inject(method = "getUseAnimation", at = @At("HEAD"), cancellable = true)
    private void fightClub$backportSpearUseAnimation(CallbackInfoReturnable<UseAnim> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        if (CombatWeapons.isPlayerBackportSpear(stack)) {
            cir.setReturnValue(CombatWeaponItem.canThrow(CombatWeapons.material(stack.getItem()), "spear") ? UseAnim.SPEAR : UseAnim.NONE);
        } else if (fightClub$ownsUse(stack)) {
            cir.setReturnValue(CombatWeapons.is(stack.getItem(), "longsword") ? UseAnim.NONE : UseAnim.SPEAR);
        }
    }

    @Inject(method = "releaseUsing", at = @At("HEAD"), cancellable = true)
    private void fightClub$backportSpearRelease(Level level, LivingEntity user, int timeLeft, CallbackInfo ci) {
        ItemStack stack = (ItemStack) (Object) this;
        if (!(user instanceof Player)) return;
        if (CombatWeapons.isPlayerBackportSpear(stack)) {
            CombatWeaponItem.releaseUsing(CombatWeapons.material(stack.getItem()), "spear", stack, level, user, timeLeft);
            ci.cancel();
        } else if (fightClub$ownsUse(stack)) {
            ExternalWeapons.Entry external = CombatWeapons.external(stack.getItem());
            CombatWeaponItem.releaseUsing(external.material(), external.weapon(), stack, level, user, timeLeft);
            ci.cancel();
        }
    }

    @Inject(method = "onUseTick", at = @At("HEAD"), cancellable = true)
    private void fightClub$backportSpearUseTick(Level level, LivingEntity user, int remaining, CallbackInfo ci) {
        if (!(user instanceof Player)) return;
        ItemStack stack = (ItemStack) (Object) this;
        if (CombatWeapons.backportSpearMaterial(stack.getItem()) != null || fightClub$ownsUse(stack)) ci.cancel();
    }

    @Inject(method = "hurtEnemy", at = @At("HEAD"), cancellable = true)
    private void fightClub$backportSpearRecovery(LivingEntity target, Player attacker, CallbackInfoReturnable<Boolean> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        if (CombatWeapons.isTrident(stack.getItem()) && !attacker.level().isClientSide && !CombatRecovery.active(attacker)) {
            com.craisinlord.fightclub.combat.WeaponSpec spec = com.craisinlord.fightclub.combat.WeaponSpecs.getOrLoadBuiltin("base", "trident");
            if (spec != null && spec.attackStyle() == com.craisinlord.fightclub.combat.WeaponSpec.AttackStyle.THRUST) {
                com.craisinlord.fightclub.combat.CombatAttacks.pierce(attacker, target, spec);
            }
        }
        if ((CombatWeapons.backportSpearMaterial(stack.getItem()) != null || CombatWeapons.external(stack.getItem()) != null)
                && CombatRecovery.active(attacker)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "hurtEnemy", at = @At("RETURN"))
    private void fightClub$backportSpearHit(LivingEntity target, Player attacker, CallbackInfoReturnable<Boolean> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        String material = CombatWeapons.backportSpearMaterial(stack.getItem());
        if (material != null) {
            if (cir.getReturnValueZ()) CombatWeaponItem.afterHit(material, "spear", target, attacker);
            return;
        }
        ExternalWeapons.Entry external = CombatWeapons.external(stack.getItem());
        if (external != null) CombatWeaponItem.afterHit(external.material(), external.weapon(), target, attacker);
    }
}
