package com.craisinlord.fightclub.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The dashing half of a charged dagger pounce. Player movement is client-side, so the pouncing
 * client steers itself at the target each tick; the server only checks the pick and strikes once
 * the player arrives (CombatChargeState).
 */
public final class PounceClient {
    /** Blocks per tick: about three times sprinting, so a 6-block pounce takes ~7 ticks. */
    private static final double SPEED = 0.85;
    /** Matches the server's timeout less latency room. */
    private static final int MAX_TICKS = 9;
    /** Stop this far short of the target's edge, inside dagger reach. */
    private static final double STOP_GAP = 0.9;

    private static LivingEntity target;
    private static int ticks;
    /** Ticks left of the short FOV punch after landing a pounce. */
    private static int impactTicks;
    public static final int IMPACT_TICKS = 3;

    private PounceClient() {}

    public static void start(LocalPlayer player, LivingEntity pounceTarget) {
        target = pounceTarget;
        ticks = 0;
        tick(player);
    }

    public static boolean active() { return target != null; }

    /** The mob being pounced on, or null. */
    public static LivingEntity target() { return target; }

    public static int impactTicks() { return impactTicks; }

    /** Called at the end of each client tick; the velocity set here moves the player next tick. */
    static void tick(LocalPlayer player) {
        if (impactTicks > 0) impactTicks--;
        if (target == null) return;
        if (player == null || !target.isAlive() || target.level() != player.level() || player.isPassenger()
                || ++ticks > MAX_TICKS || (ticks > 1 && player.horizontalCollision)) {
            finish(player, false);
            return;
        }
        Vec3 offset = target.position().subtract(player.position());
        Vec3 flat = new Vec3(offset.x, 0.0, offset.z);
        double gap = flat.length() - (target.getBbWidth() + player.getBbWidth()) * 0.5;
        if (gap <= STOP_GAP) {
            finish(player, true);
            return;
        }
        Vec3 dir = flat.normalize();
        double speed = Math.min(SPEED, gap - STOP_GAP + 0.3);
        double y = player.getDeltaMovement().y;
        // Hop up onto a target standing a block or so higher.
        if (player.onGround() && offset.y > 0.6) y = 0.42;
        player.setDeltaMovement(dir.x * speed, y, dir.z * speed);
    }

    private static void finish(LocalPlayer player, boolean arrived) {
        target = null;
        ticks = 0;
        if (player == null) return;
        Vec3 motion = player.getDeltaMovement();
        // Bleed off the dash so the player doesn't skid through; the server's hop-back follows.
        player.setDeltaMovement(motion.x * 0.2, motion.y, motion.z * 0.2);
        if (arrived) {
            player.swing(InteractionHand.MAIN_HAND);
            impactTicks = IMPACT_TICKS;
        }
    }

    static void reset() {
        target = null;
        ticks = 0;
        impactTicks = 0;
    }
}
