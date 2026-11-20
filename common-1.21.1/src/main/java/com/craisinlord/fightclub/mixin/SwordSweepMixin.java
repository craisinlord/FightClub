package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.combat.CombatAttacks;
import com.craisinlord.fightclub.combat.CombatVanillaRules;
import com.craisinlord.fightclub.combat.CombatWeaponItem;
import com.craisinlord.fightclub.combat.WeaponSpec;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Data-driven sweep for vanilla swords (Fight Club weapons sweep in CombatWeaponItem). */
@Mixin(SwordItem.class)
public abstract class SwordSweepMixin {
    @Inject(method = "hurtEnemy", at = @At("HEAD"))
    private void fightClub$sweep(ItemStack stack, LivingEntity target, LivingEntity attacker, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof CombatWeaponItem || attacker.level().isClientSide || !(attacker instanceof Player player)) return;
        WeaponSpec spec = CombatVanillaRules.swordSpec(stack);
        if (spec != null && spec.attackStyle() == WeaponSpec.AttackStyle.SWEEP) CombatAttacks.sweep(player, target, spec);
    }
}
