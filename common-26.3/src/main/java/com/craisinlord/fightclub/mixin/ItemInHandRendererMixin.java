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

/**
 * First-person animation for Fight Club weapons (ported from 1.21.1). All values come from
 * {@code assets/fight_club/animations/poses.json} (reload with F3+T).
 *
 * <p>When nothing of ours is active, this returns immediately and vanilla, NeoForge's item hooks
 * and Better Combat render untouched. With Better Combat installed, attack swings are always
 * left to it; only our held poses and effects apply.
 *
 * <p>26.3: first-person hands moved from ItemInHandRenderer to FirstPersonHandsAndItemsRenderer,
 * which renders from an extracted render state; the item is submitted through the hand's
 * ItemStackRenderState instead of renderItem.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class ItemInHandRendererMixin {
    /**
     * Degrees the dagger's model is turned (in its own plane) while winding up a throw. Its
     * handheld sprite runs corner to corner; this stands the tip up the way the trident's and
     * spear's do, so the vanilla throw wind-up points it forward.
     */
    @Unique
    private static final float DAGGER_THROW_SPIN = 45.0F;

    @Shadow
    private void applyItemArmTransform(PoseStack poseStack, HumanoidArm arm, float inverseArmHeight) {}

    @Shadow
    private void applyItemArmAttackTransform(PoseStack poseStack, HumanoidArm arm, float attackValue) {}

    /**
     * Vanilla spears charge through vanilla item use, but Fight Club gives them the trident use
     * animation for right-click throws. While the use is a left-click charge, show vanilla's
     * SPEAR (kinetic charge) animation instead.
     */
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

        // Dagger throw wind-up: the same pull-back-and-shake vanilla uses for the trident (and the
        // spear), with the dagger turned so its tip leads like the spear's.
        if (usingThisHand && kind == WeaponKind.DAGGER && stack.getUseAnimation() == ItemUseAnimation.TRIDENT
                && player.getUseItemRemainingTicks() > 0) {
            poseStack.pushPose();
            applyItemArmTransform(poseStack, arm, equippedProgress);
            poseStack.translate(side * -0.5F, 0.7F, 0.1F);
            poseStack.rotateDegrees(Axis.XP, -55.0F);
            poseStack.rotateDegrees(Axis.YP, side * 35.3F);
            poseStack.rotateDegrees(Axis.ZP, side * -9.785F);
            float timeHeld = stack.getUseDuration(player) - (player.getUseItemRemainingTicks() - partialTick + 1.0F);
            float power = Math.min(1.0F, timeHeld / 10.0F);
            if (power > 0.1F) poseStack.translate(0.0F, Mth.sin((timeHeld - 0.1F) * 1.3F) * (power - 0.1F) * 0.004F, 0.0F);
            poseStack.translate(0.0F, 0.0F, power * 0.2F);
            poseStack.scale(1.0F, 1.0F, 1.0F + power * 0.2F);
            poseStack.rotateDegrees(Axis.YN, side * 45.0F);
            fightClub$renderFirstPerson(state, mainHand, poseStack, collector, light, DAGGER_THROW_SPIN);
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
        // Longsword spin (right-click, so it also plays with Better Combat): the blade sweeps across the screen.
        float spin = view != null && mainHand && kind == WeaponKind.LONGSWORD ? view.spin() : 0.0F;

        // Our own attack swings only exist without Better Combat; it animates attacks itself.
        CombatPose swingStyle = CombatPose.NONE;
        if (mainHand && !BetterCombatCompat.isLoaded()) {
            if (kind == WeaponKind.DAGGER) swingStyle = CombatPose.JAB_DAGGER;
            else if (kind == WeaponKind.LONGSWORD) swingStyle = CombatPose.HEAVY_LONGSWORD;
        }
        boolean tridentStab = kind == WeaponKind.TRIDENT && mainHand && !BetterCombatCompat.isLoaded();
        boolean customSwing = (swingStyle != CombatPose.NONE || tridentStab) && swingProgress > 0.0F;

        if (held <= 0.0F && recoil <= 0.0F && bash <= 0.0F && dip <= 0.0F && spin <= 0.0F && !customSwing) return;
        // A vanilla use animation (spear charge, trident/spear throw wind-up, shield raise) stays
        // vanilla unless one of our effects is layered on it.
        if (usingThisHand && pose == CombatPose.NONE && bash <= 0.0F) return;
        // Vanilla spears keep vanilla's stab swing; only the recovery dip is layered on below.
        // The trident (without Better Combat) pokes with the same vanilla STAB.
        boolean vanillaStab = (kind == WeaponKind.SPEAR || tridentStab) && held <= 0.0F;

        poseStack.pushPose();
        float keep = 1.0F - held;
        boolean jab = swingStyle == CombatPose.JAB_DAGGER || swingStyle == CombatPose.JAB_SPEAR;
        boolean sweep = swingStyle == CombatPose.HEAVY_LONGSWORD;
        if (!usingThisHand && !jab && !sweep && !vanillaStab) {
            // Vanilla's sideways swing offset, faded out as a held pose takes over.
            float root = Mth.sqrt(swingProgress);
            poseStack.translate(side * -0.4F * Mth.sin(root * Mth.PI) * keep,
                    0.2F * Mth.sin(root * Mth.TWO_PI) * keep,
                    -0.2F * Mth.sin(swingProgress * Mth.PI) * keep);
        }
        applyItemArmTransform(poseStack, arm, equippedProgress);

        if (!usingThisHand && keep > 0.0F) {
            if (vanillaStab) {
                net.minecraft.client.model.effects.SpearAnimations.firstPersonAttack(swingProgress, poseStack, (int) side, arm);
            } else if (jab) {
                // Dagger tap: keyframed tip-first stab when the pose has keys, else the plain forward punch.
                CombatPoseConfig.Pose jabPose = CombatPoseConfig.get(swingStyle);
                if (!jabPose.keys().isEmpty()) fightClub$keyed(poseStack, side, jabPose, swingProgress, keep);
                else fightClub$apply(poseStack, side, jabPose, fightClub$punch(swingProgress) * keep, 0.0F, 0.0F);
            } else if (sweep) {
                // Consecutive longsword swings alternate: right to left, then the left-to-right return cut.
                fightClub$arc(poseStack, side, CombatPoseConfig.get(CombatClientPoses.backhand(player)
                        ? CombatPose.HEAVY_LONGSWORD_BACK : swingStyle), swingProgress, keep);
            } else {
                if (held <= 0.0F) applyItemArmAttackTransform(poseStack, arm, swingProgress);
            }
        }
        if (spin > 0.0F) fightClub$arc(poseStack, side, CombatPoseConfig.get(CombatPose.LONGSWORD_SPIN), spin, 1.0F);

        // Block recoil goes on before the held pose, so it kicks in screen space (down and toward
        // the camera) instead of along the axes of the turned-flat sword.
        if (recoil > 0.0F) fightClub$apply(poseStack, side, CombatPoseConfig.get(CombatPose.BLOCK_RECOIL), recoil, 0.0F, 0.0F);
        if (held > 0.0F) {
            CombatPoseConfig.Pose config = CombatPoseConfig.get(pose);
            float progress = (pose == CombatPose.DAGGER_CHARGE || pose == CombatPose.SPEAR_LOWERED || pose == CombatPose.TRIDENT_LOWERED)
                    ? CombatInputClient.chargeProgress(Minecraft.getInstance(), partialTick) : 0.0F;
            if (pose == CombatPose.LONGSWORD_SPIN_CHARGE) {
                progress = com.craisinlord.fightclub.client.LongswordSpinClient.progress(Minecraft.getInstance(), partialTick);
            }
            float shake = progress > 0.0F && progress < 1.0F ? fightClub$shake(player, partialTick) * config.tremble() * progress : 0.0F;
            // Dagger ready cue: a sharp shake the moment the charge fills (CombatClientPoses.readyFlash).
            if (pose == CombatPose.DAGGER_CHARGE) shake += fightClub$shake(player, partialTick) * 0.04F * CombatClientPoses.readyFlash(player, partialTick);
            float pop = progress >= 1.0F ? config.readyPop() : 0.0F;
            float amount = held;
            if (pose == CombatPose.DAGGER_CHARGE && player == Minecraft.getInstance().player) {
                // Draw back further as the charge fills, and only snap "ready" with a target in reach.
                amount *= 0.4F + 0.6F * fightClub$smooth(progress);
                if (!CombatInputClient.pounceReady(Minecraft.getInstance())) pop = 0.0F;
            }
            fightClub$apply(poseStack, side, config, amount, shake, pop);
        }
        if (dip > 0.0F) fightClub$apply(poseStack, side, CombatPoseConfig.get(CombatPose.RECOVERY_DIP), dip, 0.0F, 0.0F);
        if (bash > 0.0F) fightClub$apply(poseStack, side, CombatPoseConfig.get(CombatPose.SHIELD_BASH), bash, 0.0F, 0.0F);

        fightClub$submitItem(state, mainHand, poseStack, collector, light);
        poseStack.popPose();
        ci.cancel();
    }

    @Unique
    private static void fightClub$renderFirstPerson(FirstPersonHandsAndItemsRenderState state, boolean mainHand, PoseStack poseStack,
                                                    SubmitNodeCollector collector, int light, float modelSpin) {
        CombatModels.setModelSpin(modelSpin);
        try {
            fightClub$submitItem(state, mainHand, poseStack, collector, light);
        } finally {
            CombatModels.setModelSpin(0.0F);
        }
    }

    @Unique
    private static void fightClub$submitItem(FirstPersonHandsAndItemsRenderState state, boolean mainHand, PoseStack poseStack,
                                             SubmitNodeCollector collector, int light) {
        (mainHand ? state.mainHandRenderState : state.offHandRenderState)
                .submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
    }

    /** Applies a pose scaled by amount; X translation and Y/Z rotation mirror for the left hand. */
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

    /**
     * Keyframed one-shot motion (dagger jab): the item offset eased from key to key
     * over progress t, starting from and settling back to no offset. Each key is a translation and a
     * rotation around Y (yaw), then X (pitch), then Z (roll); X translation and Y/Z mirror for the left hand.
     */
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

    /**
     * Longsword swing and spin: the blade turns round the grip like a clock hand while the hand
     * travels a short path, all in camera space (so it looks the same at any view pitch). Per key,
     * {@code translation} moves the hand from the idle hold (x right, y up, z toward the camera) and
     * {@code rotation} is the blade's heading as [pitch, yaw, roll] in degrees: yaw 0 points straight
     * ahead and +90 to the left, pitch raises the tip, roll twists the flat round the blade (0 = flat
     * facing up). The pose's {@code rotation} is the idle hold's heading (where every swing starts and
     * ends) and its {@code pivot} the grip, relative to vanilla's first-person hand. The left hand mirrors.
     */
    @Unique
    private static void fightClub$arc(PoseStack poseStack, float side, CombatPoseConfig.Pose pose, float t, float amount) {
        if (pose.keys().isEmpty() || t <= 0.0F || t >= 1.0F || amount <= 0.0F) return;
        CombatPoseConfig.Segment seg = CombatPoseConfig.segment(pose.keys(), t);
        CombatPoseConfig.Key from = seg.from() < 0 ? null : pose.keys().get(seg.from());
        CombatPoseConfig.Key to = seg.to() < 0 ? null : pose.keys().get(seg.to());
        Vector3f rest = pose.rotation();
        // Before the first key and after the last, blend with the idle heading (taken the short way round).
        Vector3f a = from != null ? from.rotation() : fightClub$near(rest, to.rotation());
        Vector3f b = to != null ? to.rotation() : fightClub$near(rest, from.rotation());
        Vector3f heading = new Vector3f(a).lerp(b, seg.s());
        heading = fightClub$near(rest, heading).lerp(heading, amount);
        Vector3f hand = new Vector3f(from == null ? new Vector3f() : from.translation())
                .lerp(to == null ? new Vector3f() : to.translation(), seg.s()).mul(amount);
        Vector3f grip = pose.pivot();
        // Hand along its path, then the blade swung round the grip to the heading. The idle heading
        // is undone first, so the item's own model/display angles drop out.
        poseStack.translate(side * (hand.x() + grip.x()), hand.y() + grip.y(), hand.z() + grip.z());
        poseStack.rotateDegrees(Axis.YP, side * heading.y());
        poseStack.rotateDegrees(Axis.XP, heading.x());
        poseStack.rotateDegrees(Axis.ZP, side * heading.z());
        poseStack.rotateDegrees(Axis.ZP, -side * rest.z());
        poseStack.rotateDegrees(Axis.XP, -rest.x());
        poseStack.rotateDegrees(Axis.YP, -side * rest.y());
        poseStack.translate(-side * grip.x(), -grip.y(), -grip.z());
    }

    /** The idle heading with each angle shifted by whole turns to lie nearest {@code ref}. */
    @Unique
    private static Vector3f fightClub$near(Vector3f rest, Vector3f ref) {
        return new Vector3f(rest.x() + 360.0F * Math.round((ref.x() - rest.x()) / 360.0F),
                rest.y() + 360.0F * Math.round((ref.y() - rest.y()) / 360.0F),
                rest.z() + 360.0F * Math.round((ref.z() - rest.z()) / 360.0F));
    }

    @Unique
    private static float fightClub$shake(AbstractClientPlayer player, float partialTick) {
        return Mth.sin((player.tickCount + partialTick) * 2.7F);
    }

    /** Jab curve: fast thrust out over the first 30% of the swing, soft return after. */
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
