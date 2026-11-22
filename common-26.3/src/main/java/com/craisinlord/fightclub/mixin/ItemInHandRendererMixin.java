package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.CombatConfig;
import com.craisinlord.fightclub.client.CombatClientPoses;
import com.craisinlord.fightclub.client.CombatInputClient;
import com.craisinlord.fightclub.client.CombatModels;
import com.craisinlord.fightclub.client.CombatPose;
import com.craisinlord.fightclub.client.CombatPoseConfig;
import com.craisinlord.fightclub.combat.CombatChargeState;
import com.craisinlord.fightclub.combat.WeaponKind;
import com.craisinlord.fightclub.compat.BetterCombatCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class ItemInHandRendererMixin {
    @Unique
    private static final org.joml.Quaternionf HANDHELD_FIRST_PERSON_INVERSE =
            new org.joml.Quaternionf().rotationXYZ(0.0F, -90.0F * Mth.DEG_TO_RAD, 25.0F * Mth.DEG_TO_RAD).conjugate();

    @Shadow
    private void applyItemArmTransform(PoseStack poseStack, HumanoidArm arm, float inverseArmHeight) {}

    @Shadow
    private void applyItemArmAttackTransform(PoseStack poseStack, HumanoidArm arm, float attackValue) {}

    @Redirect(method = "submitArmWithItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;getUseAnimation()Lnet/minecraft/world/item/ItemUseAnimation;"))
    private ItemUseAnimation fightClub$spearChargeAnimation(ItemStack stack) {
        if (CombatInputClient.isCharging() && CombatChargeState.usesItemForCharge(stack.getItem())) return ItemUseAnimation.SPEAR;
        return stack.getUseAnimation();
    }

    @Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
    private void fightClub$render(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, float partialTick,
                                  float xRot, InteractionHand hand, float swingProgress, ItemStack stack, float equippedProgress,
                                  PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        AbstractClientPlayer player = Minecraft.getInstance().player;
        if (player == null || stack.isEmpty() || state.isScoping || stack.is(net.minecraft.world.item.Items.CROSSBOW)
                || stack.has(net.minecraft.core.component.DataComponents.MAP_ID)
                || !CombatConfig.enabled(CombatConfig.ANIMATIONS)) return;
        boolean mainHand = hand == InteractionHand.MAIN_HAND;
        HumanoidArm arm = mainHand ? player.getMainArm() : player.getMainArm().getOpposite();
        boolean right = arm == HumanoidArm.RIGHT;
        float side = right ? 1.0F : -1.0F;
        WeaponKind kind = WeaponKind.of(stack);
        boolean usingThisHand = player.isUsingItem() && player.getUsedItemHand() == hand;
        CombatClientPoses.View view = CombatClientPoses.view(player, partialTick);

        if (usingThisHand && kind == WeaponKind.DAGGER && stack.getUseAnimation() == ItemUseAnimation.TRIDENT
                && player.getUseItemRemainingTicks() > 0) {
            poseStack.pushPose();
            applyItemArmTransform(poseStack, arm, equippedProgress);
            float timeHeld = Math.max(0.0F, stack.getUseDuration(player) - (player.getUseItemRemainingTicks() - partialTick + 1.0F));
            CombatPoseConfig.Pose flip = CombatPoseConfig.get(CombatPose.DAGGER_THROW_FLIP);
            CombatPoseConfig.Pose hold = CombatPoseConfig.get(CombatPose.DAGGER_THROW);
            Vector3f pos = new Vector3f();
            Vector3f rot = new Vector3f();
            Vector3f orient = new Vector3f();
            CombatPoseConfig.daggerToss(flip, timeHeld / flip.blendIn(), pos, rot, orient);
            float windup = fightClub$smooth((timeHeld - flip.blendIn()) / hold.blendIn());
            if (windup > 0.0F) {
                if (!hold.keys().isEmpty()) {
                    CombatPoseConfig.Key wound = hold.keys().get(hold.keys().size() - 1);
                    pos.add(new Vector3f(wound.translation()).mul(windup));
                    rot.add(new Vector3f(wound.rotation()).mul(windup));
                    orient.add(new Vector3f(wound.orient()).mul(windup));
                }
                float shake = fightClub$shake(player, partialTick) * hold.tremble() * windup;
                pos.add(shake, shake * 0.6F, 0.0F);
            }
            fightClub$daggerHeading(poseStack, side, pos, rot, orient);
            fightClub$submitItem(state, mainHand, poseStack, collector, light);
            poseStack.popPose();
            ci.cancel();
            return;
        }

        CombatPose pose = CombatPose.NONE;
        float held = 0.0F, recoil = 0.0F, bash = 0.0F, dip = 0.0F;
        if (mainHand) {
            if (view != null) {
                pose = view.pose();
                held = view.amount();
                recoil = view.recoil();
            }
            if (kind.hasRecovery()) {
                dip = fightClub$smooth(player.getCooldowns().getCooldownPercent(stack, partialTick));
            }
        }
        if (view != null && kind == WeaponKind.SHIELD && usingThisHand) bash = view.bash();
        float spin = view != null && mainHand && kind == WeaponKind.LONGSWORD ? view.spin() : 0.0F;

        CombatPose swingStyle = CombatPose.NONE;
        if (mainHand && !BetterCombatCompat.isLoaded()) {
            if (kind == WeaponKind.DAGGER) swingStyle = CombatPose.JAB_DAGGER;
            else if (kind == WeaponKind.LONGSWORD) swingStyle = CombatPose.HEAVY_LONGSWORD;
        }
        boolean tridentStab = kind == WeaponKind.TRIDENT && mainHand && !BetterCombatCompat.isLoaded();
        boolean customSwing = (swingStyle != CombatPose.NONE || tridentStab) && swingProgress > 0.0F;

        if (held <= 0.0F && recoil <= 0.0F && bash <= 0.0F && dip <= 0.0F && spin <= 0.0F && !customSwing) return;
        if (usingThisHand && pose == CombatPose.NONE && bash <= 0.0F) return;
        boolean vanillaStab = (kind == WeaponKind.SPEAR || tridentStab) && held <= 0.0F;

        poseStack.pushPose();
        float keep = 1.0F - held;
        boolean jab = swingStyle == CombatPose.JAB_DAGGER || swingStyle == CombatPose.JAB_SPEAR;
        boolean sweep = swingStyle == CombatPose.HEAVY_LONGSWORD;
        if (!usingThisHand && !jab && !sweep && !vanillaStab) {
            float root = Mth.sqrt(swingProgress);
            poseStack.translate(side * -0.4F * Mth.sin(root * Mth.PI) * keep,
                    0.2F * Mth.sin(root * Mth.TWO_PI) * keep,
                    -0.2F * Mth.sin(swingProgress * Mth.PI) * keep);
        }
        applyItemArmTransform(poseStack, arm, equippedProgress);

        float tridentRoll = 0.0F;
        if (kind == WeaponKind.TRIDENT && mainHand && held > 0.0F && pose == CombatPose.TRIDENT_LOWERED) {
            tridentRoll = CombatPoseConfig.modelRoll(pose) * held;
        }
        if (!usingThisHand && keep > 0.0F) {
            if (vanillaStab) {
                if (tridentStab) {
                    float out = fightClub$stabExtension(swingProgress);
                    tridentRoll = CombatPoseConfig.modelRoll(CombatPose.JAB_SPEAR) * out;
                    fightClub$apply(poseStack, side, CombatPoseConfig.get(CombatPose.JAB_SPEAR), out, 0.0F, 0.0F);
                }
                net.minecraft.client.model.effects.SpearAnimations.firstPersonAttack(swingProgress, poseStack, (int) side, arm);
            } else if (jab) {
                CombatPoseConfig.Pose jabPose = CombatPoseConfig.get(swingStyle);
                if (!jabPose.keys().isEmpty()) fightClub$keyed(poseStack, side, jabPose, swingProgress, keep);
                else fightClub$apply(poseStack, side, jabPose, fightClub$punch(swingProgress) * keep, 0.0F, 0.0F);
            } else if (sweep) {
                fightClub$arc(poseStack, side, CombatPoseConfig.get(CombatClientPoses.backhand(player)
                        ? CombatPose.HEAVY_LONGSWORD_BACK : swingStyle), swingProgress, keep);
            } else {
                if (held <= 0.0F) applyItemArmAttackTransform(poseStack, arm, swingProgress);
            }
        }
        if (spin > 0.0F) fightClub$arc(poseStack, side, CombatPoseConfig.get(CombatPose.LONGSWORD_SPIN), spin, 1.0F);

        if (recoil > 0.0F) fightClub$apply(poseStack, side, CombatPoseConfig.get(CombatPose.BLOCK_RECOIL), recoil, 0.0F, 0.0F);
        if (held > 0.0F) {
            CombatPoseConfig.Pose config = CombatPoseConfig.get(pose);
            float progress = (pose == CombatPose.DAGGER_CHARGE || pose == CombatPose.SPEAR_LOWERED || pose == CombatPose.TRIDENT_LOWERED)
                    ? CombatInputClient.chargeProgress(Minecraft.getInstance(), partialTick) : 0.0F;
            if (pose == CombatPose.LONGSWORD_SPIN_CHARGE) {
                progress = com.craisinlord.fightclub.client.LongswordSpinClient.progress(Minecraft.getInstance(), partialTick);
            }
            float shake = progress > 0.0F && progress < 1.0F ? fightClub$shake(player, partialTick) * config.tremble() * progress : 0.0F;
            if (pose == CombatPose.DAGGER_CHARGE) shake += fightClub$shake(player, partialTick) * 0.04F * CombatClientPoses.readyFlash(player, partialTick);
            float pop = progress >= 1.0F ? config.readyPop() : 0.0F;
            float amount = held;
            if (pose == CombatPose.DAGGER_CHARGE && player == Minecraft.getInstance().player) {
                amount *= 0.4F + 0.6F * fightClub$smooth(progress);
                if (!CombatInputClient.pounceReady(Minecraft.getInstance())) pop = 0.0F;
            }
            fightClub$apply(poseStack, side, config, amount, shake, pop);
        }
        if (dip > 0.0F) fightClub$apply(poseStack, side, CombatPoseConfig.get(CombatPose.RECOVERY_DIP), dip, 0.0F, 0.0F);
        if (bash > 0.0F) fightClub$apply(poseStack, side, CombatPoseConfig.get(CombatPose.SHIELD_BASH), bash, 0.0F, 0.0F);

        if (tridentRoll != 0.0F) {
            CombatModels.setModelRoll(side * tridentRoll, CombatModels.TRIDENT_POLE_X, CombatModels.TRIDENT_POLE_Z);
            try {
                fightClub$submitItem(state, mainHand, poseStack, collector, light);
            } finally {
                CombatModels.setModelRoll(0.0F, 0.0F, 0.0F);
            }
        } else {
            fightClub$submitItem(state, mainHand, poseStack, collector, light);
        }
        poseStack.popPose();
        ci.cancel();
    }

    @Unique
    private static void fightClub$submitItem(FirstPersonHandsAndItemsRenderState state, boolean mainHand, PoseStack poseStack,
                                             SubmitNodeCollector collector, int light) {
        (mainHand ? state.mainHandRenderState : state.offHandRenderState)
                .submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
    }

    @Unique
    private static void fightClub$daggerHeading(PoseStack poseStack, float side, Vector3f pos, Vector3f rot, Vector3f orient) {
        poseStack.translate(side * pos.x(), pos.y(), pos.z());
        poseStack.rotateDegrees(Axis.XP, rot.x());
        poseStack.rotateDegrees(Axis.YP, side * rot.y());
        poseStack.rotateDegrees(Axis.ZP, side * rot.z());
        poseStack.rotateDegrees(Axis.XP, orient.x());
        poseStack.rotateDegrees(Axis.YP, side * orient.y());
        poseStack.rotateDegrees(Axis.ZP, side * orient.z());
        poseStack.rotateDegrees(Axis.ZP, side * 45.0F);
        if (side < 0.0F) poseStack.rotateDegrees(Axis.YP, 180.0F);
        poseStack.rotate(HANDHELD_FIRST_PERSON_INVERSE);
        poseStack.translate(-side * 1.13F / 16.0F, -3.2F / 16.0F, -1.13F / 16.0F);
    }

    @Unique
    private static void fightClub$apply(PoseStack poseStack, float side, CombatPoseConfig.Pose pose,
                                        float amount, float shake, float pop) {
        Vector3f t = pose.translation();
        Vector3f r = pose.rotation();
        poseStack.translate(side * (t.x() * amount + shake), t.y() * amount + shake * 0.6F, (t.z() + pop) * amount);
        poseStack.rotateDegrees(Axis.XP, r.x() * amount);
        poseStack.rotateDegrees(Axis.YP, side * r.y() * amount);
        poseStack.rotateDegrees(Axis.ZP, side * r.z() * amount);
    }

    @Unique
    private static void fightClub$keyed(PoseStack poseStack, float side, CombatPoseConfig.Pose pose, float t, float amount) {
        if (pose.keys().isEmpty() || t <= 0.0F || t >= 1.0F || amount <= 0.0F) return;
        CombatPoseConfig.Segment seg = CombatPoseConfig.segment(pose.keys(), t);
        CombatPoseConfig.Key from = seg.from() < 0 ? null : pose.keys().get(seg.from());
        CombatPoseConfig.Key to = seg.to() < 0 ? null : pose.keys().get(seg.to());
        Vector3f tr = new Vector3f(from == null ? new Vector3f() : from.translation())
                .lerp(to == null ? new Vector3f() : to.translation(), seg.s()).mul(amount);
        Vector3f r = new Vector3f(from == null ? new Vector3f() : from.rotation())
                .lerp(to == null ? new Vector3f() : to.rotation(), seg.s()).mul(amount);
        Vector3f p = pose.pivot();
        poseStack.translate(side * (tr.x() + p.x()), tr.y() + p.y(), tr.z() + p.z());
        poseStack.rotateDegrees(Axis.YP, side * r.y());
        poseStack.rotateDegrees(Axis.XP, r.x());
        poseStack.rotateDegrees(Axis.ZP, side * r.z());
        poseStack.translate(-side * p.x(), -p.y(), -p.z());
        Vector3f o = new Vector3f(from == null ? new Vector3f() : from.orient())
                .lerp(to == null ? new Vector3f() : to.orient(), seg.s()).mul(amount);
        poseStack.rotateDegrees(Axis.YP, side * o.y());
        poseStack.rotateDegrees(Axis.XP, o.x());
        poseStack.rotateDegrees(Axis.ZP, side * o.z());
    }

    @Unique
    private static void fightClub$arc(PoseStack poseStack, float side, CombatPoseConfig.Pose pose, float t, float amount) {
        if (pose.keys().isEmpty() || t <= 0.0F || t >= 1.0F || amount <= 0.0F) return;
        CombatPoseConfig.Segment seg = CombatPoseConfig.segment(pose.keys(), t);
        CombatPoseConfig.Key from = seg.from() < 0 ? null : pose.keys().get(seg.from());
        CombatPoseConfig.Key to = seg.to() < 0 ? null : pose.keys().get(seg.to());
        Vector3f rest = pose.rotation();
        Vector3f a = from != null ? from.rotation() : fightClub$near(rest, to.rotation());
        Vector3f b = to != null ? to.rotation() : fightClub$near(rest, from.rotation());
        Vector3f heading = new Vector3f(a).lerp(b, seg.s());
        heading = fightClub$near(rest, heading).lerp(heading, amount);
        Vector3f hand = new Vector3f(from == null ? new Vector3f() : from.translation())
                .lerp(to == null ? new Vector3f() : to.translation(), seg.s()).mul(amount);
        Vector3f grip = pose.pivot();
        poseStack.translate(side * (hand.x() + grip.x()), hand.y() + grip.y(), hand.z() + grip.z());
        poseStack.rotateDegrees(Axis.YP, side * heading.y());
        poseStack.rotateDegrees(Axis.XP, heading.x());
        poseStack.rotateDegrees(Axis.ZP, side * heading.z());
        poseStack.rotateDegrees(Axis.ZP, -side * rest.z());
        poseStack.rotateDegrees(Axis.XP, -rest.x());
        poseStack.rotateDegrees(Axis.YP, -side * rest.y());
        poseStack.translate(-side * grip.x(), -grip.y(), -grip.z());
    }

    @Unique
    private static Vector3f fightClub$near(Vector3f rest, Vector3f ref) {
        return new Vector3f(rest.x() + 360.0F * Math.round((ref.x() - rest.x()) / 360.0F),
                rest.y() + 360.0F * Math.round((ref.y() - rest.y()) / 360.0F),
                rest.z() + 360.0F * Math.round((ref.z() - rest.z()) / 360.0F));
    }

    @Unique
    private static float fightClub$stabExtension(float t) {
        if (t <= 0.0F || t >= 1.0F) return 0.0F;
        float starting = net.minecraft.util.Ease.inOutSine(Mth.clamp(t / 0.05F, 0.0F, 1.0F));
        float ending = net.minecraft.util.Ease.inOutExpo(Mth.clamp((t - 0.4F) / 0.6F, 0.0F, 1.0F));
        return Mth.clamp(starting - ending, 0.0F, 1.0F);
    }

    @Unique
    private static float fightClub$shake(AbstractClientPlayer player, float partialTick) {
        return Mth.sin((player.tickCount + partialTick) * 2.7F);
    }

    @Unique
    private static float fightClub$punch(float t) {
        if (t <= 0.0F || t >= 1.0F) return 0.0F;
        if (t < 0.3F) {
            float u = t / 0.3F;
            return 1.0F - (1.0F - u) * (1.0F - u) * (1.0F - u);
        }
        return 1.0F - fightClub$smooth((t - 0.3F) / 0.7F);
    }

    @Unique
    private static float fightClub$smooth(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }
}
