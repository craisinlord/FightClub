package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.combat.CombatAttacks;
import com.craisinlord.fightclub.combat.CombatVanillaRules;
import com.craisinlord.fightclub.combat.WeaponSpec;
import com.craisinlord.fightclub.combat.WeaponSpecs;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The mace's normal attack is a heavy sweep (design doc); a falling hit stays vanilla's smash. */
@Mixin(MaceItem.class)
public abstract class MaceSweepMixin {
    @Inject(method = "hurtEnemy", at = @At("HEAD"))
    private void fightClub$sweep(ItemStack stack, LivingEntity target, LivingEntity attacker, CallbackInfoReturnable<Boolean> cir) {
        if (attacker.level().isClientSide || !(attacker instanceof Player player)) return;
        // Every landed hit counts toward the hit streak (falling smashes too).
        com.craisinlord.fightclub.combat.MaceHitStreak.onHit(player);
        // A falling hit is vanilla's mace smash, which already knocks back everything nearby.
        if (MaceItem.canSmashAttack(player)) return;
        WeaponSpec spec = WeaponSpecs.getOrLoadBuiltin("base", "mace");
        if (spec != null) CombatAttacks.sweep(player, target, spec);
    }
}
