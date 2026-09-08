package com.craisinlord.combatoverhaul.combat;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public abstract class CombatProjectile extends ThrowableProjectile implements ItemSupplier {
    private static final EntityDataAccessor<ItemStack> ITEM = SynchedEntityData.defineId(CombatProjectile.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Boolean> EMBEDDED = SynchedEntityData.defineId(CombatProjectile.class, EntityDataSerializers.BOOLEAN);
    protected CombatProjectile(EntityType<? extends CombatProjectile> type, Level level) { super(type, level); }
    protected CombatProjectile(EntityType<? extends CombatProjectile> type, LivingEntity owner, Level level, ItemStack stack) { super(type, level); setOwner(owner); setItem(stack); setPos(owner.getX(), owner.getEyeY() - 0.1D, owner.getZ()); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(ITEM, ItemStack.EMPTY); builder.define(EMBEDDED, false); }
    public void setItem(ItemStack stack) { entityData.set(ITEM, stack.copy()); }
    @Override public ItemStack getItem() { return entityData.get(ITEM); }
    public boolean isEmbedded() { return entityData.get(EMBEDDED); }
    @Override protected double getDefaultGravity() { return 0.03D; }
    @Override public void tick() { if (!isEmbedded()) super.tick(); }
    @Override protected void onHit(HitResult result) {
        if (result instanceof EntityHitResult hit) {
            if (!level().isClientSide) {
                if (hit.getEntity() instanceof LivingEntity living && living != getOwner()) living.hurt(damageSources().thrown(this, getOwner()), 4.0F);
                net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(level(), getX(), getY(), getZ(), getItem().copy());
                item.setDefaultPickUpDelay();
                level().addFreshEntity(item);
            }
            discard();
            return;
        }
        entityData.set(EMBEDDED, true); setDeltaMovement(Vec3.ZERO); setNoGravity(true); setPos(result.getLocation());
    }
    @Override public void playerTouch(Player player) { if (!isEmbedded() || level().isClientSide || player.distanceToSqr(this) > 4.0D) return; ItemStack pickup = getItem().copy(); if (!player.getInventory().add(pickup)) player.drop(pickup, false); discard(); }
}
