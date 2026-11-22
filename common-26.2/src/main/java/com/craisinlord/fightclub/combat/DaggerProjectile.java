package com.craisinlord.fightclub.combat;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
public final class DaggerProjectile extends CombatProjectile {
    public DaggerProjectile(EntityType<? extends DaggerProjectile> type, Level level) { super(type, level); }
    public DaggerProjectile(Level level, LivingEntity owner, ItemStack stack) { super(CombatPlatform.daggerProjectileType(), owner, level, stack); }
    public static final double GRAVITY = 0.05D;
    @Override protected double getDefaultGravity() { return GRAVITY; }

    @Override protected float projectileDamage() {
        if (CombatWeapons.is(getItem().getItem(), "dagger")) {
            WeaponSpec spec = WeaponSpecs.getOrLoadBuiltin(CombatWeapons.material(getItem().getItem()), "dagger");
            return spec == null ? 4.0F : CombatWeapons.throwDamage(getItem(), spec);
        }
        return 4.0F;
    }
}
