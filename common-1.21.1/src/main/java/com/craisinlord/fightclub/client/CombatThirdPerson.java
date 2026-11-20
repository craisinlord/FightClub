package com.craisinlord.fightclub.client;

import com.craisinlord.fightclub.combat.WeaponKind;
import com.craisinlord.fightclub.compat.BetterCombatCompat;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import org.joml.Vector3f;

/**
 * Third-person arm poses on the player model, run after vanilla has posed the arms. Uses the same
 * per-player state as first person, so other players see your sword block, charges, mace wind-up
 * and bashes. With Better Combat installed, attack swings and the longsword grip are left to it.
 *
 * <p>Poses keep some life: arms tilt with the player's look pitch ({@code follow_head}) and keep
 * part of vanilla's walking swing ({@code walk_bob}), so held poses don't freeze while moving.
 */
public final class CombatThirdPerson {
    private CombatThirdPerson() {}

    public static void apply(HumanoidModel<?> model, Player player, float limbSwing, float limbSwingAmount, float ageInTicks) {
        // PlayerRenderer.renderHand calls setupAnim with all zeros for the first-person bare arm; leave that alone.
        if (limbSwing == 0.0F && limbSwingAmount == 0.0F && ageInTicks == 0.0F) return;
        if (!com.craisinlord.fightclub.CombatConfig.enabled(com.craisinlord.fightclub.CombatConfig.ANIMATIONS)) return;

        float partialTick = Mth.clamp(ageInTicks - player.tickCount, 0.0F, 1.0F);
        boolean rightHanded = player.getMainArm() == HumanoidArm.RIGHT;
        ModelPart main = rightHanded ? model.rightArm : model.leftArm;
        ModelPart off = rightHanded ? model.leftArm : model.rightArm;
        // Config values are written for a right-handed player; mirror Y/Z for left-handers.
        float mirror = rightHanded ? 1.0F : -1.0F;
        float headPitch = model.head.xRot;
        // Vanilla's held-item walking swing for the main arm; two-handed poses move both arms together.
        float bob = Mth.cos(limbSwing * 0.6662F + (rightHanded ? Mth.PI : 0.0F)) * limbSwingAmount * 0.5F;

        CombatClientPoses.View view = CombatClientPoses.view(player, partialTick);
        float held = view == null ? 0.0F : view.amount();
        WeaponKind kind = WeaponKind.of(player.getMainHandItem());
        boolean offhandEmpty = player.getOffhandItem().isEmpty();
        boolean betterCombat = BetterCombatCompat.isLoaded();
        float attack = model.attackTime;
        if (CombatClientPoses.debugSwing(player) >= 0.0F) attack = CombatClientPoses.debugSwing(player);

        // Two-handed longsword grip: the main arm holds the pose between swings, and the off hand
        // stays on the hilt by following the main arm's pitch, including during swings.
        if (!betterCombat && kind == WeaponKind.LONGSWORD && offhandEmpty && !player.isUsingItem() && held <= 0.0F) {
            CombatPoseConfig.ArmPose grip = CombatPoseConfig.arms(CombatPose.LONGSWORD_GRIP);
            if (grip != null && grip.main() != null) {
                float hold = attack > 0.0F ? 1.0F - Mth.sin(attack * Mth.PI) : 1.0F;
                lerp(main, grip.main(), mirror, hold);
                main.xRot += (headPitch * grip.followHead() + bob * grip.walkBob()) * hold;
                if (grip.off() != null) {
                    off.xRot = main.xRot + Mth.DEG_TO_RAD * (grip.off().x() - grip.main().x());
                    off.yRot = mirror * Mth.DEG_TO_RAD * grip.off().y();
                    off.zRot = mirror * Mth.DEG_TO_RAD * grip.off().z();
                }
            }
        }

        // Spears and tridents: a straight poke modelled on vanilla 26.x's spear STAB.
        if (!betterCombat && attack > 0.0F && held <= 0.0F && kind.thrusts()) {
            StabAnimation.thirdPersonArm(main, model.body, attack);
        }

        // Forward thrust instead of the sideways chop for daggers.
        if (!betterCombat && attack > 0.0F && held <= 0.0F && kind == WeaponKind.DAGGER) {
            CombatPoseConfig.ArmPose jab = CombatPoseConfig.arms(CombatPose.JAB_DAGGER);
            float punch = CombatClientPoses.punch(attack);
            if (jab != null) {
                pose(main, off, jab, mirror, punch, offhandEmpty, headPitch, 0.0F);
                model.body.yRot *= 1.0F - punch;
            }
        }

        // Longsword swing (without Better Combat): one wide horizontal cut, the sword arm sweeping
        // from out on its side across the body; the off hand rides along on the hilt.
        if (!betterCombat && attack > 0.0F && held <= 0.0F && kind == WeaponKind.LONGSWORD) {
            CombatPoseConfig.ArmPose arc = CombatPoseConfig.arms(
                    CombatClientPoses.backhand(player) ? CombatPose.HEAVY_LONGSWORD_BACK : CombatPose.HEAVY_LONGSWORD);
            if (arc != null) keyedArms(model, main, off, arc, mirror, attack, offhandEmpty, headPitch);
        }

        if (view == null) return;

        // Longsword spin: arms out with the blade while the whole body turns (LongswordSpinClient.bodySpin).
        if (view.spin() > 0.0F && kind == WeaponKind.LONGSWORD) {
            CombatPoseConfig.ArmPose spin = CombatPoseConfig.arms(CombatPose.LONGSWORD_SPIN);
            if (spin != null) keyedArms(model, main, off, spin, mirror, view.spin(), offhandEmpty, headPitch);
        }

        if (held > 0.0F && view.pose() == CombatPose.SPEAR_LOWERED) {
            // Spear charge: vanilla's newer spear hold-out pose, one-handed. While blending out
            // after release there's no charge time left, so ease back from the base pose.
            float time = Math.max(0.0F, CombatClientPoses.spearChargeTicks(player, partialTick));
            SpearChargeAnimation.thirdPersonArm(main, model.head, rightHanded, player, time, held);
        } else if (held > 0.0F && view.pose() != CombatPose.NONE) {
            CombatPoseConfig.ArmPose arms = CombatPoseConfig.arms(view.pose());
            if (arms != null) pose(main, off, arms, mirror, held, offhandEmpty, headPitch, bob);
            if (view.pose() == CombatPose.DAGGER_CHARGE) {
                // Dagger ready cue: a quick shake of the drawn-back arm the moment the charge fills.
                float flash = CombatClientPoses.readyFlash(player, partialTick) * held;
                float jitter = Mth.sin((player.tickCount + partialTick) * 2.7F);
                main.xRot += flash * 0.15F * jitter;
                main.zRot += mirror * flash * 0.1F * jitter;
            }
        }

        if (view.bash() > 0.0F) {
            CombatPoseConfig.ArmPose bash = CombatPoseConfig.arms(CombatPose.SHIELD_BASH);
            ModelPart shieldArm = player.getOffhandItem().is(Items.SHIELD) ? off
                    : player.getMainHandItem().is(Items.SHIELD) ? main : null;
            if (bash != null && bash.off() != null && shieldArm != null) {
                // shield_bash values are written for the left arm.
                lerp(shieldArm, bash.off(), shieldArm == model.leftArm ? 1.0F : -1.0F, view.bash());
                shieldArm.xRot += headPitch * bash.followHead() * view.bash();
            }
        }
    }

