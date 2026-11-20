package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.combat.CombatVanillaRules;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Spears and tridents crit only on charged hits (spear_charge_crits).
 *
 * <p>Normal jump crits: Player.attack asks canCriticalAttack, which is false for them here, so no
 * 1.5x and no crit effects (on NeoForge the CriticalHitEvent then sees a non-crit).
 *
 * <p>Spear charge: vanilla's kinetic lance hits go through stabAttack, which never crits and calls
 * attackVisualEffects with isCritical = false. While a left-click spear charge runs, that flag is
 * set, so each charge hit plays vanilla's crit sound and particles. Only the effects: stabAttack
 * computes its damage before this call, so the kinetic damage is unchanged.
 */
@Mixin(Player.class)
public abstract class PlayerCritMixin {
    @Inject(method = "canCriticalAttack", at = @At("HEAD"), cancellable = true)
    private void fightClub$chargeOnlyCrits(Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (CombatVanillaRules.suppressesCrits((Player) (Object) this)) cir.setReturnValue(false);
    }

    @ModifyArg(method = "stabAttack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;attackVisualEffects(Lnet/minecraft/world/entity/Entity;ZZZZF)V"), index = 1)
    private boolean fightClub$spearChargeCrit(boolean critical) {
        return critical || CombatVanillaRules.spearChargeCrits((Player) (Object) this);
    }
}
