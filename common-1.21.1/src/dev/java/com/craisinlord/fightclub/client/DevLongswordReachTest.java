package com.craisinlord.fightclub.client;

import com.craisinlord.fightclub.FightClub;
import com.craisinlord.fightclub.combat.CombatChargeState;
import com.craisinlord.fightclub.combat.LongswordSpin;
import com.craisinlord.fightclub.mixin.AttackTickerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
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
import java.util.TreeMap;

public final class DevLongswordReachTest {
    private static final String TRIGGER = "fc_lsreach", WORLD = "fc_lsreach";
    private static Boolean enabled;
    private static boolean worldRequested, scheduled;
    private static int tick;
    private static final TreeMap<Integer, Runnable> STEPS = new TreeMap<>();

    private record Dummy(String label, Zombie zombie, float before) {}

    private DevLongswordReachTest() {}

    public static void tick(Minecraft mc) {
        if (enabled == null) enabled = new File(mc.gameDirectory, TRIGGER).isFile();
        if (!enabled) return;
        mc.options.pauseOnLostFocus = false;
        if (mc.level != null && mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        if (mc.level == null || mc.player == null) {
            if (!worldRequested && mc.screen instanceof TitleScreen) {
                worldRequested = true;
                LevelSettings settings = new LevelSettings(WORLD + System.currentTimeMillis(), GameType.CREATIVE, false, Difficulty.EASY, true,
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
        int t = 80;
        STEPS.put(t, () -> onServer(server, () -> {
            for (String c : new String[]{"gamerule doDaylightCycle false", "gamerule doMobSpawning false", "time set 18000",
                    "gamerule sendCommandFeedback false", "kill @e[type=!minecraft:player]"}) run(server, c);
        }));
        for (double d = 1.5; d <= 4.51; d += 0.5) t = realTrial(mc, server, t + 10, d, 0.0, true);
        for (double d = 1.5; d <= 4.51; d += 0.5) t = realTrial(mc, server, t + 10, d, 50.0, false);
        for (double d = 1.5; d <= 4.51; d += 0.5) {
            t = trial(server, t + 10, "punch", d, 0.0);
            t = trial(server, t + 10, "punch", d, 55.0);
        }
        for (double d = 2.0; d <= 5.01; d += 0.5) {
            t = trial(server, t + 10, "spin", d, 0.0);
            t = trial(server, t + 10, "spin", d, 150.0);
        }
        for (double d = 2.0; d <= 4.01; d += 0.5) t = trial(server, t + 10, "click", d, 0.0);
        STEPS.put(t + 20, () -> {
            log("DONE");
            mc.stop();
        });
    }

    private static int trial(MinecraftServer server, int t, String mode, double distance, double angle) {
        List<Dummy> dummies = new ArrayList<>();
        STEPS.put(t, () -> onServer(server, () -> {
            run(server, "kill @e[type=!minecraft:player]");
            ServerPlayer player = arena(server);
            double yaw = Math.toRadians(angle);
            double fx = 0.0, fz = 1.0;
            double rx = -1.0, rz = 0.0;
            double x = 0.5 + (fx * Math.cos(yaw) + rx * Math.sin(yaw)) * distance;
            double z = 0.5 + (fz * Math.cos(yaw) + rz * Math.sin(yaw)) * distance;
            Zombie zombie = EntityType.ZOMBIE.create(player.serverLevel());
            zombie.setNoAi(true);
            zombie.setSilent(true);
            zombie.moveTo(x, -60, z, 180.0F, 0.0F);
            player.serverLevel().addFreshEntity(zombie);
            dummies.add(new Dummy(mode + " d=" + distance + " angle=" + angle, zombie, zombie.getHealth()));
        }));
        STEPS.put(t + 3, () -> onServer(server, () -> {
            ServerPlayer player = player(server);
            ((AttackTickerAccessor) player).fightClub$setAttackStrengthTicker(1000);
            switch (mode) {
                case "punch" -> LongswordSpin.punch(player);
                case "spin" -> LongswordSpin.release(player, player.getMainHandItem(), 200);
                default -> player.attack(dummies.get(0).zombie());
            }
        }));
        STEPS.put(t + 25, () -> onServer(server, () -> {
            for (Dummy dummy : dummies) {
                log("{}: damage={} gap={}", dummy.label(), dummy.before() - dummy.zombie().getHealth(),
                        String.format("%.2f", Math.sqrt(dummy.zombie().distanceToSqr(player(server).getX(), dummy.zombie().getY(), player(server).getZ()))
                                - (dummy.zombie().getBbWidth() + player(server).getBbWidth()) * 0.5));
            }
        }));
        return t + 26;
    }

    private static int realTrial(Minecraft mc, MinecraftServer server, int t, double distance, double angle, boolean aimAt) {
        List<Dummy> dummies = new ArrayList<>();
        STEPS.put(t, () -> onServer(server, () -> {
            run(server, "kill @e[type=!minecraft:player]");
            ServerPlayer player = arena(server);
            double yaw = Math.toRadians(angle);
            double x = 0.5 - Math.sin(yaw) * distance;
            double z = 0.5 + Math.cos(yaw) * distance;
            Zombie zombie = EntityType.ZOMBIE.create(player.serverLevel());
            zombie.setNoAi(true);
            zombie.setSilent(true);
            zombie.moveTo(x, -60, z, 180.0F, 0.0F);
            player.serverLevel().addFreshEntity(zombie);
            dummies.add(new Dummy("real " + (aimAt ? "aimed" : "side") + " d=" + distance + " angle=" + angle, zombie, zombie.getHealth()));
        }));
        STEPS.put(t + 5, () -> {
            mc.player.setYRot(0.0F);
            mc.player.setXRot(aimAt ? 15.0F : 0.0F);
            mc.player.yRotO = 0.0F;
            mc.player.xRotO = mc.player.getXRot();
        });
        STEPS.put(t + 40, () -> {
            mc.player.setYRot(0.0F);
            mc.player.setXRot(aimAt ? 15.0F : 0.0F);
            log("real d={} client strength={} hit={}", distance, mc.player.getAttackStrengthScale(0.0F),
                    mc.hitResult == null ? "null" : mc.hitResult.getType());
            boolean went = ((com.craisinlord.fightclub.mixin.MinecraftAccessor) mc).fightClub$startAttack();
            log("real d={} startAttack returned {}", distance, went);
        });
        STEPS.put(t + 65, () -> onServer(server, () -> {
            for (Dummy dummy : dummies) log("{}: damage={}", dummy.label(), dummy.before() - dummy.zombie().getHealth());
        }));
        return t + 66;
    }

    private static ServerPlayer arena(MinecraftServer server) {
        ServerPlayer player = player(server);
        com.craisinlord.fightclub.combat.CombatRecovery.clear(player);
        CombatChargeState.clear(player);
        player.setGameMode(GameType.SURVIVAL);
        player.getAbilities().invulnerable = true;
        player.teleportTo(player.serverLevel(), 0.5, -60, 0.5, 0.0F, 0.0F);
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        player.connection.teleport(0.5, -60, 0.5, 0.0F, 0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("fight_club", "iron_longsword"))));
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        return player;
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().get(0);
    }

    private static void onServer(MinecraftServer server, Runnable runnable) {
        server.execute(() -> {
            try {
                runnable.run();
            } catch (RuntimeException e) {
                log("SERVER STEP FAILED: {}", e.toString());
                FightClub.LOGGER.error("LSREACH", e);
            }
        });
    }

    private static void run(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput().withPermission(4), command);
    }

    private static void log(String message, Object... args) {
        FightClub.LOGGER.info("LSREACH " + message, args);
    }
}
