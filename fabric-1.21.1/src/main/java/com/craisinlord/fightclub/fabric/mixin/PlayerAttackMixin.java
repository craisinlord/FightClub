package com.craisinlord.fightclub.fabric.mixin;

import com.craisinlord.fightclub.combat.CombatVanillaRules;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.SwordItem;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Fabric-only attack rules. NeoForge does the same thing with CriticalHitEvent and
 * SweepAttackEvent, and patches the instanceof check below out of Player.attack.
 */
@Mixin(Player.class)
public abstract class PlayerAttackMixin {
    // Vanilla reads fallDistance once in attack(), for the critical-hit check.
    @Redirect(method = "attack", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/entity/player/Player;fallDistance:F", opcode = Opcodes.GETFIELD))
    private float fightClub$noLongswordCrit(Player self) {
        return CombatVanillaRules.suppressesCrits(self) ? 0.0F : self.fallDistance;
    }

    // Vanilla sweeps for anything that is a SwordItem; daggers and spears extend it but thrust.
    @WrapOperation(method = "attack", constant = @Constant(classValue = SwordItem.class))
    private boolean fightClub$noThrustSweep(Object item, Operation<Boolean> original) {
        return original.call(item) && !CombatVanillaRules.blocksVanillaSweep((Player) (Object) this);
    }
}
