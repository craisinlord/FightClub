package com.craisinlord.fightclub.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class PounceClient {
    private static final double SPEED = 0.85;
    private static final int MAX_TICKS = 9;
    private static final double STOP_GAP = 0.9;

    private static LivingEntity target;
    private static int ticks;
    private static int impactTicks;
    public static final int IMPACT_TICKS = 3;

    private PounceClient() {}

    public static void start(LocalPlayer player, LivingEntity pounceTarget) {
        target = pounceTarget;
        ticks = 0;
        tick(player);
    }

    public static boolean active() { return target != null; }

    public static LivingEntity target() { return target; }

    public static int impactTicks() { return impactTicks; }

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
        if (player.onGround() && offset.y > 0.6) y = 0.42;
        player.setDeltaMovement(dir.x * speed, y, dir.z * speed);
    }

    private static void finish(LocalPlayer player, boolean arrived) {
        target = null;
        ticks = 0;
        if (player == null) return;
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x * 0.2, motion.y, motion.z * 0.2);
        if (arrived) {
            player.swing(InteractionHand.MAIN_HAND, player.getMainHandItem().getAttackAnimation(), false);
            impactTicks = IMPACT_TICKS;
        }
    }

    static void reset() {
        target = null;
        ticks = 0;
        impactTicks = 0;
    }
}
