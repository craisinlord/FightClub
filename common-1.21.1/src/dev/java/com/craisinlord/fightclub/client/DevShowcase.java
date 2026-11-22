package com.craisinlord.fightclub.client;

import com.craisinlord.fightclub.FightClub;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class DevShowcase {
    private static final String TRIGGER = "co_showcase", WORLD = "fc_showcase", ARENA_FILE = "fcs_arena.txt";
    private static final float YAW = 90.0F;
    private static final int ITEM_DELAY = 24;
    private static final int ARENA_RADIUS = 9;

    public static boolean cameraOn;
    public static double camX, camY, camZ;
    public static float camYaw, camPitch;
    public static int hud;

    enum View { FP, TPB, TPF, OUTSIDE }

    private record Foe(String type, double r, double f, boolean hit, String nbt) {}

    private static final class Scene {
        final String name;
        String main, off;
        View view = View.OUTSIDE;
        float pitch = 4.0F, time = -1;
        double lift;
        final List<Foe> mobs = new ArrayList<>();
        final List<String> commands = new ArrayList<>();
        Runnable pose, action;
        int actionAt = -1, wait = 14;
        double[] cam;
        Runnable fx;

        Scene(String name, String main) { this.name = name; this.main = main; }
        Scene off(String item) { off = item; return this; }
        Scene view(View v) { view = v; return this; }
        Scene pitch(float p) { pitch = p; return this; }
        Scene lift(double l) { lift = l; return this; }
        Scene mob(String type, double r, double f, boolean hit) { mobs.add(new Foe(type, r, f, hit, "")); return this; }
        Scene cmd(String c) { commands.add(c); return this; }
        Scene pose(Runnable p) { pose = p; return this; }
        Scene action(int ticksBeforeCapture, Runnable a) { actionAt = ticksBeforeCapture; action = a; return this; }
        final java.util.Map<Integer, Runnable> timeline = new java.util.HashMap<>();
        Scene at(int ticksBeforeCapture, Runnable step) { timeline.merge(ticksBeforeCapture, step, (a, b) -> () -> { a.run(); b.run(); }); return this; }
        Scene wait(int w) { wait = w; return this; }
        Scene cam(double r, double u, double f, double lr, double lu, double lf) { cam = new double[]{r, u, f, lr, lu, lf}; return this; }
        Scene fx(Runnable r) { fx = r; return this; }
    }

    private static Boolean enabled;
    private static boolean worldRequested;
    private static int phase, wait;
    private static volatile boolean arenaReady;
    private static double ax, ay, az;
    private static List<Scene> scenes;
    private static int index = -1, timer;
    private static Scene current;

    private DevShowcase() {}

    static boolean enabled(Minecraft client) {
        if (enabled == null) enabled = new File(client.gameDirectory, TRIGGER).isFile();
        return enabled;
    }

    public static void tick(Minecraft mc) {
        if (!enabled(mc)) return;
        mc.options.pauseOnLostFocus = false;
        if (mc.level != null && mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        if (mc.level == null || mc.player == null) {
            if (!worldRequested && mc.screen instanceof TitleScreen) {
                worldRequested = true;
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1920, 1080);
                if (new File(mc.gameDirectory, "saves/" + WORLD + "/level.dat").isFile()) {
                    mc.createWorldOpenFlows().openWorld(WORLD, () -> {});
                } else {
                    createWorld(mc);
                }
            }
            return;
        }
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) return;
        mc.gui.getChat().clearMessages(false);
        mc.getToasts().clear();
        switch (phase) {
            case 0 -> {
                server.execute(() -> {
                    for (String c : new String[]{"gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "weather clear",
                            "gamerule doMobSpawning false", "gamerule randomTickSpeed 0", "gamerule doEntityDrops false",
                            "gamerule doMobLoot false", "gamerule sendCommandFeedback false", "difficulty easy", "time set 11000",
                            "kill @e[type=!minecraft:player]"}) {
                        run(server, c);
                    }
                });
                File file = new File(mc.gameDirectory, ARENA_FILE);
                if (file.isFile()) {
                    try {
                        String[] p = Files.readString(file.toPath()).trim().split(" ");
                        ax = Double.parseDouble(p[0]); ay = Double.parseDouble(p[1]); az = Double.parseDouble(p[2]);
                        arenaReady = true;
                        wait = 140;
                    } catch (Exception e) {
                        FightClub.LOGGER.error("Showcase: bad arena file", e);
                    }
                }
                if (!arenaReady) {
                    wait = 260;
                    server.execute(() -> buildArena(server, file));
                }
                phase = 1;
            }
            case 1 -> {
                if (!arenaReady) return;
                server.execute(() -> run(server, String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f 0", ax + 0.5, ay, az + 0.5, YAW)));
                phase = 2;
            }
            case 2 -> {
                holdPlayer(mc, 4.0F, 0.0);
                if (--wait > 0) return;
                scenes = filter(mc, buildScenes());
                phase = 3;
            }
            default -> runScenes(mc, server);
        }
    }

    private static void runScenes(Minecraft mc, MinecraftServer server) {
        if (index < 0 || timer <= 0) {
            if (index >= 0) {
                Screenshot.grab(mc.gameDirectory, "fcs_" + current.name + ".png", mc.getMainRenderTarget(), message -> {});
                FightClub.LOGGER.info("Showcase shot {}", current.name);
            }
            index++;
            if (index >= scenes.size()) {
                FightClub.LOGGER.info("Showcase finished: {} screenshots", scenes.size());
                reset(mc);
                mc.stop();
                return;
            }
            start(mc, server, scenes.get(index));
        }
        Scene s = current;
        if (s.action != null && timer == s.actionAt) s.action.run();
        Runnable step = s.timeline.get(timer);
        if (step != null) step.run();
        if (s.fx != null && (timer == 2 || timer == 1)) s.fx.run();
        timer--;
        holdPlayer(mc, s.pitch, s.lift);
        for (Foe m : s.mobs) {
            if (!m.hit()) continue;
            Vec3 at = local(m.r(), 0, m.f());
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof net.minecraft.world.entity.LivingEntity living && !(e instanceof net.minecraft.world.entity.player.Player)
                        && Math.abs(e.getX() - at.x) < 0.6 && Math.abs(e.getZ() - at.z) < 0.6) {
                    living.hurtTime = 5;
                }
            }
        }
        if (s.view == View.OUTSIDE && s.cam != null) {
            Vec3 c = local(s.cam[0], s.cam[1], s.cam[2]);
            Vec3 look = local(s.cam[3], s.cam[4], s.cam[5]);
            Vec3 d = look.subtract(c);
            cameraOn = true;
            camX = c.x; camY = c.y; camZ = c.z;
            camYaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
            camPitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        } else {
            cameraOn = false;
        }
    }

    private static void start(Minecraft mc, MinecraftServer server, Scene s) {
        reset(mc);
        current = s;
        mc.player.getInventory().selected = 0;
        boolean flying = s.lift > 0.0;
        mc.player.getAbilities().flying = flying;
        mc.player.onUpdateAbilities();
        server.execute(() -> {
            ServerLevel level = server.overworld();
            List<Entity> existing = new ArrayList<>();
            level.getAllEntities().forEach(existing::add);
            for (Entity e : existing) {
                if (e.getTags().contains("fcs") || BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace().equals(FightClub.MOD_ID)
                        || e instanceof net.minecraft.world.entity.item.ItemEntity) {
                    e.discard();
                }
            }
            run(server, fill(-3, 0, 6, 3, 3, 6, "air"));
            run(server, "item replace entity @a weapon.mainhand with " + s.main);
            run(server, "item replace entity @a weapon.offhand with " + (s.off == null ? "air" : s.off));
            run(server, String.format(Locale.ROOT, "tp @a %.3f %.3f %.3f %.1f %.1f", ax + 0.5, ay + s.lift, az + 0.5, YAW, s.pitch));
            for (Foe m : s.mobs) {
                Vec3 at = local(m.r(), 0, m.f());
                Vec3 player = local(0, 0, 0);
                float yaw = (float) Math.toDegrees(Math.atan2(-(player.x - at.x), player.z - at.z));
                run(server, String.format(Locale.ROOT, "summon %s %.3f %.3f %.3f {NoAI:1b,Silent:1b,PersistenceRequired:1b,Tags:[\"fcs\"],Rotation:[%.1ff,0f]%s}",
                        m.type(), at.x, at.y, at.z, yaw, mobNbt(m.type())));
            }
            for (String c : s.commands) run(server, c);
            for (Entity e : level.getAllEntities()) {
                if (e instanceof net.minecraft.world.entity.Mob mob && e.getTags().contains("fcs")) mob.setAggressive(true);
            }
        });
        if (s.pose != null) s.pose.run();
        CameraType camera = switch (s.view) {
            case FP -> CameraType.FIRST_PERSON;
            case TPF -> CameraType.THIRD_PERSON_FRONT;
            default -> CameraType.THIRD_PERSON_BACK;
        };
        mc.options.setCameraType(camera);
        mc.options.hideGui = s.view != View.FP;
        hud = s.view == View.FP ? 2 : 1;
        timer = ITEM_DELAY + s.wait;
    }

    private static String mobNbt(String type) {
        return switch (type) {
            case "minecraft:vindicator" -> ",HandItems:[{id:\"minecraft:iron_axe\",count:1},{}]";
            case "minecraft:pillager" -> ",HandItems:[{id:\"minecraft:crossbow\",count:1},{}]";
            case "minecraft:zombie" -> ",ArmorItems:[{},{},{},{id:\"minecraft:iron_helmet\",count:1}]";
            case "minecraft:skeleton" -> ",HandItems:[{id:\"minecraft:bow\",count:1},{}],ArmorItems:[{},{},{},{id:\"minecraft:chainmail_helmet\",count:1}]";
            default -> "";
        };
    }

    private static float aimYaw, aimPitch;

    private static void holdPlayer(Minecraft mc, float pitch, double lift) {
        var p = mc.player;
        float YAW = DevShowcase.YAW + aimYaw;
        pitch += aimPitch;
        p.setYRot(YAW);
        p.yRotO = YAW;
        p.setXRot(pitch);
        p.xRotO = pitch;
        p.xBob = pitch;
        p.xBobO = pitch;
        p.yBob = YAW;
        p.yBobO = YAW;
        p.yHeadRot = YAW;
        p.yHeadRotO = YAW;
        p.yBodyRot = YAW;
        p.yBodyRotO = YAW;
        p.setDeltaMovement(Vec3.ZERO);
        if (lift > 0.0) p.setPos(ax + 0.5, ay + lift, az + 0.5);
    }

    private static void reset(Minecraft mc) {
        CombatClientPoses.debugPose = null;
        CombatClientPoses.debugSwing = -1.0F;
        CombatClientPoses.debugSpin = -1.0F;
        CombatClientPoses.debugBackhand = false;
        CombatInputClient.debugProgress = -1.0F;
        cameraOn = false;
        hud = 0;
        aimYaw = 0.0F;
        aimPitch = 0.0F;
        Minecraft client = Minecraft.getInstance();
        client.options.keyUse.setDown(false);
    }

    private static Vec3 local(double r, double u, double f) {
        double yaw = Math.toRadians(YAW);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        return new Vec3(ax + 0.5 + rx * r + fx * f, ay + u, az + 0.5 + rz * r + fz * f);
    }

    private static String pos(double r, double u, double f) {
        Vec3 v = local(r, u, f);
        return String.format(Locale.ROOT, "%.3f %.3f %.3f", v.x, v.y, v.z);
    }

    private static String fill(int r1, int u1, int f1, int r2, int u2, int f2, String block) {
        Vec3 a = local(r1, u1, f1), b = local(r2, u2, f2);
        return String.format(Locale.ROOT, "fill %d %d %d %d %d %d %s", (int) Math.floor(a.x), (int) Math.floor(a.y), (int) Math.floor(a.z),
                (int) Math.floor(b.x), (int) Math.floor(b.y), (int) Math.floor(b.z), block);
    }

    private static void run(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput().withPermission(4), command);
    }

    private static void createWorld(Minecraft mc) {
        LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.EASY, true,
                new GameRules(), WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(20261121L, false, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.NORMAL)
                        .value().createWorldDimensions(),
                mc.screen);
    }

    private static void buildArena(MinecraftServer server, File file) {
        ServerLevel level = server.overworld();
        var generator = level.getChunkSource().getGenerator();
        var random = level.getChunkSource().randomState();
        List<ResourceKey<Biome>> wanted = List.of(Biomes.CHERRY_GROVE, Biomes.MEADOW, Biomes.FLOWER_FOREST, Biomes.SUNFLOWER_PLAINS, Biomes.PLAINS);
        int[] best = null;
        int bestRank = wanted.size();
        int sx = level.getSharedSpawnPos().getX(), sz = level.getSharedSpawnPos().getZ();
        search:
        for (int ring = 0; ring <= 60; ring++) {
            for (int i = -ring; i <= ring; i++) {
                for (int[] d : new int[][]{{i, -ring}, {i, ring}, {-ring, i}, {ring, i}}) {
                    if (ring > 0 && Math.abs(d[0]) != ring && Math.abs(d[1]) != ring) continue;
                    int x = sx + d[0] * 40, z = sz + d[1] * 40;
                    Holder<Biome> biome = generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(80), QuartPos.fromBlock(z), random.sampler());
                    int rank = -1;
                    for (int k = 0; k < bestRank; k++) if (biome.is(wanted.get(k))) rank = k;
                    if (rank < 0) continue;
                    int h = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random);
                    if (h < 64 || generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, random) != h) continue;
                    boolean flat = true;
                    for (int a = 0; a < 12 && flat; a++) {
                        double ang = a * Math.PI / 6;
                        int px = x + (int) Math.round(Math.cos(ang) * 14), pz = z + (int) Math.round(Math.sin(ang) * 14);
                        int ph = generator.getBaseHeight(px, pz, Heightmap.Types.OCEAN_FLOOR_WG, level, random);
                        int pw = generator.getBaseHeight(px, pz, Heightmap.Types.WORLD_SURFACE_WG, level, random);
                        flat = Math.abs(ph - h) <= 3 && pw == ph;
                    }
                    if (!flat) continue;
                    best = new int[]{x, h, z};
                    bestRank = rank;
                    FightClub.LOGGER.info("Showcase: candidate {} at {} {} {}", wanted.get(rank).location(), x, h, z);
                    if (rank == 0) break search;
                }
            }
        }
        if (best == null) best = new int[]{sx, generator.getBaseHeight(sx, sz, Heightmap.Types.WORLD_SURFACE_WG, level, random), sz};
        int x = best[0], z = best[2];
        for (int cx = (x - ARENA_RADIUS - 8) >> 4; cx <= (x + ARENA_RADIUS + 8) >> 4; cx++) {
            for (int cz = (z - ARENA_RADIUS - 8) >> 4; cz <= (z + ARENA_RADIUS + 8) >> 4; cz++) level.getChunk(cx, cz);
        }
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        ax = x; ay = top; az = z;
        int y = top;
        for (int dz = -ARENA_RADIUS; dz <= ARENA_RADIUS; dz++) {
            int half = (int) Math.floor(Math.sqrt(ARENA_RADIUS * ARENA_RADIUS - dz * dz));
            run(server, String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", x - half, y, z + dz, x + half, y + 20, z + dz));
            run(server, String.format(Locale.ROOT, "fill %d %d %d %d %d %d grass_block", x - half, y - 1, z + dz, x + half, y - 1, z + dz));
            run(server, String.format(Locale.ROOT, "fill %d %d %d %d %d %d dirt replace air", x - half, y - 5, z + dz, x + half, y - 2, z + dz));
        }
        var rand = net.minecraft.util.RandomSource.create(7L);
        for (int dx = -ARENA_RADIUS; dx <= ARENA_RADIUS; dx++) {
            for (int dz = -ARENA_RADIUS; dz <= ARENA_RADIUS; dz++) {
                if (dx * dx + dz * dz > ARENA_RADIUS * ARENA_RADIUS || rand.nextFloat() > 0.45F) continue;
                var state = net.minecraft.world.level.block.Blocks.PINK_PETALS.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.PinkPetalsBlock.AMOUNT, 1 + rand.nextInt(4))
                        .setValue(net.minecraft.world.level.block.PinkPetalsBlock.FACING, net.minecraft.core.Direction.Plane.HORIZONTAL.getRandomDirection(rand));
                level.setBlock(new net.minecraft.core.BlockPos(x + dx, y, z + dz), state, 2);
            }
        }
        run(server, String.format(Locale.ROOT, "setworldspawn %d %d %d", x, y, z));
        try {
            Files.writeString(file.toPath(), String.format(Locale.ROOT, "%d %d %d", x, y, z));
        } catch (java.io.IOException e) {
            FightClub.LOGGER.error("Showcase: couldn't save the arena", e);
        }
        FightClub.LOGGER.info("Showcase arena at {} {} {} ({})", x, y, z, bestRank < wanted.size() ? wanted.get(bestRank).location() : "spawn");
        arenaReady = true;
    }

    private static List<Scene> filter(Minecraft mc, List<Scene> all) {
        List<String> keep = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(new File(mc.gameDirectory, TRIGGER).toPath())) {
                if (!line.isBlank()) keep.add(line.trim());
            }
        } catch (java.io.IOException ignored) {
        }
        if (keep.isEmpty()) return all;
        List<Scene> result = new ArrayList<>();
        for (Scene s : all) if (keep.stream().anyMatch(s.name::contains)) result.add(s);
        return result;
    }

    private static String spear(String material) {
        if (com.craisinlord.fightclub.combat.CombatWeapons.backportSpears()) {
            return BuiltInRegistries.ITEM.getKey(com.craisinlord.fightclub.combat.CombatWeapons.backportSpear(material)).toString();
        }
        return "fight_club:" + material + "_spear";
    }

    private static void particle(net.minecraft.core.particles.ParticleOptions type, double r, double u, double f, double vx, double vy, double vz) {
        Vec3 p = local(r, u, f);
        Minecraft.getInstance().level.addParticle(type, p.x, p.y, p.z, vx, vy, vz);
    }

    private static Runnable crits(double r, double u, double f, int count) {
        return () -> {
            var rand = Minecraft.getInstance().level.random;
            for (int i = 0; i < count; i++) {
                particle(ParticleTypes.CRIT, r + rand.nextGaussian() * 0.25, u + rand.nextGaussian() * 0.3, f + rand.nextGaussian() * 0.25,
                        rand.nextGaussian() * 0.4, rand.nextDouble() * 0.4, rand.nextGaussian() * 0.4);
            }
        };
    }

    private static Runnable all(Runnable... rs) {
        return () -> { for (Runnable r : rs) r.run(); };
    }

    private static Runnable sweep(double r, double u, double f) {
        return () -> particle(ParticleTypes.SWEEP_ATTACK, r, u, f, 0, 0, 0);
    }

    private static List<Scene> buildScenes() {
        List<Scene> list = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        String netherLs = "fight_club:netherite_longsword", diaLs = "fight_club:diamond_longsword", goldLs = "fight_club:gold_longsword", ironLs = "fight_club:iron_longsword";

        list.add(new Scene("01_longsword_sweep_outside", netherLs)
                .mob("minecraft:husk", -1.4, 2.0, true).mob("minecraft:vindicator", -0.2, 2.6, true).mob("minecraft:zombie", -1.3, 3.5, true)
                .pose(() -> CombatClientPoses.debugSwing = 0.50F)
                .cam(3.6, 1.5, 1.9, -0.2, 1.0, 1.5)
                .fx(all(sweep(-0.4, 1.1, 1.8), sweep(0.8, 1.1, 1.6), crits(-0.1, 1.3, 2.5, 4))));
        list.add(new Scene("02_longsword_sweep_fp", diaLs).view(View.FP).pitch(4.0F)
                .mob("minecraft:husk", -1.9, 3.0, true).mob("minecraft:vindicator", 0.0, 3.4, true).mob("minecraft:zombie", 2.0, 3.0, false)
                .pose(() -> CombatClientPoses.debugSwing = 0.45F)
                .fx(all(sweep(-0.4, 1.3, 2.2), crits(0.0, 1.4, 3.3, 4))));
        list.add(new Scene("03_longsword_backhand_tpb", goldLs).view(View.TPB).pitch(10.0F)
                .mob("minecraft:husk", -1.7, 2.3, true).mob("minecraft:pillager", 0.2, 3.0, true).mob("minecraft:zombie", 1.9, 2.2, true)
                .pose(() -> { CombatClientPoses.debugBackhand = true; CombatClientPoses.debugSwing = 0.50F; })
                .fx(all(sweep(0.0, 1.1, 1.9), crits(0.2, 1.3, 3.0, 4))));
        list.add(new Scene("04_longsword_backhand_outside", ironLs)
                .mob("minecraft:vindicator", 1.4, 2.0, true).mob("minecraft:husk", 0.2, 2.6, true).mob("minecraft:pillager", 1.3, 3.5, false)
                .pose(() -> { CombatClientPoses.debugBackhand = true; CombatClientPoses.debugSwing = 0.42F; })
                .cam(-3.6, 1.5, 1.9, 0.2, 1.0, 1.5)
                .fx(all(sweep(0.6, 1.1, 1.7), crits(1.5, 1.3, 1.9, 4))));

        Scene spin = new Scene("05_longsword_spin_outside", netherLs)
                .pose(() -> CombatClientPoses.debugSpin = 0.45F)
                .cam(3.4, 4.0, 3.0, 0.0, 0.6, 0.0);
        String[] ring = {"minecraft:husk", "minecraft:vindicator", "minecraft:zombie", "minecraft:pillager", "minecraft:husk", "minecraft:vindicator"};
        List<Runnable> spinFx = new ArrayList<>();
        for (int i = 0; i < ring.length; i++) {
            double ang = Math.toRadians(i * 60.0 + 20.0);
            spin.mob(ring[i], Math.sin(ang) * 2.4, Math.cos(ang) * 2.4, true);
            spinFx.add(sweep(Math.sin(ang) * 1.4, 1.0, Math.cos(ang) * 1.4));
        }
        list.add(spin.fx(all(spinFx.toArray(new Runnable[0]))));
        list.add(new Scene("06_longsword_spin_charge_tpf", diaLs).view(View.TPF).pitch(12.0F)
                .mob("minecraft:husk", -2.4, -1.0, false).mob("minecraft:vindicator", 2.4, -0.6, false)
                .pose(() -> CombatClientPoses.debugPose = CombatPose.LONGSWORD_SPIN_CHARGE));

        list.add(new Scene("07_shield_bash_outside", "minecraft:iron_sword").off("minecraft:shield")
                .mob("minecraft:vindicator", 0.0, 1.5, true)
                .action(3, () -> CombatClientPoses.playBash(mc.player))
                .cam(-3.4, 1.4, 1.4, 0.0, 1.1, 0.8)
                .fx(crits(0.0, 1.3, 1.3, 5)));
        list.add(new Scene("08_shield_bash_fp", "minecraft:diamond_sword").off("minecraft:shield").view(View.FP).pitch(6.0F)
                .mob("minecraft:vindicator", 0.0, 2.0, true)
                .action(3, () -> CombatClientPoses.playBash(mc.player))
                .fx(crits(0.0, 1.4, 1.8, 5)));
        list.add(new Scene("09_sword_block_tpf", "minecraft:netherite_sword").view(View.TPF).pitch(6.0F)
                .mob("minecraft:vindicator", 2.6, 0.2, false)
                .pose(() -> CombatClientPoses.debugPose = CombatPose.SWORD_BLOCK));
        list.add(new Scene("10_sword_block_fp", "minecraft:diamond_sword").view(View.FP).pitch(4.0F)
                .mob("minecraft:vindicator", 0.3, 2.2, false)
                .pose(() -> CombatClientPoses.debugPose = CombatPose.SWORD_BLOCK));

        String hayWall = fill(-3, 0, 6, 3, 2, 6, "hay_block");
        list.add(new Scene("11_spear_charge_outside", spear("diamond")).pitch(6.0F)
                .mob("minecraft:pillager", -0.3, 3.4, false).mob("minecraft:husk", 0.9, 4.2, false)
                .pose(() -> { CombatClientPoses.debugPose = CombatPose.SPEAR_LOWERED; CombatInputClient.debugProgress = 1.0F; })
                .cam(3.8, 1.2, 1.0, -0.2, 1.0, 2.0));
        list.add(new Scene("12_spear_charge_fp", spear("netherite")).view(View.FP).pitch(4.0F)
                .mob("minecraft:pillager", -0.5, 4.0, false).mob("minecraft:husk", 1.1, 5.0, false)
                .pose(() -> { CombatClientPoses.debugPose = CombatPose.SPEAR_LOWERED; CombatInputClient.debugProgress = 0.7F; }));
        for (double[] cam : new double[][]{{1.9, 1.75, -1.3, -0.3, 1.4, 4.0}, {4.2, 1.7, 3.6, -0.4, 1.4, 5.4}}) {
        Scene thrown = new Scene(cam[0] < 3 ? "13_thrown_spears_outside" : "13b_thrown_spears_side", spear("iron")).wait(150)
                .cmd(hayWall)
                .cam(cam[0], cam[1], cam[2], cam[3], cam[4], cam[5]);
        Object[][] throwsList = {{spear("iron"), -14F, -2F}, {spear("diamond"), 9F, -7F}, {spear("gold"), -3F, 2F},
                {"fight_club:iron_dagger", 15F, -1F}, {spear("netherite"), 2F, -9F}};
        int t = 140;
        for (Object[] th : throwsList) {
            String item = (String) th[0];
            float yaw = (Float) th[1], pitch = (Float) th[2];
            thrown.at(t, () -> {
                aimYaw = yaw;
                aimPitch = pitch;
                MinecraftServer server = mc.getSingleplayerServer();
                server.execute(() -> run(server, "item replace entity @a weapon.mainhand with " + item));
            });
            thrown.at(t - 6, () -> {
                mc.options.keyUse.setDown(true);
                FightClub.LOGGER.info("Showcase throw use {} -> {}", mc.player.getMainHandItem(), mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND));
            });
            thrown.at(t - 22, () -> {
                FightClub.LOGGER.info("Showcase throw release using={} ticks={}", mc.player.isUsingItem(), mc.player.getTicksUsingItem());
                mc.options.keyUse.setDown(false);
            });
            thrown.at(t - 25, () -> FightClub.LOGGER.info("Showcase projectiles {}", mc.level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,
                    mc.player.getBoundingBox().inflate(12), e -> e instanceof com.craisinlord.fightclub.combat.CombatProjectile).size()));
            t -= 26;
        }
        thrown.at(t, () -> {
            aimYaw = 0.0F;
            aimPitch = 0.0F;
            MinecraftServer server = mc.getSingleplayerServer();
            server.execute(() -> run(server, "item replace entity @a weapon.mainhand with fight_club:diamond_dagger"));
        });
        thrown.at(t - 8, () -> CombatClientPoses.debugPose = CombatPose.DAGGER_THROW);
        list.add(thrown);
        }
        list.add(new Scene("14_spear_stab_outside", spear("iron")).pitch(6.0F)
                .mob("minecraft:vindicator", 0.0, 2.4, true)
                .pose(() -> CombatClientPoses.debugSwing = 0.30F)
                .cam(3.6, 1.3, 1.6, -0.2, 1.2, 1.4)
                .fx(crits(0.0, 1.4, 2.2, 4)));
        list.add(new Scene("15_trident_charge_tpf", "minecraft:trident").view(View.TPF).pitch(8.0F)
                .mob("minecraft:husk", 2.2, 0.6, false)
                .pose(() -> { CombatClientPoses.debugPose = CombatPose.TRIDENT_LOWERED; CombatInputClient.debugProgress = 1.0F; }));

        list.add(new Scene("16_dagger_charge_fp", "fight_club:netherite_dagger").view(View.FP).pitch(4.0F)
                .mob("minecraft:pillager", 0.2, 4.5, false)
                .pose(() -> { CombatClientPoses.debugPose = CombatPose.DAGGER_CHARGE; CombatInputClient.debugProgress = 1.0F; }));
        list.add(new Scene("17_dagger_charge_tpf", "fight_club:gold_dagger").view(View.TPF).pitch(8.0F)
                .mob("minecraft:pillager", -2.2, 1.0, false)
                .pose(() -> { CombatClientPoses.debugPose = CombatPose.DAGGER_CHARGE; CombatInputClient.debugProgress = 1.0F; }));
        list.add(new Scene("18_dagger_pounce_outside", "fight_club:diamond_dagger").lift(0.8)
                .mob("minecraft:pillager", 0.0, 2.2, true)
                .pose(() -> CombatClientPoses.debugPose = CombatPose.DAGGER_POUNCE)
                .cam(3.4, 1.6, 1.6, -0.2, 1.4, 1.2)
                .fx(crits(0.0, 1.4, 2.0, 5)));

        list.add(new Scene("19_mace_swing_outside", "minecraft:mace").wait(10)
                .mob("minecraft:husk", 0.0, 1.8, true)
                .action(3, () -> mc.player.swing(InteractionHand.MAIN_HAND))
                .cam(-3.2, 1.5, 1.6, 0.0, 1.0, 0.9)
                .fx(crits(0.0, 1.3, 1.6, 6)));
        list.add(new Scene("20_mace_swing_fp", "minecraft:mace").view(View.FP).pitch(8.0F)
                .mob("minecraft:husk", 0.0, 2.4, true)
                .pose(() -> CombatClientPoses.debugSwing = 0.55F)
                .fx(crits(0.0, 1.4, 2.2, 6)));
        return list;
    }
}
