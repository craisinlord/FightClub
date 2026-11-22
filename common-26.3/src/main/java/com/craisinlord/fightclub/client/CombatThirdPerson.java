package com.craisinlord.fightclub.client;

import com.craisinlord.fightclub.CombatConfig;
import com.craisinlord.fightclub.combat.WeaponKind;
import com.craisinlord.fightclub.compat.BetterCombatCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class CombatThirdPerson {
    private CombatThirdPerson() {}

    public static void apply(HumanoidModel<?> model, AvatarRenderState state) {
        if (!CombatConfig.enabled(CombatConfig.ANIMATIONS)) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || !(client.level.getEntity(state.id) instanceof Player player)) return;

        float partialTick = Mth.clamp(state.ageInTicks - player.tickCount, 0.0F, 1.0F);
        boolean rightHanded = state.mainArm == HumanoidArm.RIGHT;
        ModelPart main = rightHanded ? model.rightArm : model.leftArm;
        ModelPart off = rightHanded ? model.leftArm : model.rightArm;
        float mirror = rightHanded ? 1.0F : -1.0F;
        float headPitch = model.head.xRot;
        float bob = Mth.cos(state.walkAnimationPos * 0.6662F + (rightHanded ? Mth.PI : 0.0F)) * state.walkAnimationSpeed * 0.5F;

        CombatClientPoses.View view = CombatClientPoses.view(player, partialTick);
        float held = view == null ? 0.0F : view.amount();
        WeaponKind kind = WeaponKind.of(player.getMainHandItem());
        boolean offhandEmpty = player.getOffhandItem().isEmpty();
        boolean betterCombat = BetterCombatCompat.isLoaded();
        float attack = state.swingAnimation;
        float longswordSwing = !betterCombat && kind == WeaponKind.LONGSWORD ? CombatClientPoses.thirdPersonSwing(player, partialTick) : 0.0F;

        boolean twoHanded = grip(kind, offhandEmpty, player, held, betterCombat);
        if (twoHanded) {
            CombatPoseConfig.ArmPose grip = CombatPoseConfig.arms(CombatPose.LONGSWORD_GRIP);
            if (grip != null && grip.main() != null) {
                float lean = state.isCrouching ? 0.4F : 0.0F;
                lerp(main, grip.main(), mirror, 1.0F);
                main.yRot += model.body.yRot;
                pitchAboutShoulders(main, model.body.yRot, headPitch * grip.followHead() + lean);
            }
        }

        if (!betterCombat && attack > 0.0F && held <= 0.0F && kind == WeaponKind.DAGGER) {
            CombatPoseConfig.ArmPose jab = CombatPoseConfig.arms(CombatPose.JAB_DAGGER);
            float punch = CombatClientPoses.punch(attack);
            if (jab != null) {
                pose(main, off, jab, mirror, punch, offhandEmpty, headPitch, 0.0F);
                model.body.yRot *= 1.0F - punch;
            }
        }

        float maceSwing = !betterCombat && kind == WeaponKind.MACE ? CombatClientPoses.thirdPersonSwing(player, partialTick) : 0.0F;
        if (maceSwing > 0.0F && held <= 0.0F) {
            CombatPoseConfig.ArmPose slam = CombatPoseConfig.arms(CombatPose.HEAVY_MACE);
            if (slam != null) keyedArms(model, main, off, slam, mirror, maceSwing, offhandEmpty, headPitch, false);
        }

        if (longswordSwing > 0.0F && held <= 0.0F) {
            CombatPoseConfig.ArmPose arc = CombatPoseConfig.arms(
                    CombatClientPoses.backhand(player) ? CombatPose.HEAVY_LONGSWORD_BACK : CombatPose.HEAVY_LONGSWORD);
            if (arc != null) keyedArms(model, main, off, arc, mirror, longswordSwing, offhandEmpty, headPitch, twoHanded);
        }

        boolean hiltPoses = kind == WeaponKind.LONGSWORD && offhandEmpty;
        float onHilt = twoHanded ? 1.0F : 0.0F;

        if (view == null) {
            if (onHilt > 0.0F) holdHilt(main, off, player, null, partialTick, betterCombat, mirror, rightHanded, onHilt);
            return;
        }

        if (view.spin() > 0.0F && kind == WeaponKind.LONGSWORD) {
            CombatPoseConfig.ArmPose spin = CombatPoseConfig.arms(CombatPose.LONGSWORD_SPIN);
            if (spin != null) {
                float weight = keyedArms(model, main, off, spin, mirror, view.spin(), offhandEmpty, headPitch, hiltPoses);
                if (hiltPoses) onHilt = Math.max(onHilt, weight);
            }
        }

        if (held > 0.0F && view.pose() != CombatPose.NONE) {
            CombatPoseConfig.ArmPose arms = CombatPoseConfig.arms(view.pose());
            if (arms != null && hiltPoses && view.pose() == CombatPose.LONGSWORD_SPIN_CHARGE && !arms.keys().isEmpty()) {
                holdKey(model, main, arms.keys().get(0), mirror, held, (headPitch * arms.followHead() + bob * arms.walkBob()) * held);
                onHilt = Math.max(onHilt, held);
            } else if (arms != null) {
                pose(main, off, arms, mirror, held, offhandEmpty, headPitch, bob);
            }
            if (view.pose() == CombatPose.DAGGER_CHARGE) {
                float flash = CombatClientPoses.readyFlash(player, partialTick) * held;
                float jitter = Mth.sin((player.tickCount + partialTick) * 2.7F);
                main.xRot += flash * 0.15F * jitter;
                main.zRot += mirror * flash * 0.1F * jitter;
            }
        }

        if (view.bash() > 0.0F) {
            CombatPoseConfig.ArmPose bash = CombatPoseConfig.arms(CombatPose.SHIELD_BASH);
            ModelPart shieldArm = com.craisinlord.fightclub.combat.CombatWeapons.isShield(player.getOffhandItem()) ? off
                    : com.craisinlord.fightclub.combat.CombatWeapons.isShield(player.getMainHandItem()) ? main : null;
            if (bash != null && bash.off() != null && shieldArm != null) {
                lerp(shieldArm, bash.off(), shieldArm == model.leftArm ? 1.0F : -1.0F, view.bash());
                shieldArm.xRot += headPitch * bash.followHead() * view.bash();
            }
        }

        if (onHilt > 0.0F) holdHilt(main, off, player, view, partialTick, betterCombat, mirror, rightHanded, onHilt);
    }

    private static void holdHilt(ModelPart main, ModelPart off, Player player, CombatClientPoses.View view, float partialTick,
                                 boolean betterCombat, float mirror, boolean rightHanded, float amount) {
        Quaternionf turn = longswordTurn(player, view, partialTick, betterCombat);
        if (turn != null) offHandOnHilt(main, off, mirrorTurn(turn, mirror), rightHanded, amount);
    }

    public record ItemTurn(Quaternionf rotation, boolean atFist) {}

    public static final float FIST_PIVOT = -2.0F / 16.0F;

    public static ItemTurn itemTurn(net.minecraft.client.renderer.entity.state.ArmedEntityRenderState state, HumanoidArm arm) {
        if (!CombatConfig.enabled(CombatConfig.ANIMATIONS) || !(state instanceof AvatarRenderState avatar) || arm != avatar.mainArm) return null;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || !(client.level.getEntity(avatar.id) instanceof Player player)) return null;
        float mirror = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        float partialTick = Mth.clamp(avatar.ageInTicks - player.tickCount, 0.0F, 1.0F);
        CombatClientPoses.View view = CombatClientPoses.view(player, partialTick);
        float held = view == null ? 0.0F : view.amount();
        if (WeaponKind.of(player.getMainHandItem()) == WeaponKind.LONGSWORD) {
            Quaternionf turn = longswordTurn(player, view, partialTick, BetterCombatCompat.isLoaded());
            if (turn != null) return turn.equals(new Quaternionf(), 1.0E-6F) ? null : new ItemTurn(mirrorTurn(turn, mirror), true);
        }
        if (WeaponKind.of(player.getMainHandItem()) == WeaponKind.MACE && !BetterCombatCompat.isLoaded() && held <= 0.0F) {
            float swing = CombatClientPoses.thirdPersonSwing(player, partialTick);
            if (swing > 0.0F) {
                Quaternionf turn = keyedTurn(CombatPoseConfig.arms(CombatPose.HEAVY_MACE), swing, new Quaternionf());
                return new ItemTurn(mirrorTurn(turn, mirror), true);
            }
        }
        if (held <= 0.0F || view.pose() == CombatPose.NONE) return null;
        CombatPoseConfig.ArmPose arms = CombatPoseConfig.arms(view.pose());
        if (arms == null || arms.item() == null || arms.item().lengthSquared() == 0.0F) return null;
        Vector3f item = new Vector3f(arms.item()).mul(held);
        return new ItemTurn(turn(new Vector3f(item.x(), mirror * item.y(), mirror * item.z())), false);
    }

    private static Quaternionf longswordTurn(Player player, CombatClientPoses.View view, float partialTick, boolean betterCombat) {
        float held = view == null ? 0.0F : view.amount();
        float spin = view == null ? 0.0F : view.spin();
        CombatPoseConfig.ArmPose charge = held > 0.0F && view.pose() == CombatPose.LONGSWORD_SPIN_CHARGE
                ? CombatPoseConfig.arms(CombatPose.LONGSWORD_SPIN_CHARGE) : null;
        Vector3f chargeItem = charge != null && !charge.keys().isEmpty() ? charge.keys().get(0).item() : null;
        if (spin <= 0.0F && chargeItem == null && (held > 0.0F || betterCombat)) return null;
        CombatPoseConfig.ArmPose gripPose = !betterCombat && player.getOffhandItem().isEmpty()
                ? CombatPoseConfig.arms(CombatPose.LONGSWORD_GRIP) : null;
        Quaternionf rest = turn(gripPose != null && gripPose.item() != null ? gripPose.item() : new Vector3f());
        Quaternionf q;
        if (spin > 0.0F) {
            q = keyedTurn(CombatPoseConfig.arms(CombatPose.LONGSWORD_SPIN), spin, rest);
        } else if (held <= 0.0F) {
            q = keyedTurn(CombatPoseConfig.arms(CombatClientPoses.backhand(player) ? CombatPose.HEAVY_LONGSWORD_BACK : CombatPose.HEAVY_LONGSWORD),
                    CombatClientPoses.thirdPersonSwing(player, partialTick), rest);
        } else {
            q = rest;
        }
        if (chargeItem != null) q.slerp(turn(chargeItem), held);
        return q;
    }

    private static Quaternionf keyedTurn(CombatPoseConfig.ArmPose pose, float t, Quaternionf rest) {
        if (pose == null || pose.keys().isEmpty() || t <= 0.0F || t >= 1.0F) return new Quaternionf(rest);
        CombatPoseConfig.Segment seg = CombatPoseConfig.segment(pose.keys(), t);
        Vector3f from = seg.from() < 0 ? null : pose.keys().get(seg.from()).item();
        Vector3f to = seg.to() < 0 ? null : pose.keys().get(seg.to()).item();
        Quaternionf q = from == null ? new Quaternionf(rest) : turn(from);
        return q.slerp(to == null ? rest : turn(to), seg.s());
    }

    private static Quaternionf turn(Vector3f degrees) {
        return new Quaternionf().rotationZ(Mth.DEG_TO_RAD * degrees.z())
                .rotateX(Mth.DEG_TO_RAD * degrees.x()).rotateY(Mth.DEG_TO_RAD * degrees.y());
    }

    private static Quaternionf mirrorTurn(Quaternionf turn, float mirror) {
        return new Quaternionf(turn.x(), mirror * turn.y(), mirror * turn.z(), turn.w());
    }

    private static final Vector3f HILT_GRIP = new Vector3f(0.0F, 0.2206F, 0.9285F);
    private static final Vector3f HILT_POMMEL = new Vector3f(0.0F, -0.9729F, 0.2312F);
    private static final float HILT_GAP = 3.0F, HILT_GAP_MIN = 2.0F, HILT_GAP_MAX = 4.0F;
    private static final float OFF_GRIP_Y = 9.5F;

    private static void offHandOnHilt(ModelPart main, ModelPart off, Quaternionf turn, boolean rightHanded, float amount) {
        float side = rightHanded ? 1.0F : -1.0F;
        Vector3f grip = turn.transform(new Vector3f(HILT_GRIP)).add(0.0F, FIST_PIVOT * 16.0F, 0.0F);
        Vector3f pommel = turn.transform(new Vector3f(HILT_POMMEL));
        Vector3f g = new Vector3f(-grip.x() - side, 10.0F - grip.z(), -grip.y() - 2.0F);
        Vector3f p = new Vector3f(-pommel.x(), -pommel.z(), -pommel.y());
        Quaternionf mainRot = new Quaternionf().rotationZYX(main.zRot, main.yRot, main.xRot);
        mainRot.transform(g).add(main.x, main.y, main.z);
        mainRot.transform(p);
        Vector3f w = g.sub(off.x, off.y, off.z);
        float hx = side;
        float reach = (float) Math.sqrt(hx * hx + OFF_GRIP_Y * OFF_GRIP_Y);
        float b = p.dot(w);
        float disc = b * b - (w.lengthSquared() - reach * reach);
        float gap;
        if (disc >= 0.0F) {
            float root = (float) Math.sqrt(disc);
            float near = -b - root, far = -b + root;
            gap = Math.abs(near - HILT_GAP) < Math.abs(far - HILT_GAP) ? near : far;
        } else {
            gap = -b;
        }
        gap = Mth.clamp(gap, HILT_GAP_MIN, HILT_GAP_MAX);
        Vector3f d = w.fma(gap, p);
        float length = d.length();
        if (length < 1.0E-4F) return;
        d.mul(reach / length);
        float pitch = -(float) Math.acos(Mth.clamp(d.y() / OFF_GRIP_Y, -1.0F, 1.0F));
        float yaw = (float) (Mth.atan2(d.x(), d.z()) - Mth.atan2(hx, OFF_GRIP_Y * Mth.sin(pitch)));
        off.xRot = Mth.lerp(amount, off.xRot, pitch);
        off.yRot += Mth.wrapDegrees((yaw - off.yRot) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD * amount;
        off.zRot = Mth.lerp(amount, off.zRot, 0.0F);
    }

    private static void pitchAboutShoulders(ModelPart arm, float bodyYaw, float angle) {
        if (angle == 0.0F) return;
        Quaternionf q = new Quaternionf().rotationAxis(angle, Mth.cos(bodyYaw), 0.0F, -Mth.sin(bodyYaw))
                .mul(new Quaternionf().rotationZYX(arm.zRot, arm.yRot, arm.xRot));
        org.joml.Matrix3f m = new org.joml.Matrix3f().set(q);
        arm.xRot = (float) Math.atan2(m.m12, m.m22);
        arm.yRot = (float) Math.asin(Mth.clamp(-m.m02, -1.0F, 1.0F));
        arm.zRot = (float) Math.atan2(m.m01, m.m00);
    }

    private static boolean grip(WeaponKind kind, boolean offhandEmpty, Player player, float held, boolean betterCombat) {
        return !betterCombat && kind == WeaponKind.LONGSWORD && offhandEmpty && !player.isUsingItem() && held <= 0.0F;
    }

    private static float keyedArms(HumanoidModel<?> model, ModelPart main, ModelPart off, CombatPoseConfig.ArmPose pose,
                                  float mirror, float t, boolean offhandEmpty, float headPitch, boolean offOnHilt) {
        java.util.List<CombatPoseConfig.ArmKey> keys = pose.keys();
        if (keys.isEmpty() || t <= 0.0F || t >= 1.0F) return 0.0F;
        CombatPoseConfig.Segment seg = CombatPoseConfig.segment(keys, t);
        CombatPoseConfig.ArmKey from = seg.from() < 0 ? null : keys.get(seg.from());
        CombatPoseConfig.ArmKey to = seg.to() < 0 ? null : keys.get(seg.to());
        float s = seg.s();
        float weight = from == null ? s : to == null ? 1.0F - s : 1.0F;
        float follow = headPitch * pose.followHead() * weight;
        model.rightArm.yRot -= model.body.yRot;
        model.leftArm.yRot -= model.body.yRot;
        keyedPart(main, from == null ? null : from.main(), to == null ? null : to.main(), s, mirror);
        if (!offOnHilt) main.xRot += follow;
        Vector3f offFrom = from == null ? null : from.off(), offTo = to == null ? null : to.off();
        if (!offOnHilt && offhandEmpty && (offFrom != null || offTo != null)) {
            keyedPart(off, offFrom, offTo, s, mirror);
            off.xRot += follow;
        }
        float twist = (from == null ? 0.0F : from.body()) * (1.0F - s) + (to == null ? 0.0F : to.body()) * s;
        float bodyYaw = -mirror * Mth.DEG_TO_RAD * twist;
        twistBody(model, bodyYaw);
        if (offOnHilt) pitchAboutShoulders(main, bodyYaw, follow);
        return weight;
    }

    private static void twistBody(HumanoidModel<?> model, float bodyYaw) {
        model.body.yRot = bodyYaw;
        model.rightArm.z = Mth.sin(bodyYaw) * 5.0F;
        model.rightArm.x = -Mth.cos(bodyYaw) * 5.0F;
        model.leftArm.z = -Mth.sin(bodyYaw) * 5.0F;
        model.leftArm.x = Mth.cos(bodyYaw) * 5.0F;
        model.rightArm.yRot += bodyYaw;
        model.leftArm.yRot += bodyYaw;
    }

    private static void holdKey(HumanoidModel<?> model, ModelPart main, CombatPoseConfig.ArmKey key, float mirror,
                                float amount, float pitch) {
        model.rightArm.yRot -= model.body.yRot;
        model.leftArm.yRot -= model.body.yRot;
        float bodyYaw = Mth.lerp(amount, model.body.yRot, -mirror * Mth.DEG_TO_RAD * key.body());
        if (key.main() != null) lerp(main, key.main(), mirror, amount);
        twistBody(model, bodyYaw);
        pitchAboutShoulders(main, bodyYaw, pitch);
    }

    private static void keyedPart(ModelPart part, Vector3f from, Vector3f to, float s, float mirror) {
        if (from != null && to != null) {
            lerp(part, new Vector3f(from).lerp(to, s), mirror, 1.0F);
        } else if (to != null) {
            lerp(part, to, mirror, s);
        } else if (from != null) {
            lerp(part, from, mirror, 1.0F - s);
        }
    }

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
