package com.craisinlord.fightclub.combat;

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
    /** Loyalty's return acceleration for the thrown stack (0 = no Loyalty), worked out on the server. */
    private static final EntityDataAccessor<Byte> LOYALTY = SynchedEntityData.defineId(CombatProjectile.class, EntityDataSerializers.BYTE);
    /** Flying back to its owner (Loyalty), like a returning vanilla trident. */
    private static final EntityDataAccessor<Boolean> RETURNING = SynchedEntityData.defineId(CombatProjectile.class, EntityDataSerializers.BOOLEAN);
    /** Server ticks spent stuck; a Loyalty weapon starts back after vanilla's 4. */
    private int stuckTicks;
    /** Client tick count when this projectile stuck, for the impact wobble. */
    private int embedTick = Integer.MIN_VALUE / 2;

    protected CombatProjectile(EntityType<? extends CombatProjectile> type, Level level) { super(type, level); }
    protected CombatProjectile(EntityType<? extends CombatProjectile> type, LivingEntity owner, Level level, ItemStack stack) {
        super(type, level);
        this.setOwner(owner);
        this.setItem(stack);
        this.setPos(owner.getX(), owner.getEyeY() - 0.1D, owner.getZ());
        creativeThrow = owner instanceof Player player && player.getAbilities().instabuild;
    }

    /**
     * Thrown by a creative player, who kept the item: it never drops or gives a copy (vanilla's
     * creative-only pickup rule), so a creative throw can't duplicate the weapon.
     */
    private boolean creativeThrow;

    // Saved so a stuck (or returning) weapon survives chunk unloads and world reloads. The owner is
    // saved by Projectile.
    @Override protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (!getItem().isEmpty()) output.store("Item", ItemStack.CODEC, getItem());
        output.putBoolean("Embedded", isEmbedded());
        output.putBoolean("CreativeThrow", creativeThrow);
        output.putBoolean("Returning", isReturning());
    }

    @Override protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
        super.readAdditionalSaveData(input);
        setItem(input.read("Item", ItemStack.CODEC).orElse(ItemStack.EMPTY));
        entityData.set(EMBEDDED, input.getBooleanOr("Embedded", false));
        creativeThrow = input.getBooleanOr("CreativeThrow", false);
        entityData.set(RETURNING, input.getBooleanOr("Returning", false));
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ITEM, ItemStack.EMPTY);
        builder.define(EMBEDDED, false);
        builder.define(LOYALTY, (byte) 0);
        builder.define(RETURNING, false);
    }
    public void setItem(ItemStack stack) {
        entityData.set(ITEM, stack.copy());
        if (level() instanceof net.minecraft.server.level.ServerLevel server) {
            entityData.set(LOYALTY, (byte) net.minecraft.util.Mth.clamp(
                    net.minecraft.world.item.enchantment.EnchantmentHelper.getTridentReturnToOwnerAcceleration(server, stack, this), 0, 127));
        }
    }
    @Override public ItemStack getItem() { return this.entityData.get(ITEM); }
    public boolean isEmbedded() { return this.entityData.get(EMBEDDED); }
    @Override protected double getDefaultGravity() { return 0.03D; }

    public boolean isReturning() { return entityData.get(RETURNING); }
    private int loyalty() { return entityData.get(LOYALTY); }

    @Override public void tick() {
        if (isReturning()) {
            tickReturn();
            return;
        }
        if (isEmbedded()) {
            // No physics while stuck, but keep the "previous" position/rotation in step so the
            // renderer's interpolation doesn't jitter between stale and current values.
            setOldPosAndRot();
            if (!level().isClientSide() && level().noCollision(new net.minecraft.world.phys.AABB(position(), position()).inflate(0.06D))) {
                // The block it was stuck in is gone: fall like a stuck arrow or trident does.
                entityData.set(EMBEDDED, false);
                setNoGravity(false);
                stuckTicks = 0;
                return;
            }
            if (!level().isClientSide() && loyalty() > 0 && ++stuckTicks > 4 && canReturnTo(getOwner())) startReturn();
            return;
        }
        super.tick();
    }

    /** Vanilla's trident rule: no return to a missing (logged out), dead or spectating owner. */
    private static boolean canReturnTo(net.minecraft.world.entity.Entity owner) {
        return owner != null && owner.isAlive() && !(owner instanceof Player player && player.isSpectator());
    }

    private void startReturn() {
        entityData.set(RETURNING, true);
        entityData.set(EMBEDDED, false);
        setNoGravity(true);
        stuckTicks = 0;
        // Only player-thrown weapons play the return sound, as for tridents (not ones thrown by mobs).
        if (getOwner() instanceof Player) playSound(net.minecraft.sounds.SoundEvents.TRIDENT_RETURN, 10.0F, 1.0F);
    }

    /** Loyalty: home in on the owner's eyes with vanilla's trident motion, passing through blocks and mobs. */
    private void tickReturn() {
        net.minecraft.world.entity.Entity owner = getOwner();
        if (!canReturnTo(owner)) {
            // The owner died or left mid-flight: fall out of the air as an item (the client waits for the server).
            if (!level().isClientSide()) {
                if (!creativeThrow) dropAsItem();
                discard();
            }
            return;
        }
        if (!(owner instanceof Player) && position().distanceTo(owner.getEyePosition()) < owner.getBbWidth() + 1.0D) {
            if (!level().isClientSide()) discard();
            return;
        }
        int loyalty = loyalty();
        Vec3 toOwner = owner.getEyePosition().subtract(position());
        setPosRaw(getX(), getY() + toOwner.y * 0.015D * loyalty, getZ());
        setDeltaMovement(getDeltaMovement().scale(0.95D).add(toOwner.normalize().scale(0.05D * loyalty)));
        Vec3 motion = getDeltaMovement();
        setPos(getX() + motion.x, getY() + motion.y, getZ() + motion.z);
        // Flies back butt first, the point still facing away from the owner, like a returning trident.
        setYRot((float) (net.minecraft.util.Mth.atan2(-motion.x, -motion.z) * net.minecraft.util.Mth.RAD_TO_DEG));
        setXRot((float) (net.minecraft.util.Mth.atan2(-motion.y, motion.horizontalDistance()) * net.minecraft.util.Mth.RAD_TO_DEG));
    }

    private void dropAsItem() {
        net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(level(), getX(), getY(), getZ(), getItem().copy());
        item.setDefaultPickUpDelay();
        level().addFreshEntity(item);
    }

    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        // Wobble only when it sticks in front of us, not when an already-stuck one loads in.
        if (EMBEDDED.equals(key) && isEmbedded() && level().isClientSide() && tickCount > 1) embedTick = tickCount;
    }

    /**
     * A stuck weapon sits exactly on the block face, so sampling light at its position reads the
     * inside of the block and renders it black. Sample a little back along the flight instead.
     * Vanilla's EntityRenderer lights the entity from this point.
     */
    @Override public Vec3 getLightProbePosition(float partialTick) {
        Vec3 position = super.getLightProbePosition(partialTick);
        if (!isEmbedded()) return position;
        // Projectile rotations: yaw = atan2(dx, dz), pitch = atan2(dy, horizontal).
        double yaw = Math.toRadians(getYRot()), pitch = Math.toRadians(getXRot());
        Vec3 flight = new Vec3(Math.sin(yaw) * Math.cos(pitch), Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        return position.subtract(flight.scale(0.35));
    }

    /** Remaining wobble (in ticks) after sticking, like an arrow's shake; 0 when settled. */
    public float wobble(float partialTick) {
        return Math.max(0.0F, 7.0F - (tickCount - embedTick + partialTick));
    }

    @Override protected void onHit(HitResult result) {
        if (result instanceof EntityHitResult hit) {
            if (!level().isClientSide()) {
                if (hit.getEntity() instanceof LivingEntity living && living != getOwner()) {
                    living.hurtServer((net.minecraft.server.level.ServerLevel) level(), this.damageSources().thrown(this, getOwner()), projectileDamage());
                    playSound(CombatSounds.PROJECTILE_HIT, 1.0F, 0.95F + random.nextFloat() * 0.1F);
                }
                if (loyalty() > 0 && canReturnTo(getOwner())) {
                    // Loyalty: bounce off and fly back instead of dropping (vanilla trident).
                    setDeltaMovement(getDeltaMovement().multiply(-0.01D, -0.1D, -0.01D));
                    startReturn();
                    return;
                }
                if (!creativeThrow) dropAsItem();
            } else if (loyalty() > 0) {
                // The server decides whether it flies back or drops; don't vanish ahead of it.
                setDeltaMovement(getDeltaMovement().multiply(-0.01D, -0.1D, -0.01D));
                return;
            }
            discard();
            return;
        }
        if (!level().isClientSide() && !isEmbedded()) playSound(CombatSounds.PROJECTILE_STICK, 1.0F, 0.95F + random.nextFloat() * 0.1F);
        boolean wasEmbedded = isEmbedded();
        this.entityData.set(EMBEDDED, true);
        this.setDeltaMovement(Vec3.ZERO);
        this.setNoGravity(true);
        this.setPos(result.getLocation());
        setOldPosAndRot();
        // entityData.set doesn't fire onSyncedDataUpdated on the side that sets it; start the wobble here too.
        if (!wasEmbedded && level().isClientSide()) embedTick = tickCount;
    }

    protected float projectileDamage() { return 4.0F; }

    @Override public void playerTouch(Player player) {
        if (level().isClientSide()) return;
        if (isReturning()) {
            // Arrived: only its owner catches it, and it keeps circling them until there is room.
            if (player != getOwner()) return;
            if (creativeThrow) {
                // The thrower kept their copy.
                sendPickup(player);
                discard();
                return;
            }
            if (!player.getInventory().add(getItem().copy())) return;
            sendPickup(player);
            discard();
            return;
        }
        if (!isEmbedded() || player.distanceToSqr(this) > 4.0D) return;
        if (creativeThrow) {
            // The thrower kept their copy; only they (or another creative player) can clear it away.
            if (player.getAbilities().instabuild) discard();
            return;
        }
        ItemStack pickup = getItem().copy();
        if (!player.getInventory().add(pickup)) player.drop(pickup, false);
        else sendPickup(player);
        discard();
    }

    /** Vanilla's pickup feedback (pop sound, item flying into the player); Player.take only sends it for arrows, items and orbs. */
    private void sendPickup(Player player) {
        ((net.minecraft.server.level.ServerLevel) level()).getChunkSource().sendToTrackingPlayers(this,
                new net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket(getId(), player.getId(), 1));
    }
}
