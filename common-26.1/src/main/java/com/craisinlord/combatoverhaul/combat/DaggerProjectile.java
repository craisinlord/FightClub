package com.craisinlord.combatoverhaul.combat;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
public final class DaggerProjectile extends CombatProjectile {
    public DaggerProjectile(EntityType<? extends DaggerProjectile> type, Level level) { super(type, level); }
    public DaggerProjectile(Level level, LivingEntity owner, ItemStack stack) { super(CombatPlatform.daggerProjectileType(), owner, level, stack); }
}
