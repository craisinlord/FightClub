package com.craisinlord.fightclub.combat;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
public final class SpearProjectile extends CombatProjectile {
    public SpearProjectile(EntityType<? extends SpearProjectile> type, Level level) { super(type, level); }
    public SpearProjectile(Level level, LivingEntity owner, ItemStack stack) { super(CombatPlatform.spearProjectileType(), owner, level, stack); }
    @Override protected float projectileDamage() {
        WeaponSpec spec = WeaponSpecs.getOrLoadBuiltin("base", "trident");
        if (CombatWeapons.is(getItem().getItem(), "spear")) {
            spec = WeaponSpecs.getOrLoadBuiltin(CombatWeapons.material(getItem().getItem()), "spear");
        }
        return spec == null ? 4.0F : spec.attackDamage();
    }
}
