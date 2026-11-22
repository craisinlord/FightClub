package com.craisinlord.fightclub.client;

import com.craisinlord.fightclub.FightClub;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class DevPoseGallery {
    private static final int SETTLE_TICKS = 12;
    private static final int SEQUENCE_FRAMES = 16;
    private static final int ITEM_DELAY_TICKS = 30;

    private record Shot(String name, String mainItem, String offItem, CameraType camera, boolean gui,
                        Runnable setup, int waitTicks, Runnable check) {
        Shot(String name, String mainItem, String offItem, CameraType camera, boolean gui, Runnable setup, int waitTicks) {
            this(name, mainItem, offItem, camera, gui, setup, waitTicks, null);
        }
    }

    private static Boolean enabled;
    private static boolean worldRequested;
    private static List<Shot> shots;
    private static int index = -1;
    private static int timer;
    private static int warmup = 60;
    private static Float bodyYaw;
    private static Shot previous;
    private static Float walkPhase;
    private static Float viewPitch;

    private DevPoseGallery() {}

    private static final List<Object[]> LATER = new ArrayList<>();

    private static void later(int ticks, Runnable step) {
        LATER.add(new Object[]{ticks, step});
    }

    private static void runLater() {
        for (Object[] entry : new ArrayList<>(LATER)) {
            int left = (Integer) entry[0] - 1;
            entry[0] = left;
            if (left <= 0) {
                LATER.remove(entry);
                ((Runnable) entry[1]).run();
            }
        }
    }

    private static void pressFor(Minecraft mc, int ticks) {
        net.minecraft.client.KeyMapping.click(mc.options.keyAttack.getDefaultKey());
        mc.options.keyAttack.setDown(true);
        later(ticks, () -> mc.options.keyAttack.setDown(false));
    }

    static boolean enabled(Minecraft client) {
        if (enabled == null) enabled = new File(client.gameDirectory, "co_pose_gallery").isFile();
        return enabled;
    }

    public static void tick(Minecraft client) {
        if (!enabled(client)) return;
        client.options.pauseOnLostFocus = false;
        if (client.level != null && client.screen instanceof net.minecraft.client.gui.screens.PauseScreen) client.setScreen(null);
        if (client.level == null || client.player == null) {
            if (!worldRequested && client.screen instanceof TitleScreen) {
                worldRequested = true;
                createWorld(client);
            }
            return;
        }
        if (warmup > 0) {
            if (warmup == 60) {
                command(client, "gamerule doDaylightCycle false");
                command(client, "time set 6000");
                command(client, "weather clear");
                command(client, "gamerule doMobSpawning false");
                command(client, "gamerule sendCommandFeedback false");
            }
            warmup--;
            return;
        }
        runLater();
        if (shots == null) shots = filter(client, buildShots());
        if (index < 0 || timer <= 0) {
            if (index >= 0) capture(client, shots.get(index));
            index++;
            if (index >= shots.size()) {
                FightClub.LOGGER.info("Pose gallery finished: {} screenshots", shots.size());
                reset();
                client.stop();
                return;
            }
            start(client, shots.get(index));
        }
        Shot current = shots.get(index);
        if (timer == current.waitTicks() && current.setup() != null) current.setup().run();
        timer--;
        client.gui.getChat().clearMessages(false);
        client.getToasts().clear();
        client.player.setYRot(0.0F);
        float pitch = viewPitch != null ? viewPitch : 8.0F;
        client.player.setXRot(pitch);
        client.player.xRotO = pitch;
        client.player.xBob = pitch;
        client.player.xBobO = pitch;
        client.player.yBob = client.player.getYRot();
        client.player.yBobO = client.player.getYRot();
        client.player.yHeadRot = 0.0F;
        float body = client.options.getCameraType() == CameraType.FIRST_PERSON ? 0.0F : bodyYaw != null ? bodyYaw : 45.0F;
        client.player.yBodyRot = body;
        client.player.yBodyRotO = body;
        if (walkPhase != null) {
            client.player.walkDist = walkPhase;
            client.player.walkDistO = walkPhase;
            client.player.bob = 0.1F;
            client.player.oBob = 0.1F;
        }
        if (bodyYaw != null && client.options.getCameraType() != CameraType.FIRST_PERSON) {
            client.player.yHeadRot = body;
            client.player.yHeadRotO = body;
        }
    }

    private static void reset() {
        com.craisinlord.fightclub.combat.CombatTooltips.setShiftSupplier(net.minecraft.client.gui.screens.Screen::hasShiftDown);
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof TooltipScreen) client.setScreen(null);
        CombatClientPoses.debugPose = null;
        CombatClientPoses.debugSwing = -1.0F;
        CombatClientPoses.debugSpin = -1.0F;
        CombatClientPoses.debugBackhand = false;
        CombatInputClient.debugProgress = -1.0F;
        bodyYaw = null;
        walkPhase = null;
        viewPitch = null;
    }

    private static void start(Minecraft client, Shot shot) {
        reset();
        if (client.player != null) client.player.getInventory().selected = 0;
        command(client, "item replace entity @s weapon.mainhand with " + shot.mainItem());
        command(client, "item replace entity @s weapon.offhand with " + (shot.offItem() == null ? "air" : shot.offItem()));
        client.options.setCameraType(shot.camera());
        client.options.hideGui = !shot.gui();
        boolean sameItems = previous != null && previous.mainItem().equals(shot.mainItem())
                && java.util.Objects.equals(previous.offItem(), shot.offItem()) && (shot.name().startsWith("ls_") || shot.name().startsWith("jab_fp_") || shot.name().startsWith("mace_"));
        previous = shot;
        timer = (sameItems ? 2 : ITEM_DELAY_TICKS) + shot.waitTicks();
    }

    private static void capture(Minecraft client, Shot shot) {
        if (shot.check() != null) shot.check().run();
        if (client.player != null) {
            FightClub.LOGGER.info("Pose gallery shot {}: swinging={} swingTime={} attackAnim={} item={}", shot.name(),
                    client.player.swinging, client.player.swingTime, client.player.getAttackAnim(0.0F),
                    client.player.getMainHandItem().getItem());
            if (client.getEntityRenderDispatcher().getRenderer(client.player) instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer renderer) {
                var model = renderer.getModel();
                FightClub.LOGGER.info("Pose gallery arms {}: right {} {} {} at {} {} {} body {} left {} {} {}", shot.name(),
                        Math.toDegrees(model.rightArm.xRot), Math.toDegrees(model.rightArm.yRot), Math.toDegrees(model.rightArm.zRot),
                        model.rightArm.x, model.rightArm.y, model.rightArm.z, Math.toDegrees(model.body.yRot),
                        Math.toDegrees(model.leftArm.xRot), Math.toDegrees(model.leftArm.yRot), Math.toDegrees(model.leftArm.zRot));
            }
        }
        Screenshot.grab(client.gameDirectory, "co_" + shot.name() + ".png", client.getMainRenderTarget(), message -> {});
    }

    private static void command(Minecraft client, String command) {
        if (client.player != null) client.player.connection.sendCommand(command);
    }

    private static void createWorld(Minecraft client) {
        String name = "co_pose_gallery_" + System.currentTimeMillis();
        LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                new GameRules(), WorldDataConfiguration.DEFAULT);
        client.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(0L, false, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                        .value().createWorldDimensions(),
                client.screen);
    }

    private static List<Shot> filter(Minecraft client, List<Shot> all) {
        List<String> keep = new ArrayList<>();
        try {
            for (String line : java.nio.file.Files.readAllLines(new File(client.gameDirectory, "co_pose_gallery").toPath())) {
                if (!line.isBlank()) keep.add(line.trim());
            }
        } catch (java.io.IOException ignored) {
        }
        if (keep.isEmpty()) return all;
        List<Shot> result = new ArrayList<>();
        for (Shot shot : all) {
            if (keep.stream().anyMatch(shot.name()::contains)) result.add(shot);
        }
        return result;
    }

    private static void addLongswordShots(List<Shot> list, String longsword, String sword) {
        float[] swingFrames = {0.10F, 0.22F, 0.30F, 0.38F, 0.46F, 0.58F, 0.72F, 0.86F};
        float[] spinFrames = {0.08F, 0.20F, 0.35F, 0.50F, 0.65F, 0.80F, 0.92F};
        Object[][] views = {{"fp", CameraType.FIRST_PERSON, 0.0F}, {"tpb", CameraType.THIRD_PERSON_BACK, 20.0F},
                {"tpf", CameraType.THIRD_PERSON_FRONT, 35.0F}, {"tps", CameraType.THIRD_PERSON_FRONT, 90.0F}};
        for (Object[] v : views) {
            String view = (String) v[0];
            CameraType camera = (CameraType) v[1];
            float yaw = (Float) v[2];
            boolean fp = camera == CameraType.FIRST_PERSON;
            if (fp) {
                for (String[] item : new String[][]{{"longsword", longsword}, {"sword", sword}}) {
                    list.add(new Shot("ls_fp_idle_" + item[0], item[1], null, camera, true, null, SETTLE_TICKS));
                    for (float phase : new float[]{0.0F, 0.5F, 1.0F, 1.5F}) {
                        list.add(new Shot("ls_fp_walk_" + item[0] + "_" + Math.round(phase * 10), item[1], null, camera, true,
                                () -> walkPhase = phase, 4));
                    }
                }
            }
            for (float frame : swingFrames) {
                list.add(new Shot("ls_" + view + "_swing_" + Math.round(frame * 100), longsword, null, camera, fp, () -> {
                    bodyYaw = yaw;
                    CombatClientPoses.debugSwing = frame;
                }, 4));
            }
            for (float frame : swingFrames) {
                list.add(new Shot("ls_" + view + "_back_" + Math.round(frame * 100), longsword, null, camera, fp, () -> {
                    bodyYaw = yaw;
                    CombatClientPoses.debugBackhand = true;
                    CombatClientPoses.debugSwing = frame;
                }, 4));
            }
            list.add(new Shot("ls_" + view + "_hold", longsword, null, camera, fp, () -> bodyYaw = yaw, SETTLE_TICKS));
            Object[][] smearPitches = fp ? new Object[][]{{"up", -30.0F}, {"level", 0.0F}, {"down", 30.0F}, {"steep", 60.0F}}
                    : new Object[][]{{"level", 0.0F}};
            for (Object[] p : smearPitches) {
                float pitch = (Float) p[1];
                for (int delay : new int[]{4, 6, 8, 10}) {
                    list.add(new Shot("ls_" + view + "_smeargap_" + p[0] + "_" + delay, longsword, null, camera, fp, () -> {
                        bodyYaw = yaw;
                        viewPitch = pitch;
                    }, 14));
                    list.add(new Shot("ls_" + view + "_smear_" + p[0] + "_" + delay, longsword, null, camera, fp, () -> {
                        bodyYaw = yaw;
                        viewPitch = pitch;
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.player != null) mc.player.swing(InteractionHand.MAIN_HAND);
                    }, delay));
                }
            }
            if (fp) {
                Object[][] pitches = {{"up", -30.0F}, {"level", 0.0F}, {"down", 30.0F}};
                for (Object[] p : pitches) {
                    for (boolean back : new boolean[]{false, true}) {
                        for (int k = 0; k < SEQUENCE_FRAMES; k++) {
                            float frame = (k + 0.5F) / SEQUENCE_FRAMES;
                            float pitch = (Float) p[1];
                            list.add(new Shot(String.format(java.util.Locale.ROOT, "ls_seq_%s_%s_%02d", back ? "back" : "fore", p[0], k),
                                    longsword, null, camera, true, () -> {
                                        viewPitch = pitch;
                                        CombatClientPoses.debugBackhand = back;
                                        CombatClientPoses.debugSwing = frame;
                                    }, 4));
                        }
                    }
                }
            }
            for (float frame : spinFrames) {
                list.add(new Shot("ls_" + view + "_spin_" + Math.round(frame * 100), longsword, null, camera, fp, () -> {
                    bodyYaw = yaw;
                    CombatClientPoses.debugSpin = frame;
                }, 4));
            }
            list.add(new Shot("ls_" + view + "_spincharge", longsword, null, camera, fp, () -> {
                bodyYaw = yaw;
                CombatClientPoses.debugPose = CombatPose.LONGSWORD_SPIN_CHARGE;
            }, SETTLE_TICKS));
            list.add(new Shot("block_" + view, sword, null, camera, fp, () -> {
                bodyYaw = yaw;
                CombatClientPoses.debugPose = CombatPose.SWORD_BLOCK;
            }, SETTLE_TICKS));
        }
    }

    private static void addMaceShots(List<Shot> list) {
        float[] frames = {0.0F, 0.12F, 0.22F, 0.30F, 0.38F, 0.44F, 0.48F, 0.55F, 0.62F, 0.75F, 0.88F};
        Object[][] views = {{"fp", CameraType.FIRST_PERSON, 0.0F}, {"tpb", CameraType.THIRD_PERSON_BACK, 20.0F},
                {"tpf", CameraType.THIRD_PERSON_FRONT, 35.0F}, {"tps", CameraType.THIRD_PERSON_FRONT, 90.0F}};
        for (Object[] v : views) {
            String view = (String) v[0];
            CameraType camera = (CameraType) v[1];
            float yaw = (Float) v[2];
            boolean fp = camera == CameraType.FIRST_PERSON;
            for (float frame : frames) {
                list.add(new Shot("mace_" + view + "_swing_" + Math.round(frame * 100), "minecraft:mace", null, camera, fp, () -> {
                    bodyYaw = yaw;
                    if (frame > 0.0F) CombatClientPoses.debugSwing = frame;
                }, 4));
            }
        }
    }

    private static void addThrustShots(List<Shot> list, String spear) {
        float[] jabFrames = {0.03F, 0.10F, 0.20F, 0.30F, 0.45F, 0.60F, 0.80F, 0.95F};
        Object[][] views = {{"fp", CameraType.FIRST_PERSON, 0.0F}, {"tpf", CameraType.THIRD_PERSON_FRONT, 35.0F},
                {"tps", CameraType.THIRD_PERSON_FRONT, 90.0F}};
        String[][] items = {{"spear", spear}, {"trident", "minecraft:trident"}};
        for (String[] item : items) {
            boolean trident = item[0].equals("trident");
            CombatPose charge = trident ? CombatPose.TRIDENT_LOWERED : CombatPose.SPEAR_LOWERED;
            for (Object[] v : views) {
                String view = (String) v[0];
                CameraType camera = (CameraType) v[1];
                float yaw = (Float) v[2];
                boolean fp = camera == CameraType.FIRST_PERSON;
                list.add(new Shot("thrust_" + item[0] + "_" + view + "_idle", item[1], null, camera, fp, () -> bodyYaw = yaw, SETTLE_TICKS));
                for (float frame : jabFrames) {
                    list.add(new Shot("thrust_" + item[0] + "_" + view + "_jab_" + Math.round(frame * 100), item[1], null, camera, fp, () -> {
                        bodyYaw = yaw;
                        CombatClientPoses.debugSwing = frame;
                    }, 4));
                }
                for (int ticks : new int[]{1, 2, 3, 5, 12, 40}) {
                    list.add(new Shot("thrust_" + item[0] + "_" + view + "_charge_" + ticks, item[1], null, camera, fp, () -> {
                        bodyYaw = yaw;
                        CombatClientPoses.debugPose = charge;
                        CombatInputClient.debugProgress = Math.min(1.0F, ticks / 20.0F);
                    }, ticks));
                }
            }
        }
    }

    private static List<Shot> buildShots() {
        List<Shot> list = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        CameraType fp = CameraType.FIRST_PERSON;
        CameraType tp = CameraType.THIRD_PERSON_FRONT;
        String sword = "minecraft:iron_sword", spear = com.craisinlord.fightclub.combat.CombatWeapons.backportSpears() ? "minecraft:iron_spear" : "fight_club:iron_spear", dagger = "fight_club:iron_dagger";
        String longsword = "fight_club:iron_longsword";
        addLongswordShots(list, longsword, sword);
        addMaceShots(list);
        addThrustShots(list, spear);
        list.add(new Shot("jab_fp_idle", dagger, null, fp, true, null, SETTLE_TICKS));
        for (float frame : new float[]{0.10F, 0.20F, 0.30F, 0.40F, 0.50F, 0.62F, 0.75F, 0.88F}) {
            list.add(new Shot("jab_fp_" + Math.round(frame * 100), dagger, null, fp, true,
                    () -> CombatClientPoses.debugSwing = frame, 4));
        }

        for (CameraType camera : new CameraType[]{fp, tp}) {
            String view = camera == fp ? "fp" : "tp";
            list.add(new Shot(view + "_idle_sword", sword, null, camera, camera == fp, null, SETTLE_TICKS));
            list.add(new Shot(view + "_sword_block", sword, null, camera, camera == fp, pose(CombatPose.SWORD_BLOCK), SETTLE_TICKS));
            list.add(new Shot(view + "_spear_lowered", spear, null, camera, camera == fp, pose(CombatPose.SPEAR_LOWERED), SETTLE_TICKS));
            list.add(new Shot(view + "_trident_lowered", "minecraft:trident", null, camera, camera == fp, pose(CombatPose.TRIDENT_LOWERED), SETTLE_TICKS));
            list.add(new Shot(view + "_dagger_charge", dagger, null, camera, camera == fp, () -> {
                CombatClientPoses.debugPose = CombatPose.DAGGER_CHARGE;
                CombatInputClient.debugProgress = 1.0F;
            }, SETTLE_TICKS));
            list.add(new Shot(view + "_dagger_pounce", dagger, null, camera, camera == fp, pose(CombatPose.DAGGER_POUNCE), SETTLE_TICKS));
            list.add(new Shot(view + "_dagger_throw", dagger, null, camera, camera == fp, pose(CombatPose.DAGGER_THROW), SETTLE_TICKS));
            list.add(new Shot(view + "_longsword_idle", longsword, null, camera, camera == fp, null, SETTLE_TICKS));
            list.add(new Shot(view + "_idle_dagger", dagger, null, camera, camera == fp, null, SETTLE_TICKS));
            list.add(new Shot(view + "_jab_dagger", dagger, null, camera, camera == fp, swing(mc), 2));
            list.add(new Shot(view + "_idle_spear", spear, null, camera, camera == fp, null, SETTLE_TICKS));
            list.add(new Shot(view + "_jab_spear", spear, null, camera, camera == fp, swing(mc), 3));
            list.add(new Shot(view + "_swing_longsword", longsword, null, camera, camera == fp, swing(mc), 4));
            list.add(new Shot(view + "_shield_bash", sword, "minecraft:shield", camera, camera == fp, () -> {
                if (mc.player != null) CombatClientPoses.playBash(mc.player);
            }, 2));
            list.add(new Shot(view + "_recovery_dip", dagger, null, camera, camera == fp, () -> {
                if (mc.player != null) mc.player.getCooldowns().addCooldown(mc.player.getMainHandItem().getItem(), 40);
            }, 6));
        }
        list.add(new Shot("thrown_weapons", longsword, null, fp, true, () -> {
            command(mc, "item replace entity @s hotbar.1 with " + spear);
            command(mc, "item replace entity @s hotbar.2 with " + dagger);
            command(mc, "item replace entity @s hotbar.3 with minecraft:trident");
            command(mc, "setblock ~2 ~1 ~4 minecraft:stone");
            command(mc, "setblock ~-2 ~1 ~4 minecraft:stone");
            command(mc, "summon fight_club:spear_projectile ~1.5 ~1.5 ~4 {Embedded:1b,NoGravity:1b,Rotation:[90f,-15f],Item:{id:\"" + spear + "\",count:1}}");
            command(mc, "summon fight_club:dagger_projectile ~-1.5 ~1.5 ~4 {Embedded:1b,NoGravity:1b,Rotation:[-90f,-10f],Item:{id:\"" + dagger + "\",count:1}}");
            command(mc, "summon fight_club:dagger_projectile ~0 ~1.7 ~3.5 {NoGravity:1b,Motion:[0.001d,0d,0d],Item:{id:\"" + dagger + "\",count:1}}");
        }, SETTLE_TICKS + 3));
        String[][] pairs = {{longsword, spear}, {dagger, sword}, {"minecraft:iron_axe", "minecraft:mace"}, {"minecraft:trident", "minecraft:shield"}};
        for (boolean shift : new boolean[]{false, true}) {
            for (String[] pair : pairs) {
                String name = "tooltip_" + pair[0].substring(pair[0].indexOf(':') + 1) + (shift ? "_shift" : "");
                list.add(new Shot(name, sword, null, fp, true, () -> {
                    com.craisinlord.fightclub.combat.CombatTooltips.setShiftSupplier(() -> shift);
                    mc.setScreen(new TooltipScreen(stack(pair[0]), stack(pair[1])));
                }, 4));
            }
        }
        list.add(new Shot("test_reach_spear", spear, null, fp, true, null, 4, () -> logTest(mc, "reach with spear (expect 4.5 without Better Combat)", mc.player.entityInteractionRange())));
        if (com.craisinlord.fightclub.combat.CombatWeapons.backportSpears()) {
            list.add(new Shot("test_backport_spear_mob", spear, null, fp, true, () -> {
                command(mc, "kill @e[type=minecraft:pig]");
                command(mc, "summon minecraft:pig ~ ~ ~3 {NoAI:1b,Silent:1b}");
                command(mc, "item replace entity @e[type=minecraft:pig,limit=1] weapon.mainhand with minecraft:iron_spear");
            }, 20, () -> {
                logTest(mc, "player-held backport spear has VB components (expect false)",
                        com.craisinlord.fightclub.combat.CombatWeapons.hasMobComponents(mc.player.getMainHandItem()));
                logTest(mc, "mob-held backport spear has VB components (expect true)",
                        mc.level.getEntitiesOfClass(net.minecraft.world.entity.animal.Pig.class, mc.player.getBoundingBox().inflate(8.0)).stream()
                                .map(pig -> com.craisinlord.fightclub.combat.CombatWeapons.hasMobComponents(pig.getMainHandItem())).toList());
                logTest(mc, "backport spear uses Fight Club models (expect true true)",
                        (CombatModels.inHand(mc.player.getMainHandItem()) != null) + " " + (CombatModels.icon(mc.player.getMainHandItem()) != null));
                net.minecraft.world.item.ItemStack fresh = new net.minecraft.world.item.ItemStack(com.craisinlord.fightclub.combat.CombatWeapons.backportSpear("iron"));
                com.craisinlord.fightclub.combat.CombatWeapons.restoreMobComponents(fresh);
                logTest(mc, "restored backport spear components (expect true)", com.craisinlord.fightclub.combat.CombatWeapons.hasMobComponents(fresh));
                command(mc, "kill @e[type=minecraft:pig]");
            }));
        }
        list.add(new Shot("test_reach_dagger", dagger, null, fp, true, null, 4, () -> logTest(mc, "reach with dagger (expect 2.5 without Better Combat)", mc.player.entityInteractionRange())));
        addAttackTest(list, mc, "sweep_longsword", longsword, new double[][]{{-1.6, 2.5}, {0, 2.5}, {1.6, 2.5}}, "radius 2: both side pigs should be hit");
        addAttackTest(list, mc, "sweep_sword", sword, new double[][]{{-1.6, 2.5}, {0, 2.5}, {1.6, 2.5}}, "radius 1: side pigs 1.6 away should NOT be hit");
        addAttackTest(list, mc, "pierce_spear", spear, new double[][]{{0, 2.0}, {0, 3.0}, {0, 4.0}}, "pierce 2: both pigs behind should be hit");
        addAttackTest(list, mc, "no_sweep_dagger", dagger, new double[][]{{-0.9, 1.6}, {0, 1.6}, {0.9, 1.6}}, "dagger: only the middle pig should be hit");
        list.add(new Shot("test_potion_stack", "minecraft:potion", null, fp, true, () -> command(mc, "item replace entity @s weapon.mainhand with minecraft:potion 16"), 6,
                () -> logTest(mc, "potion stack (expect 16/16)", mc.player.getMainHandItem().getCount() + "/" + mc.player.getMainHandItem().getMaxStackSize())));
        list.add(new Shot("test_anvil_setup", sword, null, fp, true, () -> command(mc, "gamemode survival"), 4));
        list.add(new Shot("test_anvil", sword, null, fp, true, null, 4, () -> {
            var enchantments = mc.level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
            ItemStack worn = new ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD);
            worn.set(net.minecraft.core.component.DataComponents.REPAIR_COST, 60);
            ItemStack book = new ItemStack(net.minecraft.world.item.Items.ENCHANTED_BOOK);
            var stored = new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
            stored.set(enchantments.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS), 1);
            book.set(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
            var menu = new net.minecraft.world.inventory.AnvilMenu(0, mc.player.getInventory());
            menu.getSlot(0).set(worn);
            menu.getSlot(1).set(book);
            menu.createResult();
            logTest(mc, "anvil in survival, repair cost 60 (expect a result and a 61+ level cost)",
                    "creative=" + mc.player.getAbilities().instabuild + " cost=" + menu.getCost() + " result=" + menu.getSlot(2).getItem());
            command(mc, "gamemode creative");
        }));
        list.add(new Shot("test_running_charge_setup", spear, null, fp, true, () -> {
            command(mc, "kill @e[type=minecraft:pig]");
            command(mc, "summon minecraft:pig ~ ~ ~5 {NoAI:1b,Silent:1b}");
            command(mc, "summon minecraft:pig ~ ~ ~7 {NoAI:1b,Silent:1b}");
            net.minecraft.client.KeyMapping.click(mc.options.keyAttack.getDefaultKey());
            mc.options.keyAttack.setDown(true);
        }, 30));
        list.add(new Shot("test_running_charge_walk", spear, null, fp, true, () -> mc.options.keyUp.setDown(true), 3,
                () -> logTest(mc, "charging before contact (expect true)", CombatInputClient.isCharging())));
        list.add(new Shot("test_running_charge", spear, null, fp, true, null, 14, () -> {
            mc.options.keyUp.setDown(false);
            logTest(mc, "running charge carries on through (expect true)", CombatInputClient.isCharging());
            mc.options.keyAttack.setDown(false);
            StringBuilder pigs = new StringBuilder();
            mc.level.getEntitiesOfClass(net.minecraft.world.entity.animal.Pig.class, mc.player.getBoundingBox().inflate(8.0))
                    .forEach(pig -> pigs.append(String.format(java.util.Locale.ROOT, "hp %.1f/%.0f ", pig.getHealth(), pig.getMaxHealth())));
            logTest(mc, "running charge (expect both pigs hurt, charge ended on release)", pigs + "charging=" + CombatInputClient.isCharging());
        }));
        addPounceTests(list, mc, dagger, fp);
        addTapTests(list, mc, spear, fp);
        list.add(new Shot("gui_charge_half", spear, null, fp, true, () -> {
            CombatClientPoses.debugPose = CombatPose.SPEAR_LOWERED;
            CombatInputClient.debugProgress = 0.5F;
        }, SETTLE_TICKS));
        return list;
    }

    private static void addPounceTests(List<Shot> list, Minecraft mc, String dagger, CameraType fp) {
        String tankyPig = "{NoAI:1b,Silent:1b,Health:100f,attributes:[{id:\"minecraft:generic.max_health\",base:100d}]}";
        double[] start = new double[3];
        list.add(new Shot("test_pounce_setup", dagger, null, fp, true, () -> {
            command(mc, "kill @e[type=minecraft:pig]");
            command(mc, "summon minecraft:pig ~ ~ ~6 " + tankyPig);
        }, 10));
        list.add(new Shot("test_pounce_charge", dagger, null, fp, true, () -> {
            pigs(mc).stream().findFirst().ifPresent(pig -> mc.player.lookAt(
                    net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, pig.getBoundingBox().getCenter()));
            mc.options.keyAttack.setDown(true);
        }, CombatInputClient.HOLD_THRESHOLD_TICKS + 16 + 6, () -> logTest(mc, "pounce lock at full charge (expect pig, ready=true)",
                CombatInputClient.outlinedTarget() + " ready=" + CombatInputClient.pounceReady(mc))));
        list.add(new Shot("test_pounce", dagger, null, fp, true, () -> {
            start[0] = mc.player.getX(); start[1] = mc.player.getY(); start[2] = mc.player.getZ();
            mc.options.keyAttack.setDown(false);
        }, 16, () -> logTest(mc, "pounce from a 5+ block gap, past the server's timeout slack (expect pig hurt, dagger on cooldown; moved is net of the hop back)",
                pigHealth(mc) + String.format(java.util.Locale.ROOT, "moved=%.2f ", mc.player.position().distanceTo(new net.minecraft.world.phys.Vec3(start[0], start[1], start[2])))
                        + "cooldown=" + mc.player.getCooldowns().isOnCooldown(mc.player.getMainHandItem().getItem()))));

        list.add(new Shot("test_pounce_none_setup", dagger, null, fp, true, () -> {
            command(mc, "kill @e[type=minecraft:pig]");
            mc.player.setYRot(0.0F);
            mc.player.setXRot(0.0F);
        }, 40));
        list.add(new Shot("test_pounce_none_charge", dagger, null, fp, true, () -> mc.options.keyAttack.setDown(true),
                CombatInputClient.HOLD_THRESHOLD_TICKS + 16 + 6, () -> logTest(mc, "no target at full charge (expect null, ready=false)",
                        CombatInputClient.outlinedTarget() + " ready=" + CombatInputClient.pounceReady(mc))));
        list.add(new Shot("test_pounce_none", dagger, null, fp, true, () -> {
            start[0] = mc.player.getX(); start[1] = mc.player.getY(); start[2] = mc.player.getZ();
            mc.options.keyAttack.setDown(false);
        }, 10, () -> logTest(mc, "no-target release (expect moved~0, cooldown=false)",
                String.format(java.util.Locale.ROOT, "moved=%.2f ", mc.player.position().distanceTo(new net.minecraft.world.phys.Vec3(start[0], start[1], start[2])))
                        + "cooldown=" + mc.player.getCooldowns().isOnCooldown(mc.player.getMainHandItem().getItem()))));

        list.add(new Shot("test_pounce_passive_setup", dagger, null, fp, true, () -> {
            command(mc, "summon minecraft:pig ~1.5 ~ ~5 " + tankyPig);
            mc.player.setYRot(0.0F);
            mc.player.setXRot(0.0F);
        }, 10));
        list.add(new Shot("test_pounce_passive", dagger, null, fp, true, () -> mc.options.keyAttack.setDown(true),
                CombatInputClient.HOLD_THRESHOLD_TICKS + 16 + 6, () -> {
                    logTest(mc, "passive pig off-axis (expect null)", CombatInputClient.outlinedTarget());
                    mc.options.keyAttack.setDown(false);
                }));
        list.add(new Shot("test_pounce_cleanup", dagger, null, fp, true, () -> command(mc, "kill @e[type=minecraft:pig]"), 10));
    }

    private static List<net.minecraft.world.entity.animal.Pig> pigs(Minecraft mc) {
        return mc.level.getEntitiesOfClass(net.minecraft.world.entity.animal.Pig.class, mc.player.getBoundingBox().inflate(8.0));
    }

    private static String pigHealth(Minecraft mc) {
        StringBuilder result = new StringBuilder();
        pigs(mc).forEach(pig -> result.append(String.format(java.util.Locale.ROOT, "hp %.1f/%.0f ", pig.getHealth(), pig.getMaxHealth())));
        return result.toString();
    }

    private static void addTapTests(List<Shot> list, Minecraft mc, String spear, CameraType fp) {
        Runnable villager = () -> {
            command(mc, "kill @e[type=minecraft:villager]");
            command(mc, "summon minecraft:villager ~ ~ ~1.5 {NoAI:1b,Silent:1b,Rotation:[180f,0f]}");
        };
        list.add(new Shot("test_tap_setup", spear, null, fp, true, villager, 10));
        list.add(new Shot("test_tap", spear, null, fp, true, () -> pressFor(mc, 1), 20,
                () -> logTest(mc, "quick tap at point blank (expect villager hurt, not charging)",
                        villagerHealth(mc) + " charging=" + CombatInputClient.isCharging())));
        list.add(new Shot("test_hold_setup", spear, null, fp, true, villager, 10));
        list.add(new Shot("test_hold", spear, null, fp, true, () -> pressFor(mc, 14), 10,
                () -> logTest(mc, "holding (expect charging, villager untouched: no jab first)",
                        villagerHealth(mc) + " charging=" + CombatInputClient.isCharging())));
        list.add(new Shot("test_double_tap_setup", spear, null, fp, true, villager, 10));
        list.add(new Shot("test_double_tap", spear, null, fp, true, () -> {
            pressFor(mc, 1);
            later(3, () -> pressFor(mc, 2));
        }, 30, () -> logTest(mc, "second tap 3 ticks after the first (expect villager hurt: first hit not cancelled)",
                villagerHealth(mc) + " charging=" + CombatInputClient.isCharging())));
        for (String weapon : new String[]{spear, "minecraft:trident"}) {
            String name = weapon.equals(spear) ? "spear" : "trident";
            list.add(new Shot("test_tap_release_" + name + "_setup", weapon, null, fp, true, villager, 10));
            list.add(new Shot("test_tap_release_" + name, weapon, null, fp, true, () -> pressFor(mc, 40), 44,
                    () -> logTest(mc, name + " released after a full charge (expect swinging=false: no jab after the charge)",
                            "swinging=" + mc.player.swinging + " charging=" + CombatInputClient.isCharging())));
        }
    }

    private static String villagerHealth(Minecraft mc) {
        StringBuilder result = new StringBuilder();
        mc.level.getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class, mc.player.getBoundingBox().inflate(6.0))
                .forEach(v -> result.append(String.format(java.util.Locale.ROOT, "hp %.1f/%.0f ", v.getHealth(), v.getMaxHealth())));
        return result.length() == 0 ? "no villager (killed?)" : result.toString().trim();
    }

    private static void logTest(Minecraft mc, String what, Object value) {
        FightClub.LOGGER.info("Pose gallery test: {} -> {}", what, value);
    }

    private static void addAttackTest(List<Shot> list, Minecraft mc, String name, String weapon, double[][] pigs, String expectation) {
        list.add(new Shot("test_" + name + "_setup", weapon, null, CameraType.FIRST_PERSON, true, () -> {
            command(mc, "kill @e[type=minecraft:pig]");
            for (double[] pig : pigs) {
                command(mc, String.format(java.util.Locale.ROOT, "summon minecraft:pig ~%.2f ~ ~%.2f {NoAI:1b,Silent:1b,Rotation:[0f,0f]}", pig[0], pig[1]));
            }
        }, 10));
        list.add(new Shot("test_" + name, weapon, null, CameraType.FIRST_PERSON, true, () -> {
            if (mc.player == null || mc.level == null || mc.gameMode == null) return;
            var target = mc.level.getEntitiesOfClass(net.minecraft.world.entity.animal.Pig.class, mc.player.getBoundingBox().inflate(6.0)).stream()
                    .min(java.util.Comparator.comparingDouble(pig -> Math.abs(pig.getX() - mc.player.getX()) * 4 + pig.distanceToSqr(mc.player)))
                    .orElse(null);
            if (target == null) { logTest(mc, name, "no pig found"); return; }
            mc.gameMode.attack(mc.player, target);
            mc.player.swing(InteractionHand.MAIN_HAND);
        }, 6, () -> {
            if (mc.player == null || mc.level == null) return;
            StringBuilder result = new StringBuilder();
            mc.level.getEntitiesOfClass(net.minecraft.world.entity.animal.Pig.class, mc.player.getBoundingBox().inflate(6.0)).stream()
                    .sorted(java.util.Comparator.comparingDouble(pig -> pig.getX() + pig.getZ() * 0.001))
                    .forEach(pig -> result.append(String.format(java.util.Locale.ROOT, "[x%+.1f z%+.1f hp %.1f/%.0f] ",
                            pig.getX() - mc.player.getX(), pig.getZ() - mc.player.getZ(), pig.getHealth(), pig.getMaxHealth())));
            logTest(mc, name + " (" + expectation + ")", result.toString());
        }));
    }

    private static ItemStack stack(String id) {
        return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(id)));
    }

    private static final class TooltipScreen extends net.minecraft.client.gui.screens.Screen {
        private final ItemStack left, right;

        private TooltipScreen(ItemStack left, ItemStack right) {
            super(net.minecraft.network.chat.Component.empty());
            this.left = left;
            this.right = right;
        }

        @Override
        public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.renderTooltip(font, left, 4, 24);
            graphics.renderTooltip(font, right, width / 2 + 4, 24);
        }

        @Override
        public boolean isPauseScreen() { return false; }
    }

    private static Runnable pose(CombatPose pose) {
        return () -> CombatClientPoses.debugPose = pose;
    }

    private static Runnable swing(Minecraft mc) {
        return () -> {
            if (mc.player != null) {
                ItemStack held = mc.player.getMainHandItem();
                if (!held.isEmpty()) mc.player.swing(InteractionHand.MAIN_HAND);
            }
        };
    }
}
