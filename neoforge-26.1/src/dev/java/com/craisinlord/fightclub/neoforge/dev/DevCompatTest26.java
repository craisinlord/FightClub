package com.craisinlord.fightclub.neoforge.dev;

import com.craisinlord.fightclub.FightClub;
import com.craisinlord.fightclub.combat.CombatChargeState;
import com.craisinlord.fightclub.combat.CombatTooltips;
import com.craisinlord.fightclub.combat.CombatWeapons;
import com.craisinlord.fightclub.combat.ExternalWeapons;
import com.craisinlord.fightclub.combat.WeaponSpec;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class DevCompatTest26 {
    private static final String TRIGGER = "fc_compattest", WORLD = "fc_compattest";
    private static Boolean enabled;
    private static String ns = "";
    private static boolean worldRequested, scheduled;
    private static int tick;
    private static final TreeMap<Integer, Runnable> STEPS = new TreeMap<>();
    private static final Map<String, Float> HEALTH = new LinkedHashMap<>();
    private static final Map<String, Integer> TARGETS = new LinkedHashMap<>();
    private static final List<ItemStack> SG = new ArrayList<>();

    private DevCompatTest26() {}

    private static boolean silentGear() {
        return ns.equals("silentgear");
    }

    private static String[] types() {
        return silentGear() ? new String[]{"dagger", "knife", "katana", "spear", "sword", "machete", "shield"}
                : new String[]{"dagger", "spear", "pike", "longsword", "greatsword", "katana", "halberd", "glaive", "quarterstaff", "saber", "rapier", "scythe"};
    }

    private static String longsword() {
        return silentGear() ? "katana" : "greatsword";
    }

    public static void tick(Minecraft mc) {
        if (enabled == null) {
            File trigger = new File(mc.gameDirectory, TRIGGER);
            enabled = trigger.isFile();
            if (enabled) {
                try {
                    ns = java.nio.file.Files.readString(trigger.toPath()).trim().split("\\s+")[0];
                } catch (java.io.IOException e) {
                    enabled = false;
                }
            }
        }
        if (!enabled) return;
        mc.options.pauseOnLostFocus = false;
        if (mc.level != null && mc.screen instanceof PauseScreen) mc.setScreen(null);
        if (mc.level == null || mc.player == null) {
            if (!worldRequested && mc.screen instanceof TitleScreen) {
                worldRequested = true;
                deleteTree(new File(mc.gameDirectory, "saves/" + WORLD));
                LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE,
                        new LevelSettings.DifficultySettings(Difficulty.EASY, false, false), true, WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(20261122L, false, false),
                        WorldPresets::createFlatWorldDimensions, mc.screen);
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
                FightClub.LOGGER.error("COMPATTEST", e);
            }
        }
    }

    private static void schedule(Minecraft mc, MinecraftServer server) {
        int t = 80;
        STEPS.put(t, () -> onServer(server, () -> {
            run(server, "time set noon");
            run(server, "kill @e[type=!minecraft:player]");
        }));
        STEPS.put(t += 2, () -> org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().handle(), 1600, 900));
        STEPS.put(t += 4, () -> collectSilentGear(mc));
        for (String type : types()) {
            STEPS.put(t += 4, () -> onServer(server, () -> arena(server, type, "Iron")));
            STEPS.put(t += 4, () -> onServer(server, () -> reportMapping(server, type)));
        }
        STEPS.put(t += 5, () -> onServer(server, () -> reportReplaced(server)));
        STEPS.put(t += 5, () -> reportCreativeTab(mc));
        if (new File(mc.gameDirectory, "fc_quick").isFile()) {
            t = visualTest(mc, server, t + 10, ns + "_spear", () -> sample("spear", "Diamond"));
            if (!silentGear()) t = visualTest(mc, server, t + 10, ns + "_pike", () -> sample("pike", "Iron"));
            STEPS.put(t + 5, mc::stop);
            return;
        }
        t = pounceTest(server, t + 10, "dagger", "Iron");
        t = pounceTest(server, t + 10, "dagger", "Diamond");
        t = throwTest(mc, server, t + 10, "dagger", "Iron");
        t = throwTest(mc, server, t + 10, "spear", "Iron");
        t = spinTest(mc, server, t + 10, longsword(), "Iron");
        t = spinTest(mc, server, t + 10, longsword(), "Diamond");
        t = visualTest(mc, server, t + 10, ns + "_dagger", () -> sample("dagger", "Diamond"));
        t = visualTest(mc, server, t + 10, "ref_dagger", () -> item("fight_club", "diamond_dagger"));
        t = visualTest(mc, server, t + 10, ns + "_spear", () -> sample("spear", "Diamond"));
        t = visualTest(mc, server, t + 10, "ref_spear", () -> item("minecraft", "diamond_spear"));
        t = visualTest(mc, server, t + 10, ns + "_" + longsword(), () -> sample(longsword(), "Diamond"));
        t = visualTest(mc, server, t + 10, "ref_longsword", () -> item("fight_club", "diamond_longsword"));
        STEPS.put(t + 20, () -> {
            log("DONE");
            mc.stop();
        });
    }

    private static void collectSilentGear(Minecraft mc) {
        if (!silentGear()) return;
        CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(), true, mc.level.registryAccess());
        for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
            for (ItemStack stack : tab.getDisplayItems()) {
                if (!BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("silentgear")) continue;
                if (SG.stream().noneMatch(s -> ItemStack.isSameItemSameComponents(s, stack))) SG.add(stack.copy());
            }
        }
        log("silent gear samples: {}", SG.size());
    }

    private static void reportMapping(MinecraftServer server, String type) {
        ServerPlayer player = player(server);
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            log("mapping {}: NOT INSTALLED", type);
            return;
        }
        WeaponSpec spec = CombatTooltips.specFor(stack);
        log("mapping {} [{}]: kind={} weapon={} stackDamage={} playerReach={} specReach={} charge={} throw={} scale={}",
                type, stack.getHoverName().getString(), CombatTooltips.kindOf(stack), CombatWeapons.weapon(stack.getItem()),
                CombatWeapons.stackAttackDamage(stack), player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE),
                spec == null ? -1 : spec.reach(), spec == null ? -1 : CombatWeapons.chargeDamage(stack, spec),
                spec == null ? -1 : CombatWeapons.throwDamage(stack, spec), spec == null ? -1 : CombatWeapons.damageScale(stack, spec));
    }

    private static void reportReplaced(MinecraftServer server) {
        for (String weapon : new String[]{"dagger", "longsword"}) {
            StringBuilder line = new StringBuilder();
            for (String material : new String[]{"wood", "stone", "copper", "iron", "gold", "diamond", "netherite"}) {
                Identifier id = Identifier.fromNamespaceAndPath("fight_club", material + "_" + weapon);
                Item item = BuiltInRegistries.ITEM.getValue(id);
                boolean recipe = server.getRecipeManager().getRecipes().stream().anyMatch(r -> r.id().identifier().equals(id));
                line.append(material).append('=').append(ExternalWeapons.isReplaced(item) ? "hidden" : "shown")
                        .append(recipe ? "+recipe " : "-recipe ");
            }
            log("replaced {}: {}", weapon, line.toString().trim());
        }
    }

    private static void reportCreativeTab(Minecraft mc) {
        CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(), true, mc.level.registryAccess());
        List<String> ours = new ArrayList<>();
        for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
            for (ItemStack stack : tab.getDisplayItems()) {
                Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (id.getNamespace().equals("fight_club") && !ours.contains(id.getPath())) ours.add(id.getPath());
            }
        }
        log("fight_club items in creative tabs: {}", ours);
    }

    private static int pounceTest(MinecraftServer server, int t, String type, String material) {
        String key = "pounce " + material + " " + type;
        STEPS.put(t, () -> onServer(server, () -> {
            ServerPlayer player = arena(server, type, material);
            target(key, dummy(player, 3.2));
            CombatChargeState.handle(player, CombatChargeState.START);
        }));
        STEPS.put(t + 40, () -> onServer(server, () ->
                CombatChargeState.handle(player(server), CombatChargeState.RELEASE, TARGETS.get(key))));
        STEPS.put(t + 70, () -> onServer(server, () -> report(server, key)));
        return t + 71;
    }

    private static int throwTest(Minecraft mc, MinecraftServer server, int t, String type, String material) {
        String key = "throw " + material + " " + type;
        STEPS.put(t, () -> onServer(server, () -> target(key, dummy(arena(server, type, material), 7.0))));
        STEPS.put(t + 15, () -> {
            KeyMapping.click(mc.options.keyUse.getDefaultKey());
            mc.options.keyUse.setDown(true);
        });
        STEPS.put(t + 45, () -> {
            log("{}: client using={} before release", key, mc.player.isUsingItem());
            mc.options.keyUse.setDown(false);
        });
        STEPS.put(t + 48, () -> onServer(server, () -> {
            ServerPlayer player = player(server);
            List<Entity> nearby = player.level().getEntities(player, player.getBoundingBox().inflate(16), e -> !(e instanceof LivingEntity));
            log("{}: entities={} handAfter={}", key, nearby.stream().map(e -> BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString()).toList(),
                    BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()));
        }));
        STEPS.put(t + 90, () -> onServer(server, () -> {
            ServerPlayer player = player(server);
            ItemStack thrown = sample(type, material);
            WeaponSpec spec = CombatTooltips.specFor(thrown);
            report(server, key);
            log("{}: expected throw damage {}", key, spec == null ? -1 : (type.equals("spear")
                    ? spec.attackDamage() * CombatWeapons.damageScale(thrown, spec) : CombatWeapons.throwDamage(thrown, spec)));
            player.level().getEntities(player, player.getBoundingBox().inflate(32), e -> !(e instanceof LivingEntity)).forEach(Entity::discard);
        }));
        return t + 91;
    }

    private static int spinTest(Minecraft mc, MinecraftServer server, int t, String type, String material) {
        String key = "spin " + material + " " + type;
        STEPS.put(t, () -> onServer(server, () -> target(key, dummy(arena(server, type, material), 2.2))));
        STEPS.put(t + 15, () -> {
            KeyMapping.click(mc.options.keyUse.getDefaultKey());
            mc.options.keyUse.setDown(true);
        });
        STEPS.put(t + 55, () -> {
            log("{}: client using={} before release", key, mc.player.isUsingItem());
            mc.options.keyUse.setDown(false);
        });
        STEPS.put(t + 75, () -> onServer(server, () -> report(server, key)));
        return t + 120;
    }

    private static ItemStack item(String namespace, String path) {
        return BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath(namespace, path)).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    private static int visualTest(Minecraft mc, MinecraftServer server, int t, String name, java.util.function.Supplier<ItemStack> stack) {
        STEPS.put(t, () -> onServer(server, () -> {
            run(server, "kill @e[type=!minecraft:player]");
            run(server, "fill -3 -60 7 4 -56 7 minecraft:oak_planks");
            run(server, "fill -3 -60 1 4 -56 6 minecraft:air");
            arena(server, "dagger", "Iron").setItemInHand(InteractionHand.MAIN_HAND, stack.get());
        }));
        STEPS.put(t + 20, () -> {
            mc.options.hideGui = false;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            KeyMapping.click(mc.options.keyUse.getDefaultKey());
            mc.options.keyUse.setDown(true);
        });
        STEPS.put(t + 34, () -> grab(mc, name + "_pose_first"));
        STEPS.put(t + 35, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        STEPS.put(t + 44, () -> grab(mc, name + "_pose_front"));
        STEPS.put(t + 45, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        STEPS.put(t + 54, () -> grab(mc, name + "_pose_back"));
        for (int i = 46; i <= 53; i++) STEPS.put(t + i, () -> {
            mc.player.setYRot(-55.0F);
            mc.player.setYHeadRot(-55.0F);
            mc.player.yBodyRot = 0.0F;
            mc.player.yBodyRotO = 0.0F;
        });
        STEPS.put(t + 55, () -> {
            mc.player.setYRot(0.0F);
            mc.player.setYHeadRot(0.0F);
        });
        STEPS.put(t + 56, () -> mc.options.keyUse.setDown(false));
        STEPS.put(t + 58, () -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        STEPS.put(t + 85, () -> grab(mc, name + "_stuck"));
        STEPS.put(t + 86, () -> onServer(server, () -> {
            ServerPlayer player = player(server);
            player.teleportTo(player.level(), 3.5, -59.0, 4.0, Set.of(), 50.0F, 20.0F, true);
        }));
        STEPS.put(t + 92, () -> grab(mc, name + "_stuck_side"));
        STEPS.put(t + 93, () -> onServer(server, () -> {
            ServerPlayer player = player(server);
            player.teleportTo(player.level(), 0.5, -60.0, 0.5, Set.of(), 0.0F, 0.0F, true);
        }));
        STEPS.put(t + 96, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        STEPS.put(t + 106, () -> grab(mc, name + "_idle"));
        STEPS.put(t + 107, () -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        return t + 108;
    }

    private static void grab(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "compat26_" + name + ".png", mc.getMainRenderTarget(), 1, message -> {});
        log("screenshot compat26_{}.png using={}", name, mc.player.isUsingItem());
    }

    private static void target(String key, LivingEntity entity) {
        HEALTH.put(key, entity.getHealth());
        TARGETS.put(key, entity.getId());
    }

    private static void report(MinecraftServer server, String key) {
        ServerPlayer player = player(server);
        Integer id = TARGETS.get(key);
        Entity entity = id == null ? null : player.level().getEntity(id);
        float before = HEALTH.getOrDefault(key, -1f);
        float after = entity instanceof LivingEntity living ? living.getHealth() : -1f;
        ItemStack stack = player.getMainHandItem();
        WeaponSpec spec = CombatTooltips.specFor(stack);
        log("{} [{}]: damage dealt={} expectedCharge={} stackDamage={}", key,
                stack.isEmpty() ? "empty" : stack.getHoverName().getString(), before - after,
                spec == null ? -1 : CombatWeapons.chargeDamage(stack, spec), CombatWeapons.stackAttackDamage(stack));
        if (entity != null) entity.discard();
    }

    private static ItemStack sample(String type, String material) {
        if (silentGear()) {
            return SG.stream().filter(s -> BuiltInRegistries.ITEM.getKey(s.getItem()).getPath().equals(type)
                    && s.getHoverName().getString().startsWith(material + " ")).findFirst().orElse(ItemStack.EMPTY).copy();
        }
        return BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath(ns, material.toLowerCase() + "_" + type))
                .map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    private static ServerPlayer arena(MinecraftServer server, String type, String material) {
        ServerPlayer player = player(server);
        com.craisinlord.fightclub.combat.CombatRecovery.clear(player);
        CombatChargeState.clear(player);
        player.setGameMode(GameType.SURVIVAL);
        player.getAbilities().invulnerable = true;
        player.teleportTo(player.level(), 0.5, -60, 0.5, Set.of(), 0.0F, 0.0F, true);
        player.setItemInHand(InteractionHand.MAIN_HAND, sample(type, material));
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        player.resetAttackStrengthTicker();
        return player;
    }

    private static Zombie dummy(ServerPlayer player, double distance) {
        ServerLevel level = player.level();
        Zombie zombie = EntityType.HUSK.create(level, EntitySpawnReason.COMMAND);
        zombie.setNoAi(true);
        zombie.setPersistenceRequired();
        zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0);
        zombie.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
        zombie.setHealth(500.0F);
        zombie.snapTo(player.getX(), player.getY(), player.getZ() + distance, 180.0F, 0.0F);
        level.addFreshEntity(zombie);
        return zombie;
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
                FightClub.LOGGER.error("COMPATTEST", e);
            }
        });
    }

    private static void run(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    private static void log(String message, Object... args) {
        FightClub.LOGGER.info("COMPATTEST " + message, args);
    }

    private static void deleteTree(File file) {
        if (!file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }
}
