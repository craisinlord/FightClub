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

public class CombatPoseConfig implements ResourceManagerReloadListener {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(FightClub.MOD_ID, "combat_poses");
    private static final ResourceLocation FILE =
            ResourceLocation.fromNamespaceAndPath(FightClub.MOD_ID, "animations/poses.json");

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

    public interface Keyed {
        float t();
        String ease();
    }

    public record Key(float t, Vector3f translation, Vector3f rotation, String ease, Vector3f orient) implements Keyed {
        public Key(float t, Vector3f translation, Vector3f rotation, String ease) {
            this(t, translation, rotation, ease, new Vector3f());
        }
    }

    public record ArmKey(float t, Vector3f main, Vector3f off, float body, Vector3f item, String ease) implements Keyed {}

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

    public static float flip(Pose pose, float t) {
        if (pose.keys().isEmpty() || t <= 0.0F) return 0.0F;
        float total = pose.keys().get(pose.keys().size() - 1).rotation().x() - DAGGER_REST_ROTATION.x();
        if (t >= 1.0F || total == 0.0F) return t >= 1.0F ? -180.0F : 0.0F;
        Vector3f pos = new Vector3f(), rot = new Vector3f(), orient = new Vector3f();
        daggerToss(pose, t, pos, rot, orient);
        return -180.0F * (rot.x() - DAGGER_REST_ROTATION.x()) / Math.abs(total);
    }

    public static final Vector3f DAGGER_REST_TRANSLATION = new Vector3f(1.13F / 16.0F, 3.2F / 16.0F, 1.13F / 16.0F);
    public static final Vector3f DAGGER_REST_ROTATION = new Vector3f(20.0F, 0.0F, 0.0F);
    public static final Vector3f DAGGER_REST_ORIENT = new Vector3f(0.0F, -90.0F, 0.0F);

    public static void daggerToss(Pose flip, float t, Vector3f pos, Vector3f rot, Vector3f orient) {
        List<Key> keys = flip.keys();
        if (keys.isEmpty()) {
            pos.set(DAGGER_REST_TRANSLATION);
            rot.set(DAGGER_REST_ROTATION);
            orient.set(DAGGER_REST_ORIENT);
            return;
        }
        Segment seg = segment(keys, Math.max(0.0F, t));
        if (seg.to() < 0) {
            Key last = keys.get(keys.size() - 1);
            pos.set(last.translation());
            rot.set(last.rotation());
            orient.set(last.orient());
            return;
        }
        Key from = seg.from() < 0 ? null : keys.get(seg.from());
        Key to = keys.get(seg.to());
        pos.set(from != null ? from.translation() : DAGGER_REST_TRANSLATION).lerp(to.translation(), seg.s());
        rot.set(from != null ? from.rotation() : DAGGER_REST_ROTATION).lerp(to.rotation(), seg.s());
        orient.set(from != null ? from.orient() : DAGGER_REST_ORIENT).lerp(to.orient(), seg.s());
    }

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
    private static final Map<CombatPose, Float> ROLL_DEFAULTS = new EnumMap<>(Map.of(
            CombatPose.TRIDENT_LOWERED, 90.0F, CombatPose.JAB_SPEAR, 90.0F));
    private static volatile Map<CombatPose, Float> rolls = new EnumMap<>(ROLL_DEFAULTS);

    public static float modelRoll(CombatPose pose) {
        Float roll = rolls.get(pose);
        return roll == null ? 0.0F : roll;
    }

    public static ArmPose arms(CombatPose pose) {
        ArmPose loaded = armPoses.get(pose);
        return loaded != null ? loaded : ARM_DEFAULTS.get(pose);
    }

    private static Vector3f deg(float x, float y, float z) { return new Vector3f(x, y, z); }