    /**
     * Extra rotation for the item in this hand, in degrees [pitch, yaw, roll] (roll twists it around
     * the arm's length), mirrored for the left arm; null when there is none. Covers the held pose's
     * item turn (sword_block's flat, outward blade) and the longsword swing / spin keyframes. Called by
     * ItemInHandLayerMixin just before the item draws.
     */
    public static Vector3f itemRotation(net.minecraft.world.entity.LivingEntity entity, HumanoidArm arm, float partialTick) {
        if (!com.craisinlord.fightclub.CombatConfig.enabled(com.craisinlord.fightclub.CombatConfig.ANIMATIONS)
                || !(entity instanceof Player player) || arm != player.getMainArm()) return null;
        float mirror = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        CombatClientPoses.View view = CombatClientPoses.view(player, partialTick);
        float held = view == null ? 0.0F : view.amount();
        boolean longsword = WeaponKind.of(player.getMainHandItem()) == WeaponKind.LONGSWORD;
        Vector3f item = null;
        if (longsword && view != null && view.spin() > 0.0F) {
            CombatPoseConfig.ArmPose spin = CombatPoseConfig.arms(CombatPose.LONGSWORD_SPIN);
            if (spin != null) item = keyedItem(spin, view.spin());
        } else if (longsword && held <= 0.0F && !BetterCombatCompat.isLoaded()) {
            float attack = CombatClientPoses.debugSwing(player) >= 0.0F ? CombatClientPoses.debugSwing(player) : player.getAttackAnim(partialTick);
            CombatPoseConfig.ArmPose arc = CombatPoseConfig.arms(
                    CombatClientPoses.backhand(player) ? CombatPose.HEAVY_LONGSWORD_BACK : CombatPose.HEAVY_LONGSWORD);
            if (attack > 0.0F && arc != null) item = keyedItem(arc, attack);
        } else if (held > 0.0F && view.pose() != CombatPose.NONE) {
            CombatPoseConfig.ArmPose arms = CombatPoseConfig.arms(view.pose());
            if (arms != null && arms.item() != null) item = new Vector3f(arms.item()).mul(held);
        }
        if (item == null || item.lengthSquared() == 0.0F) return null;
        return new Vector3f(item.x(), mirror * item.y(), mirror * item.z());
    }

