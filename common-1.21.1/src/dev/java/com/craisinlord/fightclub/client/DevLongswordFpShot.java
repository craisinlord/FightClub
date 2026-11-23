package com.craisinlord.fightclub.client;

import com.craisinlord.fightclub.FightClub;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.TreeMap;

public final class DevLongswordFpShot {
    private static final String TRIGGER = "fc_lsfp";
    private static final String POSES = "../build/resources/main/assets/fight_club/animations/poses.json";
    private static final String MODEL = "../build/resources/main/assets/fight_club/models/item/diamond_longsword_in_hand.json";
    private static Boolean enabled;
    private static boolean worldRequested, scheduled;
    private static int tick;
    private static final TreeMap<Integer, Runnable> STEPS = new TreeMap<>();

    private DevLongswordFpShot() {}

    public static void tick(Minecraft mc) {
        if (enabled == null) enabled = new File(mc.gameDirectory, TRIGGER).isFile();
        if (!enabled) return;
        mc.options.pauseOnLostFocus = false;
        if (mc.level != null && mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        if (mc.level == null || mc.player == null) {
            if (!worldRequested && mc.screen instanceof TitleScreen) {
                worldRequested = true;
                LevelSettings settings = new LevelSettings("fc_lsfp" + System.currentTimeMillis(), GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                        new GameRules(), WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel(settings.levelName(), settings, new WorldOptions(20261122L, false, false),
                        registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                                .value().createWorldDimensions(), mc.screen);
            }
            return;
        }
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) return;
        if (!scheduled) {
            scheduled = true;
            schedule(mc, server);
        }
        tick++;
        Runnable step = STEPS.remove(tick);
        if (step != null) step.run();
    }

    private static void schedule(Minecraft mc, MinecraftServer server) {
        List<String> candidates;
        try {
            candidates = Files.readAllLines(new File(mc.gameDirectory, TRIGGER).toPath()).stream().filter(l -> !l.isBlank()).toList();
        } catch (IOException e) {
            candidates = List.of();
        }
        int t = 60;
        STEPS.put(t, () -> org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1600, 900));
        STEPS.put(t += 5, () -> server.execute(() -> {
            for (String c : new String[]{"gamerule doDaylightCycle false", "time set 6000", "weather clear"}) {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput().withPermission(4), c);
            }
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            player.setItemInHand(InteractionHand.MAIN_HAND,
                    new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("fight_club", "diamond_longsword"))));
        }));
        int index = 0;
        for (String line : candidates) {
            int n = index++;
            if (line.startsWith("swing ")) {
                float frame = Float.parseFloat(line.substring(6).trim());
                STEPS.put(t += 10, () -> CombatClientPoses.debugSwing = frame);
            } else if (line.startsWith("poses ")) {
                String source = line.substring(6).trim();
                STEPS.put(t += 10, () -> {
                    try {
                        Files.copy(new File(source).toPath(), new File(mc.gameDirectory, POSES).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        FightClub.LOGGER.error("LSFP copy failed", e);
                    }
                    mc.reloadResourcePacks();
                });
                t += 70;
                continue;
            } else {
                STEPS.put(t += 10, () -> {
                    CombatClientPoses.debugSwing = -1.0F;
                    writeModel(mc, line);
                    mc.reloadResourcePacks();
                });
            }
            STEPS.put(t += 70, () -> {
                mc.options.hideGui = false;
                mc.player.setYRot(0.0F);
                mc.player.setXRot(0.0F);
                mc.player.yRotO = 0.0F;
                mc.player.xRotO = 0.0F;
            });
            STEPS.put(t += 10, () -> {
                Screenshot.grab(mc.gameDirectory, "lsfp_" + n + ".png", mc.getMainRenderTarget(), message -> {});
                FightClub.LOGGER.info("LSFP shot {} = {}", n, line);
            });
        }
        STEPS.put(t + 20, () -> {
            CombatClientPoses.debugSwing = -1.0F;
            mc.stop();
        });
    }

    private static void writeModel(Minecraft mc, String line) {
        String[] v = line.trim().split("\\s+");
        String fp = "{\"rotation\":[" + v[0] + "," + v[1] + "," + v[2] + "],\"translation\":[" + v[3] + "," + v[4] + "," + v[5]
                + "],\"scale\":[" + v[6] + "," + v[7] + "," + v[8] + "]}";
        String fpl = "{\"rotation\":[" + v[0] + "," + (-Double.parseDouble(v[1])) + "," + (-Double.parseDouble(v[2])) + "],\"translation\":[" + v[3] + "," + v[4] + "," + v[5]
                + "],\"scale\":[" + v[6] + "," + v[7] + "," + v[8] + "]}";
        String json = "{\"parent\":\"minecraft:item/handheld\",\"textures\":{\"layer0\":\"fight_club:item/diamond_longsword_in_hand\"},\"display\":{"
                + "\"firstperson_righthand\":" + fp + ",\"firstperson_lefthand\":" + fpl
                + ",\"thirdperson_righthand\":{\"rotation\":[-10,0,45],\"translation\":[-1.5,9.2,-0.4],\"scale\":[1.5,1.5,0.85]}"
                + ",\"thirdperson_lefthand\":{\"rotation\":[-10,0,-45],\"translation\":[-1.5,9.2,-0.4],\"scale\":[1.5,1.5,0.85]}}}";
        try {
            Files.writeString(new File(mc.gameDirectory, MODEL).toPath(), json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            FightClub.LOGGER.error("LSFP write failed", e);
        }
    }
}
