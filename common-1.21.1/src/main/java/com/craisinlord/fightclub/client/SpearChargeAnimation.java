package com.craisinlord.fightclub.client;

import com.craisinlord.fightclub.combat.CombatChargeState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaternionf;

/**
 * The newer vanilla spear's "hold out and charge" animation (MC 26.x {@code SpearAnimations}),
 * ported to 1.21.1 for the hold-left-click spear charge. Our spear uses the same in-hand model
 * as the vanilla spear, so these numbers line up 1:1.
 *
 * <p>Vanilla's charge has phases tied to the kinetic weapon's timers: raise (the spear comes up
 * and levels out), then after a while sway (it starts shaking and tipping as the charge
 * "tires"). Our charge never runs out, so the later lower / raise-back phases are left out.
 */
public final class SpearChargeAnimation {
    /** Ticks after the raise finishes before the shaking has fully set in (vanilla: dismount time). */
    private static final int SWAY_AFTER_RAISE = 80;
    /** Vanilla's sway takes 20 ticks to ramp up. */
    private static final int SWAY_RAMP = 20;
    /** Vanilla spears' forward_movement (third-person thrust distance). */
    private static final float FORWARD_MOVEMENT = 0.38F;

    private SpearChargeAnimation() {}

    record Params(float raise, float raiseStart, float raiseMiddle, float raiseEnd, float sway,
                  float swayIntensity, float swaySlow, float swayFast) {}

    /** Vanilla's UseParams for a charge held {@code time} ticks. */
    static Params params(Player player, float time) {
        int raiseTicks = Math.max(1, CombatChargeState.chargeTicksFor(player.getMainHandItem()));
        int swayEnd = raiseTicks + SWAY_AFTER_RAISE;
        float raise = progress(time, 0.0F, raiseTicks);
        float sway = progress(time, swayEnd - SWAY_RAMP, swayEnd);
        float intensity = 2.0F * outCirc(sway);
        return new Params(raise, progress(raise, 0.0F, 0.5F), progress(raise, 0.5F, 0.8F), progress(raise, 0.8F, 1.0F),
                sway, intensity,
                Mth.sin(time * 19.0F * Mth.DEG_TO_RAD) * intensity,
                Mth.sin(time * 30.0F * Mth.DEG_TO_RAD) * intensity);
    }

    /** First person: replaces vanilla's arm transform entirely (vanilla's SPEAR use case). */
    public static void firstPerson(PoseStack poseStack, Player player, float time, float side) {
        Params p = params(player, time);
        poseStack.translate(side * 0.56F, -0.52F, -0.72F);
        poseStack.translate(
                side * (p.raise() * 0.15F + p.raiseEnd() * -0.05F + p.sway() * -0.1F + p.swaySlow() * 0.005F),
                p.raise() * -0.075F + p.raiseMiddle() * 0.075F + p.swayFast() * 0.01F,
                p.raiseStart() * 0.05F + p.raiseEnd() * -0.05F + p.swaySlow() * 0.005F);
        rotateAround(poseStack, Axis.XP.rotationDegrees(-65.0F * inOutBack(p.raise()) - 0.5F * p.swayFast()), 0.0F, 0.1F, 0.0F);
        rotateAround(poseStack, Axis.YN.rotationDegrees(side * (-90.0F * progress(p.raise(), 0.5F, 0.55F)
                + 90.0F * p.sway() + 2.0F * p.swaySlow())), side * 0.15F, 0.0F, 0.0F);
    }

    /** Third person arm (vanilla {@code thirdPersonHandUse}), blended in by {@code amount}. */
    static void thirdPersonArm(ModelPart arm, ModelPart head, boolean rightArm, Player player, float time, float amount) {
        float invert = rightArm ? 1.0F : -1.0F;
        float yRot = -0.1F * invert + head.yRot;
        float xRot = -Mth.HALF_PI + head.xRot + 0.8F;
        if (player.isFallFlying() || player.getSwimAmount(0.0F) > 0.0F) xRot -= 0.9599311F;
        yRot = Mth.DEG_TO_RAD * Mth.clamp(Mth.RAD_TO_DEG * yRot, -60.0F, 60.0F);
        xRot = Mth.DEG_TO_RAD * Mth.clamp(Mth.RAD_TO_DEG * xRot, -120.0F, 30.0F);
        float zRot = arm.zRot;
        if (time > 0.0F) {
            Params p = params(player, time);
            yRot += -invert * p.swayFast() * Mth.DEG_TO_RAD * p.swayIntensity();
            zRot += -invert * p.swaySlow() * Mth.DEG_TO_RAD * p.swayIntensity() * 0.5F;
            xRot += Mth.DEG_TO_RAD * (-40.0F * p.raiseStart() + 30.0F * p.raiseMiddle() - 20.0F * p.raiseEnd()
                    + 0.6F * p.swaySlow() * p.swayIntensity());
        }
        arm.xRot = Mth.lerp(amount, arm.xRot, xRot);
        arm.yRot = Mth.lerp(amount, arm.yRot, yRot);
        arm.zRot = Mth.lerp(amount, arm.zRot, zRot);
    }

    /** Third person held item (vanilla {@code thirdPersonUseItem}), applied just before the item renders. */
    public static void thirdPersonItem(PoseStack poseStack, Player player, float time, boolean rightArm) {
        if (time <= 0.0F) return;
        Params p = params(player, time);
        float invert = rightArm ? 1.0F : -1.0F;
        float raiseModified = 1.0F - outBack(1.0F - p.raise());
        poseStack.translate(0.0F, 0.0F, -FORWARD_MOVEMENT * raiseModified);
        rotateAround(poseStack, Axis.XN.rotationDegrees(70.0F * p.raise()), 0.0F, -0.03125F, 0.125F);
        rotateAround(poseStack, Axis.YP.rotationDegrees(invert * 90.0F * (p.raise() - p.sway())), 0.0F, 0.0F, 0.125F);
    }

    private static void rotateAround(PoseStack poseStack, Quaternionf rotation, float x, float y, float z) {
        poseStack.translate(x, y, z);
        poseStack.mulPose(rotation);
        poseStack.translate(-x, -y, -z);
    }

    private static float progress(float time, float start, float end) {
        return Mth.clamp(Mth.inverseLerp(time, start, end), 0.0F, 1.0F);
    }

    private static float outCirc(float x) {
        return (float) Math.sqrt(1.0F - (x - 1.0F) * (x - 1.0F));
    }

    private static float outBack(float x) {
        float d = x - 1.0F;
        return 1.0F + 2.70158F * d * d * d + 1.70158F * d * d;
    }

    private static float inOutBack(float x) {
        if (x < 0.5F) return 4.0F * x * x * (7.189819F * x - 2.5949094F) / 2.0F;
        float dt = 2.0F * x - 2.0F;
        return (dt * dt * (3.5949094F * dt + 2.5949094F) + 2.0F) / 2.0F;
    }
}