    private static Vector3f keyedItem(CombatPoseConfig.ArmPose pose, float t) {
        if (pose.keys().isEmpty() || t <= 0.0F || t >= 1.0F) return null;
        CombatPoseConfig.Segment seg = CombatPoseConfig.segment(pose.keys(), t);
        Vector3f from = seg.from() < 0 ? new Vector3f() : pose.keys().get(seg.from()).item();
        Vector3f to = seg.to() < 0 ? new Vector3f() : pose.keys().get(seg.to()).item();
        return new Vector3f(from).lerp(to, seg.s());
    }

    /**
     * Keyframed two-arm motion over progress t (longsword swing and spin), in the style of Better
     * Combat's keyframed animations: each key gives absolute arm angles and an upper-body twist,
     * eased from the previous key; before the first key and after the last it blends with whatever
     * pose the arms already had (vanilla or the longsword grip). The off arm follows only with an
     * empty offhand.
     */
    private static void keyedArms(HumanoidModel<?> model, ModelPart main, ModelPart off, CombatPoseConfig.ArmPose pose,
                                  float mirror, float t, boolean offhandEmpty, float headPitch) {
        java.util.List<CombatPoseConfig.ArmKey> keys = pose.keys();
        if (keys.isEmpty() || t <= 0.0F || t >= 1.0F) return;
        CombatPoseConfig.Segment seg = CombatPoseConfig.segment(keys, t);
        CombatPoseConfig.ArmKey from = seg.from() < 0 ? null : keys.get(seg.from());
        CombatPoseConfig.ArmKey to = seg.to() < 0 ? null : keys.get(seg.to());
        float s = seg.s();
        // How much of the keyed motion shows (fades in from / out to the existing pose at the ends).
        float weight = from == null ? s : to == null ? 1.0F - s : 1.0F;
        float follow = headPitch * pose.followHead() * weight;
        // Key angles are relative to the torso: take vanilla's own attack twist out of the arms first.
        model.rightArm.yRot -= model.body.yRot;
        model.leftArm.yRot -= model.body.yRot;
        keyedPart(main, from == null ? null : from.main(), to == null ? null : to.main(), s, mirror);
        main.xRot += follow;
        Vector3f offFrom = from == null ? null : from.off(), offTo = to == null ? null : to.off();
        if (offhandEmpty && (offFrom != null || offTo != null)) {
            keyedPart(off, offFrom, offTo, s, mirror);
            off.xRot += follow;
        }
        // Upper-body twist: turn the torso and carry both shoulders (and arm yaw) around with it, the
        // way vanilla's own attack twist does.
        float twist = (from == null ? 0.0F : from.body()) * (1.0F - s) + (to == null ? 0.0F : to.body()) * s;
        float bodyYaw = -mirror * Mth.DEG_TO_RAD * twist;
        model.body.yRot = bodyYaw;
        model.rightArm.z = Mth.sin(bodyYaw) * 5.0F;
        model.rightArm.x = -Mth.cos(bodyYaw) * 5.0F;
        model.leftArm.z = -Mth.sin(bodyYaw) * 5.0F;
        model.leftArm.x = Mth.cos(bodyYaw) * 5.0F;
        model.rightArm.yRot += bodyYaw;
        model.leftArm.yRot += bodyYaw;
    }

    /** Sets a part between two keyed angles (null = the part's current angles, i.e. rest). */
    private static void keyedPart(ModelPart part, Vector3f from, Vector3f to, float s, float mirror) {
        if (from != null && to != null) {
            lerp(part, new Vector3f(from).lerp(to, s), mirror, 1.0F);
        } else if (to != null) {
            lerp(part, to, mirror, s);
        } else if (from != null) {
            lerp(part, from, mirror, 1.0F - s);
        }
    }

    /** Applies a two-arm pose with head follow and walk bob, scaled by amount. */
    private static void pose(ModelPart main, ModelPart off, CombatPoseConfig.ArmPose arms, float mirror, float amount,
                             boolean offhandEmpty, float headPitch, float bob) {
        float extra = (headPitch * arms.followHead() + bob * arms.walkBob()) * amount;
        if (arms.main() != null) {
            lerp(main, arms.main(), mirror, amount);
            main.xRot += extra;
        }
        if (arms.off() != null && offhandEmpty) {
            lerp(off, arms.off(), mirror, amount);
            off.xRot += extra;
        }
    }

    private static void lerp(ModelPart part, Vector3f degrees, float mirror, float amount) {
        if (amount <= 0.0F) return;
        part.xRot = Mth.lerp(amount, part.xRot, Mth.DEG_TO_RAD * degrees.x());
        part.yRot = Mth.lerp(amount, part.yRot, mirror * Mth.DEG_TO_RAD * degrees.y());
        part.zRot = Mth.lerp(amount, part.zRot, mirror * Mth.DEG_TO_RAD * degrees.z());
    }
}
