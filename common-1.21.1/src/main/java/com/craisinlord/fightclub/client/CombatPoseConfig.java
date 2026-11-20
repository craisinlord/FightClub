package com.craisinlord.fightclub.client;

import com.craisinlord.fightclub.FightClub;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.joml.Vector3f;

import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * First-person pose parameters, read from {@code assets/fight_club/animations/poses.json}.
 * Any resource pack can override the file, and F3+T reloads it, so poses can be tuned in-game
 * without rebuilding. Missing or invalid entries fall back to the built-in defaults below.
 *
 * <p>Values are written for the right hand. For the left hand the renderer mirrors the X
 * translation and the Y/Z rotations, the same way vanilla mirrors its own use poses.
 */
public class CombatPoseConfig implements ResourceManagerReloadListener {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(FightClub.MOD_ID, "combat_poses");
    private static final ResourceLocation FILE =
            ResourceLocation.fromNamespaceAndPath(FightClub.MOD_ID, "animations/poses.json");

    /**
     * @param translation offset applied after vanilla's arm transform, in first-person units
     * @param rotation    degrees around X, then Y, then Z
     * @param blendIn     ticks to ease fully into the pose
     * @param blendOut    ticks to ease back out to vanilla
     * @param tremble     shake amplitude that grows with charge progress (0 disables)
     * @param readyPop    extra push along Z applied once a charge is full (0 disables)
     */
    public record Pose(Vector3f translation, Vector3f rotation, float blendIn, float blendOut,
                       float tremble, float readyPop, List<Key> keys, Vector3f pivot) {
        public Pose(Vector3f translation, Vector3f rotation, float blendIn, float blendOut, float tremble, float readyPop) {
            this(translation, rotation, blendIn, blendOut, tremble, readyPop, List.of(), new Vector3f());
        }

        public Pose(Vector3f translation, Vector3f rotation, float blendIn, float blendOut, float tremble, float readyPop,
                    List<Key> keys) {
            this(translation, rotation, blendIn, blendOut, tremble, readyPop, keys, new Vector3f());
        }
    }

    /** A keyframe on a one-shot motion's 0..1 progress, eased into from the previous key (or from rest). */
    public interface Keyed {
        float t();
        String ease();
    }

    /**
     * First-person keyframe at progress t; X translation and Y/Z rotation mirror for the left hand. For the
     * dagger jab: an item offset, rotation in degrees around Y (yaw), then X (pitch), then Z (roll), turning
     * around the pose's pivot, then orient (same axis order, around the hand). For the longsword swing and
     * spin: translation moves the hand from the idle hold and rotation is the blade's heading [pitch, yaw,
     * roll], swung round the grip (the pose's pivot) from the idle heading (the pose's rotation).
     */
    public record Key(float t, Vector3f translation, Vector3f rotation, String ease, Vector3f orient) implements Keyed {
        public Key(float t, Vector3f translation, Vector3f rotation, String ease) {
            this(t, translation, rotation, ease, new Vector3f());
        }
    }

    /**
     * Third-person keyframe: main/off arm angles (as in ArmPose), the upper body's twist in degrees
     * (positive turns toward the off-hand side, i.e. left for right-handers), and the held item's
     * extra rotation in degrees: [pitch, yaw, roll], roll twisting it around the arm's length.
     */
    public record ArmKey(float t, Vector3f main, Vector3f off, float body, Vector3f item, String ease) implements Keyed {}

    /**
     * Where progress t falls between keys: from/to are key indexes (-1 = the rest pose before the
     * first key or after the last), s the eased 0..1 position between them.
     */
    public record Segment(int from, int to, float s) {}

