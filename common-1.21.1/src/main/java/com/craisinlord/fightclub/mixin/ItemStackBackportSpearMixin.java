package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.combat.CombatRecovery;
import com.craisinlord.fightclub.combat.CombatWeaponItem;
import com.craisinlord.fightclub.combat.CombatWeapons;
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
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void fightClub$backportSpearUse(Level level, Player player, InteractionHand hand,
                                            CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        String material = CombatWeapons.backportSpearMaterial(stack.getItem());
        if (material == null) return;
        CombatWeapons.stripMobComponents(stack);
        InteractionResultHolder<ItemStack> result = CombatWeaponItem.use(material, "spear", player, hand);
        cir.setReturnValue(result != null ? result : InteractionResultHolder.pass(stack));
    }

    @Inject(method = "getUseDuration", at = @At("HEAD"), cancellable = true)
    private void fightClub$backportSpearUseDuration(LivingEntity user, CallbackInfoReturnable<Integer> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        if (!CombatWeapons.isPlayerBackportSpear(stack)) return;
        cir.setReturnValue(CombatWeaponItem.canThrow(CombatWeapons.material(stack.getItem()), "spear") ? 72000 : 0);
    }

    @Inject(method = "getUseAnimation", at = @At("HEAD"), cancellable = true)
    private void fightClub$backportSpearUseAnimation(CallbackInfoReturnable<UseAnim> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        if (!CombatWeapons.isPlayerBackportSpear(stack)) return;
        cir.setReturnValue(CombatWeaponItem.canThrow(CombatWeapons.material(stack.getItem()), "spear") ? UseAnim.SPEAR : UseAnim.NONE);
    }

    @Inject(method = "releaseUsing", at = @At("HEAD"), cancellable = true)
    private void fightClub$backportSpearRelease(Level level, LivingEntity user, int timeLeft, CallbackInfo ci) {
        ItemStack stack = (ItemStack) (Object) this;
        if (!(user instanceof Player) || !CombatWeapons.isPlayerBackportSpear(stack)) return;
        CombatWeaponItem.releaseUsing(CombatWeapons.material(stack.getItem()), "spear", stack, level, user, timeLeft);
        ci.cancel();
    }

    @Inject(method = "onUseTick", at = @At("HEAD"), cancellable = true)
    private void fightClub$backportSpearUseTick(Level level, LivingEntity user, int remaining, CallbackInfo ci) {
        if (user instanceof Player && CombatWeapons.backportSpearMaterial(((ItemStack) (Object) this).getItem()) != null) ci.cancel();
    }

    @Inject(method = "hurtEnemy", at = @At("HEAD"), cancellable = true)
    private void fightClub$backportSpearRecovery(LivingEntity target, Player attacker, CallbackInfoReturnable<Boolean> cir) {
        if (CombatWeapons.backportSpearMaterial(((ItemStack) (Object) this).getItem()) != null && CombatRecovery.active(attacker)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "hurtEnemy", at = @At("RETURN"))
    private void fightClub$backportSpearHit(LivingEntity target, Player attacker, CallbackInfoReturnable<Boolean> cir) {
        String material = CombatWeapons.backportSpearMaterial(((ItemStack) (Object) this).getItem());
        if (material != null && cir.getReturnValueZ()) CombatWeaponItem.afterHit(material, "spear", target, attacker);
    }
}
