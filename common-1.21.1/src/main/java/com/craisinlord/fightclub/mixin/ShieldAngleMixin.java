package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.combat.WeaponSpec;
import com.craisinlord.fightclub.combat.WeaponSpecs;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public abstract class ShieldAngleMixin {
    @ModifyReturnValue(method = "isDamageSourceBlocked", at = @At("RETURN"))
    private boolean fightClub$narrowShield(boolean blocked, DamageSource source) {
        if (!blocked || !((Object) this instanceof Player player) || !com.craisinlord.fightclub.combat.CombatWeapons.isShield(player.getUseItem())
                || !com.craisinlord.fightclub.CombatConfig.enabled(com.craisinlord.fightclub.CombatConfig.NARROW_SHIELD)) return blocked;
        WeaponSpec spec = com.craisinlord.fightclub.combat.CombatWeapons.shieldSpec(player.getUseItem());
        Vec3 from = source.getSourcePosition();
        if (spec == null || from == null || spec.blockingAngleDegrees() >= 180.0F) return blocked;
        Vec3 toSource = new Vec3(from.x - player.getX(), 0.0, from.z - player.getZ());
        if (toSource.lengthSqr() < 1.0E-4) return true;
        Vec3 facing = Vec3.directionFromRotation(0.0F, player.getYHeadRot());
        return facing.dot(toSource.normalize()) >= Math.cos(Math.toRadians(spec.blockingAngleDegrees() * 0.5));
    }
}