    static {
        DEFAULTS.put(CombatPose.SPEAR_LOWERED, new Pose(new Vector3f(-0.08F, -0.05F, 0.0F),
                new Vector3f(0.0F, 0.0F, 25.0F), 4.0F, 3.0F, 0.0F, 0.0F));
        DEFAULTS.put(CombatPose.TRIDENT_LOWERED, new Pose(new Vector3f(-0.22F, -0.05F, -0.25F),
                new Vector3f(-65.0F, 0.0F, 0.0F), 4.0F, 3.0F, 0.0F, 0.0F));
        DEFAULTS.put(CombatPose.DAGGER_CHARGE, new Pose(new Vector3f(-0.12F, 0.08F, 0.18F),
                new Vector3f(-25.0F, 0.0F, 25.0F), 5.0F, 2.0F, 0.02F, -0.10F));
        DEFAULTS.put(CombatPose.DAGGER_POUNCE, new Pose(new Vector3f(-0.20F, 0.06F, -0.40F),
                new Vector3f(-45.0F, 0.0F, 0.0F), 2.0F, 3.0F, 0.0F, 0.0F));
        DEFAULTS.put(CombatPose.SWORD_BLOCK, new Pose(new Vector3f(-0.14142136F, 0.08F, 0.14142136F),
                new Vector3f(-102.25F, 13.365F, 78.05F), 3.0F, 3.0F, 0.0F, 0.0F));
        DEFAULTS.put(CombatPose.DAGGER_THROW, new Pose(new Vector3f(), new Vector3f(), 3.0F, 2.0F, 0.004F, 0.0F, List.of(
                new Key(1.0F, new Vector3f(0.0F, 0.02F, 0.06F), deg(-8.0F, 0.0F, 0.0F), "inout"))));
        DEFAULTS.put(CombatPose.DAGGER_THROW_FLIP, new Pose(new Vector3f(), new Vector3f(), 7.0F, 0.0F, 0.0F, 0.0F, List.of(
                new Key(0.07F, new Vector3f(0.06F, 0.13F, 0.06F), deg(28.0F, 0.0F, 0.0F), "out", deg(0.0F, -95.0F, 0.0F)),
                new Key(0.15F, new Vector3f(0.03F, 0.205F, 0.0F), deg(10.0F, 0.0F, 0.0F), "out", deg(0.0F, -100.0F, 0.0F)),
                new Key(0.25F, new Vector3f(0.0F, 0.3F, -0.02F), deg(-17.5F, 0.0F, 0.0F), "linear", deg(0.0F, -100.0F, 0.0F)),
                new Key(0.35F, new Vector3f(-0.04F, 0.395F, -0.04F), deg(-45.0F, 0.0F, 0.0F), "linear", deg(0.0F, -100.0F, 0.0F)),
                new Key(0.45F, new Vector3f(-0.07F, 0.45F, -0.05F), deg(-72.0F, 0.0F, 0.0F), "linear", deg(0.0F, -100.0F, 0.0F)),
                new Key(0.55F, new Vector3f(-0.08F, 0.46F, -0.05F), deg(-100.0F, 0.0F, 0.0F), "linear", deg(0.0F, -97.0F, 0.0F)),
                new Key(0.65F, new Vector3f(-0.07F, 0.44F, -0.03F), deg(-127.0F, 0.0F, 0.0F), "linear", deg(0.0F, -93.0F, 0.0F)),
                new Key(0.75F, new Vector3f(-0.06F, 0.395F, -0.01F), deg(-155.0F, 0.0F, 0.0F), "linear", deg(0.0F, -88.0F, 0.0F)),
                new Key(0.86F, new Vector3f(-0.05F, 0.32F, 0.02F), deg(-185.0F, 0.0F, 0.0F), "linear", deg(0.0F, -83.0F, 0.0F)),
                new Key(1.0F, new Vector3f(-0.06F, 0.45F, 0.02F), deg(-200.0F, 0.0F, 8.0F), "out", deg(0.0F, -80.0F, 0.0F)))));
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
        DEFAULTS.put(CombatPose.JAB_SPEAR, new Pose(new Vector3f(-0.04F, 0.02F, -0.25F),
                new Vector3f(0.0F, 0.0F, 0.0F), 12.0F, 0.0F, 0.0F, 0.0F));
        DEFAULTS.put(CombatPose.HEAVY_LONGSWORD, new Pose(new Vector3f(), deg(68.57F, -180.0F, -90.0F), 12.0F, 0.0F, 0.0F, 0.0F, List.of(
                new Key(0.1F, new Vector3f(0.12F, 0.5F, 0.0F), deg(14.0F, -88.0F, -15.0F), "out"),
                new Key(0.2F, new Vector3f(0.05F, 0.56F, -0.05F), deg(16.0F, -66.0F, -10.0F), "inout"),
                new Key(0.32F, new Vector3f(-0.21F, 0.6F, -0.11F), deg(19.0F, -34.0F, -5.0F), "linear"),
                new Key(0.45F, new Vector3f(-0.58F, 0.63F, -0.16F), deg(22.0F, 0.0F, 0.0F), "linear"),
                new Key(0.58F, new Vector3f(-1.01F, 0.6F, -0.11F), deg(19.0F, 36.0F, 5.0F), "linear"),
                new Key(0.68F, new Vector3f(-1.29F, 0.56F, -0.05F), deg(16.0F, 66.0F, 10.0F), "linear"),
                new Key(0.76F, new Vector3f(-1.45F, 0.48F, 0.0F), deg(13.0F, 88.0F, 12.0F), "out"),
                new Key(0.86F, new Vector3f(-1.05F, -0.5F, 0.02F), deg(-10.0F, 105.0F, 0.0F), "inout")), new Vector3f(0.07F, -0.19F, -0.1F)));
        DEFAULTS.put(CombatPose.HEAVY_LONGSWORD_BACK, new Pose(new Vector3f(), deg(68.57F, -180.0F, -90.0F), 12.0F, 0.0F, 0.0F, 0.0F, List.of(
                new Key(0.07F, new Vector3f(-0.9F, -0.55F, -0.1F), deg(-30.0F, 92.0F, 0.0F), "inout"),
                new Key(0.15F, new Vector3f(-1.45F, 0.48F, 0.0F), deg(13.0F, 88.0F, 15.0F), "out"),
                new Key(0.24F, new Vector3f(-1.29F, 0.56F, -0.05F), deg(16.0F, 66.0F, 10.0F), "inout"),
                new Key(0.36F, new Vector3f(-1.01F, 0.6F, -0.11F), deg(19.0F, 36.0F, 5.0F), "linear"),
                new Key(0.49F, new Vector3f(-0.58F, 0.63F, -0.16F), deg(22.0F, 0.0F, 0.0F), "linear"),
                new Key(0.62F, new Vector3f(-0.21F, 0.6F, -0.11F), deg(19.0F, -34.0F, -5.0F), "linear"),
                new Key(0.72F, new Vector3f(0.05F, 0.56F, -0.05F), deg(16.0F, -66.0F, -10.0F), "linear"),
                new Key(0.8F, new Vector3f(0.12F, 0.5F, 0.0F), deg(14.0F, -88.0F, -12.0F), "out")), new Vector3f(0.07F, -0.19F, -0.1F)));
        DEFAULTS.put(CombatPose.LONGSWORD_SPIN_CHARGE, new Pose(new Vector3f(0.12F, -0.04F, 0.10F),
                new Vector3f(-10.0F, 45.0F, -70.0F), 8.0F, 2.0F, 0.006F, 0.0F));
        DEFAULTS.put(CombatPose.LONGSWORD_SPIN, new Pose(new Vector3f(), deg(68.57F, -180.0F, -90.0F), 15.0F, 0.0F, 0.0F, 0.0F, List.of(
                new Key(0.1F, new Vector3f(-0.05F, 0.24F, 0.04F), deg(14.0F, -72.0F, -15.0F), "out"),
                new Key(0.2F, new Vector3f(-0.15F, 0.23F, -0.01F), deg(10.0F, -55.0F, -10.0F), "inout"),
                new Key(0.32F, new Vector3f(-0.3F, 0.22F, -0.08F), deg(11.0F, -28.0F, -5.0F), "linear"),
                new Key(0.45F, new Vector3f(-0.5F, 0.21F, -0.16F), deg(16.0F, 0.0F, 0.0F), "linear"),
                new Key(0.58F, new Vector3f(-0.7F, 0.22F, -0.08F), deg(11.0F, 30.0F, 5.0F), "linear"),
                new Key(0.68F, new Vector3f(-0.84F, 0.23F, 0.0F), deg(10.0F, 58.0F, 10.0F), "linear"),
                new Key(0.76F, new Vector3f(-0.92F, 0.2F, 0.04F), deg(8.0F, 72.0F, 12.0F), "out"),
                new Key(0.86F, new Vector3f(-0.7F, -0.6F, 0.02F), deg(-10.0F, 95.0F, 0.0F), "inout")), new Vector3f(0.07F, -0.19F, -0.1F)));
        poses = new EnumMap<>(DEFAULTS);

        ARM_DEFAULTS.put(CombatPose.SWORD_BLOCK, new ArmPose(deg(-60.0F, -30.0F, 0.0F), null, 0.3F, 0.3F, deg(-30.0F, 0.0F, 55.0F)));
        ARM_DEFAULTS.put(CombatPose.SPEAR_LOWERED, new ArmPose(deg(-20.0F, 5.0F, 0.0F), deg(-45.0F, 35.0F, 0.0F), 0.6F, 0.5F));
        ARM_DEFAULTS.put(CombatPose.TRIDENT_LOWERED, new ArmPose(deg(-5.0F, 5.0F, 0.0F), deg(-45.0F, 35.0F, 0.0F), 0.6F, 0.5F));
        ARM_DEFAULTS.put(CombatPose.DAGGER_CHARGE, new ArmPose(deg(55.0F, 25.0F, 20.0F), null, 0.3F, 0.3F));
        ARM_DEFAULTS.put(CombatPose.DAGGER_POUNCE, new ArmPose(deg(-90.0F, -5.0F, 0.0F), null, 0.9F, 0.0F));
        ARM_DEFAULTS.put(CombatPose.DAGGER_THROW, new ArmPose(deg(-155.0F, 0.0F, 0.0F), null, 0.5F, 0.0F));
        ARM_DEFAULTS.put(CombatPose.SHIELD_BASH, new ArmPose(null, deg(-80.0F, 10.0F, 0.0F), 0.5F, 0.0F));
        ARM_DEFAULTS.put(CombatPose.JAB_DAGGER, new ArmPose(deg(-80.0F, -5.0F, 0.0F), null, 0.8F, 0.0F));
        ARM_DEFAULTS.put(CombatPose.JAB_SPEAR, new ArmPose(deg(-85.0F, 0.0F, 0.0F), deg(-60.0F, 30.0F, 0.0F), 0.8F, 0.0F));
        ARM_DEFAULTS.put(CombatPose.LONGSWORD_GRIP, new ArmPose(deg(-62.0F, -50.0F, 0.0F), null, 0.0F, 0.0F, deg(2.0F, 0.0F, -8.0F)));
        ARM_DEFAULTS.put(CombatPose.HEAVY_LONGSWORD, new ArmPose(null, null, 0.5F, 0.0F, null, List.of(
                new ArmKey(0.18F, deg(-112.0F, -41.0F, 0.0F), null, -50.0F, deg(36.0F, 0.0F, -4.0F), "out"),
                new ArmKey(0.36F, deg(-92.0F, -26.0F, 0.0F), null, -25.0F, deg(31.0F, 0.0F, -71.0F), "in"),
                new ArmKey(0.48F, deg(-83.0F, -31.0F, 0.0F), null, 15.0F, deg(-40.0F, 0.0F, -74.0F), "linear"),
                new ArmKey(0.6F, deg(-80.0F, -52.0F, 0.0F), null, 45.0F, deg(-97.0F, 0.0F, -87.0F), "linear"),
                new ArmKey(0.72F, deg(-67.0F, -66.0F, 0.0F), null, 60.0F, deg(-132.0F, 0.0F, -86.0F), "out"))));
        ARM_DEFAULTS.put(CombatPose.HEAVY_LONGSWORD_BACK, new ArmPose(null, null, 0.5F, 0.0F, null, List.of(
                new ArmKey(0.18F, deg(-109.0F, -45.0F, 0.0F), null, 50.0F, deg(1.0F, 0.0F, 43.0F), "out"),
                new ArmKey(0.36F, deg(-89.0F, -39.0F, 0.0F), null, 25.0F, deg(-31.0F, 0.0F, 71.0F), "in"),
                new ArmKey(0.48F, deg(-86.0F, -16.0F, 0.0F), null, -15.0F, deg(-89.0F, 0.0F, 81.0F), "linear"),
                new ArmKey(0.6F, deg(-79.0F, -18.0F, 0.0F), null, -45.0F, deg(-156.0F, 0.0F, 83.0F), "linear"),
                new ArmKey(0.72F, deg(-67.0F, -45.0F, 0.0F), null, -60.0F, deg(-206.0F, 0.0F, 32.0F), "out"),
                new ArmKey(0.86F, deg(-75.0F, -25.0F, 0.0F), null, -25.0F, deg(-119.0F, 0.0F, 90.0F), "inout"))));
        ARM_DEFAULTS.put(CombatPose.LONGSWORD_SPIN_CHARGE, new ArmPose(deg(-112.0F, -41.0F, 0.0F), null, 0.3F, 0.3F, null, List.of(
                new ArmKey(1.0F, deg(-112.0F, -41.0F, 0.0F), null, -50.0F, deg(36.0F, 0.0F, -4.0F), "inout"))));
        ARM_DEFAULTS.put(CombatPose.LONGSWORD_SPIN, new ArmPose(null, null, 0.2F, 0.0F, null, List.of(
                new ArmKey(0.12F, deg(-112.0F, -41.0F, 0.0F), null, -50.0F, deg(36.0F, 0.0F, -4.0F), "out"),
                new ArmKey(0.3F, deg(-81.0F, -30.0F, 0.0F), null, 0.0F, deg(-28.0F, 0.0F, -60.0F), "in"),
                new ArmKey(0.75F, deg(-85.0F, -39.0F, 0.0F), null, 25.0F, deg(-69.0F, 0.0F, -57.0F), "linear"),
                new ArmKey(0.85F, deg(-71.0F, -60.0F, 0.0F), null, 55.0F, deg(-117.0F, 0.0F, -80.0F), "out"))));
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
        Map<CombatPose, Float> loadedRolls = new EnumMap<>(ROLL_DEFAULTS);
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
                        if (element.getAsJsonObject().has("model_roll")) {
                            loadedRolls.put(pose, element.getAsJsonObject().get("model_roll").getAsFloat());
                        }
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
        rolls = loadedRolls;
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
