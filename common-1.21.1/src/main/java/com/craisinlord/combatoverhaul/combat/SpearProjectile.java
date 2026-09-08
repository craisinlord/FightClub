package com.craisinlord.combatoverhaul.combat;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
public final class SpearProjectile extends CombatProjectile {
    public SpearProjectile(EntityType<? extends SpearProjectile> type, Level level) { super(type, level); }
    public SpearProjectile(Level level, LivingEntity owner, ItemStack stack) { super(CombatPlatform.spearProjectileType(), owner, level, stack); }
}
