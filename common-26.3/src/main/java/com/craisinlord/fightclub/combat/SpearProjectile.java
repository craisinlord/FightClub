package com.craisinlord.fightclub.combat;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
public final class SpearProjectile extends CombatProjectile {
    private static final int GLIDE_TICKS = 7;
    private static final double GLIDE_GRAVITY = 0.02D;
    private static final double DIVE_GRAVITY = 0.10D;
    private static final double DIVE_RAMP = 0.1D;
    private static final double WATER_INERTIA = 0.99D;
    private static final double VANILLA_WATER_INERTIA = 0.8D;
    private static final double WATER_GRAVITY = 0.025D;
    private static final float ROTATION_FOLLOW = 0.5F;

    public SpearProjectile(EntityType<? extends SpearProjectile> type, Level level) { super(type, level); }
    public SpearProjectile(Level level, LivingEntity owner, ItemStack stack) { super(CombatPlatform.spearProjectileType(), owner, level, stack); }

    @Override protected float projectileDamage() {
        WeaponSpec spec = WeaponSpecs.getOrLoadBuiltin("base", "trident");
        if (CombatWeapons.is(getItem().getItem(), "spear")) spec = WeaponSpecs.getOrLoadBuiltin(CombatWeapons.material(getItem().getItem()), "spear");
        return spec == null ? 4.0F : spec.attackDamage() * CombatWeapons.damageScale(getItem(), spec);
    }

    @Override public void tick() {
        if (!isEmbedded() && !isReturning() && isInWater()) {
            setDeltaMovement(getDeltaMovement().scale(WATER_INERTIA / VANILLA_WATER_INERTIA));
        }
        super.tick();
    }

    @Override protected double getDefaultGravity() {
        if (isInWater()) return WATER_GRAVITY;
        double falling = -getDeltaMovement().y;
        if (tickCount <= GLIDE_TICKS || falling <= 0.0D) return GLIDE_GRAVITY;
        return Mth.lerp(Math.min(1.0D, falling / DIVE_RAMP), GLIDE_GRAVITY, DIVE_GRAVITY);
    }

    @Override protected void updateRotation() {
        if (rotationFrozen()) return;
        Vec3 motion = getDeltaMovement();
        float pitch = (float) (Mth.atan2(motion.y, motion.horizontalDistance()) * Mth.RAD_TO_DEG);
        float yaw = (float) (Mth.atan2(motion.x, motion.z) * Mth.RAD_TO_DEG);
        setXRot(xRotO + Mth.wrapDegrees(pitch - xRotO) * ROTATION_FOLLOW);
        setYRot(yRotO + Mth.wrapDegrees(yaw - yRotO) * ROTATION_FOLLOW);
    }
}
