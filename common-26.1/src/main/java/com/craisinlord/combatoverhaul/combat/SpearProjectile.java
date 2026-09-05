package com.craisinlord.combatoverhaul.combat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class SpearProjectile extends ThrownTrident {
    public SpearProjectile(Level level, LivingEntity owner, ItemStack stack) { super(level, owner, stack); }
    @Override protected ItemStack getDefaultPickupItem() { return getWeaponItem().copy(); }
}