    public static Segment segment(List<? extends Keyed> keys, float t) {
        int n = keys.size();
        int i = 0;
        while (i < n && keys.get(i).t() <= t) i++;
        float t0 = i == 0 ? 0.0F : keys.get(i - 1).t();
        float t1 = i == n ? 1.0F : keys.get(i).t();
        float s = t1 > t0 ? Math.max(0.0F, Math.min(1.0F, (t - t0) / (t1 - t0))) : 1.0F;
        return new Segment(i - 1, i == n ? -1 : i, ease(i == n ? "inout" : keys.get(i).ease(), s));
    }

    /** Easing curves for keyframes: linear, in, out, inout (quadratic), in_cubic, out_cubic. */
    public static float ease(String name, float s) {
        return switch (name) {
            case "linear" -> s;
            case "in" -> s * s;
            case "out" -> 1.0F - (1.0F - s) * (1.0F - s);
            case "in_cubic" -> s * s * s;
            case "out_cubic" -> 1.0F - (1.0F - s) * (1.0F - s) * (1.0F - s);
            default -> s < 0.5F ? 2.0F * s * s : 1.0F - 2.0F * (1.0F - s) * (1.0F - s);
        };
    }

    /**
     * Third-person arm rotations in degrees, as absolute model angles (vanilla's own poses are
     * replaced, blended by the pose amount). Written for a right-handed player: {@code main} is
     * the right arm, {@code off} the left arm; both mirror for left-handed players. {@code off}
     * may be null, and only applies when the offhand is empty (except shield_bash, where it is
     * the shield arm). {@code item} (may be null) turns the main-hand item in the hand, in degrees
     * [pitch, yaw, roll] (roll twists it around the arm's length; sword_block uses it to hold the
     * blade flat across the body, 1.8 style). {@code keys} keyframe the longsword swing and spin.
     */
    public record ArmPose(Vector3f main, Vector3f off, float followHead, float walkBob, Vector3f item, List<ArmKey> keys) {
        public ArmPose(Vector3f main, Vector3f off) { this(main, off, 0.0F, 0.0F, null, List.of()); }
        public ArmPose(Vector3f main, Vector3f off, float followHead, float walkBob) { this(main, off, followHead, walkBob, null, List.of()); }
        public ArmPose(Vector3f main, Vector3f off, float followHead, float walkBob, Vector3f item) {
            this(main, off, followHead, walkBob, item, List.of());
        }
    }

    private static final Map<CombatPose, Pose> DEFAULTS = new EnumMap<>(CombatPose.class);
    private static final Map<CombatPose, ArmPose> ARM_DEFAULTS = new EnumMap<>(CombatPose.class);
    private static volatile Map<CombatPose, Pose> poses;
    private static volatile Map<CombatPose, ArmPose> armPoses;

    public static ArmPose arms(CombatPose pose) {
        ArmPose loaded = armPoses.get(pose);
        return loaded != null ? loaded : ARM_DEFAULTS.get(pose);
    }

    private static Vector3f deg(float x, float y, float z) { return new Vector3f(x, y, z); }

