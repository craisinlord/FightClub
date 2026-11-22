package com.craisinlord.fightclub.client;

import com.craisinlord.fightclub.FightClub;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.io.File;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;

public final class DevBackportTest {
    private static final String TRIGGER = "fc_backporttest", WORLD = "fc_backporttest";
    private static final String[] ITEMS = {"minecraft:iron_spear", "minecraft:diamond_spear", "minecraft:iron_sword",
            "fight_club:iron_dagger", "fight_club:iron_longsword", "minecraft:trident"};
    private static Boolean enabled;
    private static boolean worldRequested, scheduled;
    private static int tick;
    private static final TreeMap<Integer, Runnable> STEPS = new TreeMap<>();

    private DevBackportTest() {}

    public static void tick(Minecraft mc) {
        if (enabled == null) enabled = new File(mc.gameDirectory, TRIGGER).isFile();
        if (!enabled) return;
        mc.options.pauseOnLostFocus = false;
        if (mc.level != null && mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        if (mc.level == null || mc.player == null) {
            if (!worldRequested && mc.screen instanceof TitleScreen) {
                worldRequested = true;
                deleteTree(new File(mc.gameDirectory, "saves/" + WORLD));
                LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.EASY, true,
                        new GameRules(), WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(20261122L, false, false),
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
        if (step != null) {
            try {
                step.run();
            } catch (RuntimeException e) {
                log("STEP FAILED at tick {}: {}", tick, e.toString());
                FightClub.LOGGER.error("BACKPORTTEST", e);
            }
        }
    }

    private static void schedule(Minecraft mc, MinecraftServer server) {
        int t = 60;
        for (String id : ITEMS) {
            STEPS.put(t, () -> server.execute(() -> {
                ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                player.setItemInHand(InteractionHand.MAIN_HAND, stack(id));
            }));
            STEPS.put(t += 10, () -> server.execute(() -> report("server", id, server.getPlayerList().getPlayers().get(0))));
            STEPS.put(t += 2, () -> {
                report("client", id, mc.player);
                ItemStack held = mc.player.getMainHandItem();
                List<Component> lines = held.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL);
                log("tooltip {}: {}", id, lines.stream().map(Component::getString).collect(Collectors.joining(" | ")));
                log("components {}: {}", id, held.getComponents().keySet().stream()
                        .map(type -> String.valueOf(BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type)))
                        .filter(key -> !key.startsWith("minecraft:max_") && !key.equals("minecraft:lore"))
                        .sorted().collect(Collectors.joining(",")));
            });
            t += 4;
        }
        STEPS.put(t + 10, () -> {
            log("DONE");
            mc.stop();
        });
    }

    private static void report(String side, String id, Player player) {
        log("{} {}: held={} damage={} speed={} reach={}", side, id,
                BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()),
                player.getAttributeValue(Attributes.ATTACK_DAMAGE), player.getAttributeValue(Attributes.ATTACK_SPEED),
                player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE));
    }

    private static ItemStack stack(String id) {
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    private static void log(String message, Object... args) {
        FightClub.LOGGER.info("BACKPORTTEST " + message, args);
    }

    private static void deleteTree(File file) {
        if (!file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }
}
