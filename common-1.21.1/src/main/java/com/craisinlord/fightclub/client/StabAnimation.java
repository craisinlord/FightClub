package com.craisinlord.fightclub.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

public final class StabAnimation {
    private StabAnimation() {}

    public static void firstPerson(PoseStack poseStack, float attack, float side) {
        if (attack <= 0.0F) return;
        float starting = inOutSine(progress(attack, 0.0F, 0.05F));
        float middle = outBack(progress(attack, 0.05F, 0.2F));
        float ending = inOutExpo(progress(attack, 0.4F, 1.0F));
        poseStack.translate(side * 0.1F * (starting - middle), -0.075F * (starting - ending), 0.65F * (starting - middle));
        poseStack.mulPose(Axis.XP.rotationDegrees(-70.0F * (starting - ending)));
        poseStack.translate(0.0F, 0.0F, -0.25F * (ending - middle));
    }

    public static float extension(float attack) {
        if (attack <= 0.0F || attack >= 1.0F) return 0.0F;
        float starting = inOutSine(progress(attack, 0.0F, 0.05F));
        float ending = inOutExpo(progress(attack, 0.4F, 1.0F));
        return Mth.clamp(starting - ending, 0.0F, 1.0F);
    }

    public static void thirdPersonArm(ModelPart arm, ModelPart body, float attackTime) {
        if (attackTime <= 0.0F) return;
        float prepare = inOutSine(progress(attackTime, 0.0F, 0.05F));
        float attack = inQuad(progress(attackTime, 0.05F, 0.2F));
        float retract = inOutExpo(progress(attackTime, 0.4F, 1.0F));
        body.yRot = 0.0F;
        arm.xRot = -Mth.PI / 10.0F + (90.0F * prepare - 120.0F * attack + 30.0F * retract) * Mth.DEG_TO_RAD;
        arm.yRot = 0.0F;
        arm.zRot = 0.0F;
    }

    private static float progress(float time, float start, float end) {
        return Mth.clamp((time - start) / (end - start), 0.0F, 1.0F);
    }

    private static float inOutSine(float x) {
        return -(Mth.cos(Mth.PI * x) - 1.0F) / 2.0F;
    }

    private static float inQuad(float x) {
        return x * x;
    }

    private static float outBack(float x) {
        float c1 = 1.70158F;
        float c3 = c1 + 1.0F;
        float u = x - 1.0F;
        return 1.0F + c3 * u * u * u + c1 * u * u;
    }

    private static float inOutExpo(float x) {
        if (x <= 0.0F) return 0.0F;
        if (x >= 1.0F) return 1.0F;
        return x < 0.5F ? (float) Math.pow(2.0, 20.0 * x - 10.0) / 2.0F
                : (2.0F - (float) Math.pow(2.0, -20.0 * x + 10.0)) / 2.0F;
    }
}