    static {
        // Tuned against screenshots from DevPoseGallery; the JSON overrides these.
        // Note: flat item sprites face the camera at an angle, so rolling them (Z) turns them
        // edge-on; flat items should pitch (X) instead. The 3D trident can roll freely.
        // Compromise until the spear gets a real long in-hand model on 1.21.1 (Phase 4): aiming the
        // flat 16px icon fully at the crosshair turns it edge-on.
        DEFAULTS.put(CombatPose.SPEAR_LOWERED, new Pose(new Vector3f(-0.08F, -0.05F, 0.0F),
                new Vector3f(0.0F, 0.0F, 25.0F), 4.0F, 3.0F, 0.0F, 0.0F));
        DEFAULTS.put(CombatPose.TRIDENT_LOWERED, new Pose(new Vector3f(-0.22F, -0.05F, 0.0F),
                new Vector3f(-65.0F, 0.0F, 0.0F), 4.0F, 3.0F, 0.0F, 0.0F));
        // Drawn in and back with the tip tilted toward the crosshair; scaled by charge progress in the renderer.
        DEFAULTS.put(CombatPose.DAGGER_CHARGE, new Pose(new Vector3f(-0.12F, 0.08F, 0.18F),
                new Vector3f(-25.0F, 0.0F, 25.0F), 5.0F, 2.0F, 0.02F, -0.10F));
        DEFAULTS.put(CombatPose.DAGGER_POUNCE, new Pose(new Vector3f(-0.20F, 0.06F, -0.40F),
                new Vector3f(-45.0F, 0.0F, 0.0F), 2.0F, 3.0F, 0.0F, 0.0F));
        // Classic 1.8 sword block: the blade turned flat across the screen, tip angled up and out.
        DEFAULTS.put(CombatPose.SWORD_BLOCK, new Pose(new Vector3f(-0.14142136F, 0.08F, 0.14142136F),
                new Vector3f(-102.25F, 13.365F, 78.05F), 3.0F, 3.0F, 0.0F, 0.0F));
        DEFAULTS.put(CombatPose.DAGGER_THROW, new Pose(new Vector3f(-0.05F, 0.35F, 0.10F),
                new Vector3f(-45.0F, 25.0F, -8.0F), 4.0F, 2.0F, 0.0F, 0.0F));
        DEFAULTS.put(CombatPose.RECOVERY_DIP, new Pose(new Vector3f(0.0F, -0.025F, 0.01F),
                new Vector3f(0.0F, 0.0F, -4.0F), 0.0F, 0.0F, 0.0F, 0.0F));
        DEFAULTS.put(CombatPose.BLOCK_RECOIL, new Pose(new Vector3f(0.0F, -0.03F, 0.10F),
                new Vector3f(8.0F, 0.0F, 6.0F), 5.0F, 0.0F, 0.0F, 0.0F));
        DEFAULTS.put(CombatPose.SHIELD_BASH, new Pose(new Vector3f(0.04F, 0.02F, -0.32F),
                new Vector3f(-4.0F, 0.0F, 0.0F), 6.0F, 0.0F, 0.0F, 0.0F));
        DEFAULTS.put(CombatPose.JAB_DAGGER, new Pose(new Vector3f(), new Vector3f(), 4.0F, 0.0F, 0.0F, 0.0F, List.of(
                new Key(0.2F, new Vector3f(-0.06F, 0.06F, 0.06F), deg(-75.0F, 20.0F, 0.0F), "out"),
                new Key(0.4F, new Vector3f(-0.14F, 0.1F, -0.3F), deg(-80.0F, 22.0F, 0.0F), "out_cubic"),
                new Key(0.62F, new Vector3f(-0.06F, 0.02F, 0.12F), deg(-45.0F, 10.0F, 0.0F), "inout"))));
        DEFAULTS.put(CombatPose.JAB_SPEAR, new Pose(new Vector3f(-0.12F, 0.08F, -0.55F),
                new Vector3f(-4.0F, 0.0F, 0.0F), 12.0F, 0.0F, 0.0F, 0.0F));
        // Horizontal cut, right to left, like Better Combat's two-handed slash seen from the eyes: the blade turns round
        // the grip like a clock hand (rotation = heading [pitch, yaw, roll]) while the hand (translation) moves a short way
        // low across the view. The pose's rotation is the idle hold's heading and pivot the grip (see ItemInHandRendererMixin#arc).
        DEFAULTS.put(CombatPose.HEAVY_LONGSWORD, new Pose(new Vector3f(), deg(68.57F, -180.0F, -90.0F), 12.0F, 0.0F, 0.0F, 0.0F, List.of(
                new Key(0.1F, new Vector3f(-0.05F, 0.24F, 0.04F), deg(14.0F, -72.0F, -15.0F), "out"),
                new Key(0.2F, new Vector3f(-0.15F, 0.23F, -0.01F), deg(10.0F, -55.0F, -10.0F), "inout"),
                new Key(0.32F, new Vector3f(-0.3F, 0.22F, -0.08F), deg(11.0F, -28.0F, -5.0F), "linear"),
                new Key(0.45F, new Vector3f(-0.5F, 0.21F, -0.16F), deg(16.0F, 0.0F, 0.0F), "linear"),
                new Key(0.58F, new Vector3f(-0.7F, 0.22F, -0.08F), deg(11.0F, 30.0F, 5.0F), "linear"),
                new Key(0.68F, new Vector3f(-0.84F, 0.23F, 0.0F), deg(10.0F, 58.0F, 10.0F), "linear"),
                new Key(0.76F, new Vector3f(-0.92F, 0.2F, 0.04F), deg(8.0F, 72.0F, 12.0F), "out"),
                new Key(0.86F, new Vector3f(-0.7F, -0.6F, 0.02F), deg(-10.0F, 95.0F, 0.0F), "inout")), new Vector3f(0.07F, -0.19F, -0.1F)));
        // The return cut, left to right (swings alternate; the duration is heavy_longsword's): the forehand mirrored.
        DEFAULTS.put(CombatPose.HEAVY_LONGSWORD_BACK, new Pose(new Vector3f(), deg(68.57F, -180.0F, -90.0F), 12.0F, 0.0F, 0.0F, 0.0F, List.of(
                new Key(0.07F, new Vector3f(-0.5F, -0.7F, -0.15F), deg(-30.0F, 80.0F, 0.0F), "inout"),
                new Key(0.15F, new Vector3f(-1.07F, 0.24F, 0.04F), deg(14.0F, 72.0F, 15.0F), "out"),
                new Key(0.24F, new Vector3f(-0.97F, 0.23F, -0.01F), deg(10.0F, 55.0F, 10.0F), "inout"),
                new Key(0.36F, new Vector3f(-0.82F, 0.22F, -0.08F), deg(11.0F, 28.0F, 5.0F), "linear"),
                new Key(0.49F, new Vector3f(-0.62F, 0.21F, -0.16F), deg(16.0F, 0.0F, 0.0F), "linear"),
                new Key(0.62F, new Vector3f(-0.42F, 0.22F, -0.08F), deg(11.0F, -30.0F, -5.0F), "linear"),
                new Key(0.72F, new Vector3f(-0.28F, 0.23F, 0.0F), deg(10.0F, -58.0F, -10.0F), "linear"),
                new Key(0.8F, new Vector3f(-0.2F, 0.2F, 0.04F), deg(8.0F, -72.0F, -12.0F), "out")), new Vector3f(0.07F, -0.19F, -0.1F)));
        DEFAULTS.put(CombatPose.LONGSWORD_SPIN_CHARGE, new Pose(new Vector3f(0.12F, -0.04F, 0.10F),
                new Vector3f(-10.0F, -45.0F, 70.0F), 6.0F, 2.0F, 0.006F, 0.0F));
        // The spin's sweep: the forehand cut's motion over the spin's length.
        // The spin's sweep: the forehand cut's motion over the spin's length.
        // The spin's sweep: the forehand cut's motion over the spin's length.
        // The spin's sweep: the forehand cut's motion over the spin's length.
        DEFAULTS.put(CombatPose.LONGSWORD_SPIN, new Pose(new Vector3f(), deg(68.57F, -180.0F, -90.0F), 10.0F, 0.0F, 0.0F, 0.0F, List.of(
                new Key(0.1F, new Vector3f(-0.05F, 0.24F, 0.04F), deg(14.0F, -72.0F, -15.0F), "out"),
                new Key(0.2F, new Vector3f(-0.15F, 0.23F, -0.01F), deg(10.0F, -55.0F, -10.0F), "inout"),
                new Key(0.32F, new Vector3f(-0.3F, 0.22F, -0.08F), deg(11.0F, -28.0F, -5.0F), "linear"),
                new Key(0.45F, new Vector3f(-0.5F, 0.21F, -0.16F), deg(16.0F, 0.0F, 0.0F), "linear"),
                new Key(0.58F, new Vector3f(-0.7F, 0.22F, -0.08F), deg(11.0F, 30.0F, 5.0F), "linear"),
                new Key(0.68F, new Vector3f(-0.84F, 0.23F, 0.0F), deg(10.0F, 58.0F, 10.0F), "linear"),
                new Key(0.76F, new Vector3f(-0.92F, 0.2F, 0.04F), deg(8.0F, 72.0F, 12.0F), "out"),
                new Key(0.86F, new Vector3f(-0.7F, -0.6F, 0.02F), deg(-10.0F, 95.0F, 0.0F), "inout")), new Vector3f(0.07F, -0.19F, -0.1F)));
        poses = new EnumMap<>(DEFAULTS);

        // Third-person starting values; tune through "third_person" in poses.json.
        // follow_head: how much the arms tilt with the player's look pitch (vanilla's bow pose uses 1.0).
        // walk_bob: how much of vanilla's walking arm swing survives (1.0 = full held-item swing).
        // 1.8 style: the arm raised across the chest, the blade held flat across the body, tip angled out and up.
        ARM_DEFAULTS.put(CombatPose.SWORD_BLOCK, new ArmPose(deg(-60.0F, -30.0F, 0.0F), null, 0.3F, 0.3F, deg(-30.0F, 0.0F, 55.0F)));
        ARM_DEFAULTS.put(CombatPose.SPEAR_LOWERED, new ArmPose(deg(-20.0F, 5.0F, 0.0F), deg(-45.0F, 35.0F, 0.0F), 0.6F, 0.5F));
        // Arm nearly hanging: the 3D trident points along the forearm, so this holds it level, not tipped up.
        ARM_DEFAULTS.put(CombatPose.TRIDENT_LOWERED, new ArmPose(deg(-5.0F, 5.0F, 0.0F), deg(-45.0F, 35.0F, 0.0F), 0.6F, 0.5F));
        ARM_DEFAULTS.put(CombatPose.DAGGER_CHARGE, new ArmPose(deg(55.0F, 25.0F, 20.0F), null, 0.3F, 0.3F));
        ARM_DEFAULTS.put(CombatPose.DAGGER_POUNCE, new ArmPose(deg(-90.0F, -5.0F, 0.0F), null, 0.9F, 0.0F));
        ARM_DEFAULTS.put(CombatPose.DAGGER_THROW, new ArmPose(deg(-155.0F, 0.0F, 0.0F), null, 0.5F, 0.0F));
        ARM_DEFAULTS.put(CombatPose.SHIELD_BASH, new ArmPose(null, deg(-80.0F, 10.0F, 0.0F), 0.5F, 0.0F));
        ARM_DEFAULTS.put(CombatPose.JAB_DAGGER, new ArmPose(deg(-80.0F, -5.0F, 0.0F), null, 0.8F, 0.0F));
        ARM_DEFAULTS.put(CombatPose.JAB_SPEAR, new ArmPose(deg(-85.0F, 0.0F, 0.0F), deg(-60.0F, 30.0F, 0.0F), 0.8F, 0.0F));
        ARM_DEFAULTS.put(CombatPose.LONGSWORD_GRIP, new ArmPose(deg(-40.0F, -18.0F, 0.0F), deg(-55.0F, 33.0F, 0.0F), 0.0F, 1.0F));
        // Longsword swing, keyframed like Better Combat's two-handed horizontal slash: [t, main arm, off arm,
        // upper-body twist (+ = toward the off hand), item turn (pitch, yaw, roll around the arm), ease].
        ARM_DEFAULTS.put(CombatPose.HEAVY_LONGSWORD, new ArmPose(null, null, 0.5F, 0.0F, null, List.of(
                new ArmKey(0.22F, deg(-88.0F, 55.0F, 0.0F), deg(-80.0F, 72.0F, 0.0F), -30.0F, deg(-65.0F, 0.0F, -90.0F), "out"),
                new ArmKey(0.4F, deg(-93.0F, 0.0F, 0.0F), deg(-97.0F, 45.0F, 0.0F), 30.0F, deg(-85.0F, 0.0F, -90.0F), "in"),
                new ArmKey(0.55F, deg(-92.0F, -35.0F, 0.0F), deg(-93.0F, 28.0F, 0.0F), 55.0F, deg(-80.0F, 0.0F, 90.0F), "out"),
                new ArmKey(0.68F, deg(-88.0F, -50.0F, 0.0F), deg(-88.0F, 22.0F, 0.0F), 60.0F, deg(-70.0F, 0.0F, 90.0F), "out"))));
        // Third-person return cut: the forehand mirrored.
        ARM_DEFAULTS.put(CombatPose.HEAVY_LONGSWORD_BACK, new ArmPose(null, null, 0.5F, 0.0F, null, List.of(
                new ArmKey(0.22F, deg(-88.0F, -55.0F, 0.0F), deg(-80.0F, -72.0F, 0.0F), 30.0F, deg(-65.0F, 0.0F, 90.0F), "out"),
                new ArmKey(0.4F, deg(-93.0F, 0.0F, 0.0F), deg(-97.0F, -45.0F, 0.0F), -30.0F, deg(-85.0F, 0.0F, 90.0F), "in"),
                new ArmKey(0.55F, deg(-92.0F, 35.0F, 0.0F), deg(-93.0F, -28.0F, 0.0F), -55.0F, deg(-80.0F, 0.0F, -90.0F), "out"),
                new ArmKey(0.68F, deg(-88.0F, 50.0F, 0.0F), deg(-88.0F, -22.0F, 0.0F), -60.0F, deg(-70.0F, 0.0F, -90.0F), "out"))));
        ARM_DEFAULTS.put(CombatPose.LONGSWORD_SPIN_CHARGE, new ArmPose(deg(-25.0F, 55.0F, 0.0F), deg(-45.0F, 40.0F, 0.0F), 0.3F, 0.3F));
        ARM_DEFAULTS.put(CombatPose.LONGSWORD_SPIN, new ArmPose(null, null, 0.2F, 0.0F, null, List.of(
                new ArmKey(0.12F, deg(-85.0F, 40.0F, 0.0F), deg(-82.0F, 70.0F, 0.0F), -30.0F, deg(-50.0F, 0.0F, -90.0F), "out"),
                new ArmKey(0.3F, deg(-95.0F, 10.0F, 0.0F), deg(-95.0F, 45.0F, 0.0F), 0.0F, deg(-80.0F, 0.0F, -90.0F), "in"),
                new ArmKey(0.75F, deg(-95.0F, 0.0F, 0.0F), deg(-95.0F, 45.0F, 0.0F), 15.0F, deg(-85.0F, 0.0F, -90.0F), "linear"),
                new ArmKey(0.85F, deg(-85.0F, -30.0F, 0.0F), deg(-90.0F, 30.0F, 0.0F), 30.0F, deg(-70.0F, 0.0F, 90.0F), "out"))));
        armPoses = new EnumMap<>(ARM_DEFAULTS);
    }

    public static Pose get(CombatPose pose) {
        Pose loaded = poses.get(pose);
        return loaded != null ? loaded : DEFAULTS.get(pose);
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        Map<CombatPose, Pose> loaded = new EnumMap<>(DEFAULTS);
        Map<CombatPose, ArmPose> loadedArms = new EnumMap<>(ARM_DEFAULTS);
        Optional<Resource> resource = manager.getResource(FILE);
        if (resource.isPresent()) {
            try (Reader reader = resource.get().openAsReader()) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                JsonObject thirdPerson = root.has("third_person") ? root.getAsJsonObject("third_person") : new JsonObject();
                for (CombatPose pose : CombatPose.values()) {
                    if (pose == CombatPose.NONE) continue;
                    JsonElement element = root.get(pose.jsonKey());
                    if (element != null && element.isJsonObject() && DEFAULTS.containsKey(pose)) {
                        loaded.put(pose, parse(element.getAsJsonObject(), DEFAULTS.get(pose)));
                    }
                    JsonElement arms = thirdPerson.get(pose.jsonKey());
                    if (arms != null && arms.isJsonObject()) {
                        ArmPose fallback = ARM_DEFAULTS.get(pose);
                        JsonObject json = arms.getAsJsonObject();
                        loadedArms.put(pose, new ArmPose(
                                vector(json, "main_arm", fallback == null ? null : fallback.main()),
                                vector(json, "off_arm", fallback == null ? null : fallback.off()),
                                number(json, "follow_head", fallback == null ? 0.0F : fallback.followHead(), 0.0F),
                                number(json, "walk_bob", fallback == null ? 0.0F : fallback.walkBob(), 0.0F),
                                vector(json, "item", fallback == null ? null : fallback.item()),
                                json.has("keys") ? armKeys(json.getAsJsonArray("keys"))
                                        : fallback == null ? List.of() : fallback.keys()));
                    }
                }
            } catch (Exception exception) {
                FightClub.LOGGER.error("Invalid {}; using built-in poses: {}", FILE, exception.getMessage());
            }
        }
        poses = loaded;
        armPoses = loadedArms;
    }

    private static Pose parse(JsonObject json, Pose fallback) {
        return new Pose(
                vector(json, "translation", fallback.translation()),
                vector(json, "rotation", fallback.rotation()),
                json.has("duration_ticks") ? number(json, "duration_ticks", fallback.blendIn(), 1.0F)
                        : number(json, "blend_in_ticks", fallback.blendIn(), 0.5F),
                number(json, "blend_out_ticks", fallback.blendOut(), 0.5F),
                number(json, "tremble", fallback.tremble(), 0.0F),
                json.has("ready_pop") ? json.get("ready_pop").getAsFloat() : fallback.readyPop(),
                json.has("keys") ? keys(json.getAsJsonArray("keys")) : fallback.keys(),
                vector(json, "pivot", fallback.pivot()));
    }

    private static final Vector3f ZERO = new Vector3f();

    private static List<Key> keys(JsonArray array) {
        List<Key> keys = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject key = element.getAsJsonObject();
            keys.add(new Key(key.get("t").getAsFloat(), vector(key, "translation", ZERO), vector(key, "rotation", ZERO),
                    key.has("ease") ? key.get("ease").getAsString() : "inout", vector(key, "orient", ZERO)));
        }
        return List.copyOf(keys);
    }

    private static List<ArmKey> armKeys(JsonArray array) {
        List<ArmKey> keys = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject key = element.getAsJsonObject();
            keys.add(new ArmKey(key.get("t").getAsFloat(), vector(key, "main_arm", ZERO), vector(key, "off_arm", null),
                    key.has("body") ? key.get("body").getAsFloat() : 0.0F, vector(key, "item", ZERO),
                    key.has("ease") ? key.get("ease").getAsString() : "inout"));
        }
        return List.copyOf(keys);
    }

    private static Vector3f vector(JsonObject json, String key, Vector3f fallback) {
        if (!json.has(key)) return fallback;
        JsonArray array = json.getAsJsonArray(key);
        if (array.size() != 3) throw new IllegalArgumentException(key + " needs 3 numbers");
        return new Vector3f(array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat());
    }

    private static float number(JsonObject json, String key, float fallback, float min) {
        return json.has(key) ? Math.max(min, json.get(key).getAsFloat()) : fallback;
    }
}
