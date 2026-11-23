package com.craisinlord.fightclub.client;

import com.craisinlord.fightclub.FightClub;
import com.craisinlord.fightclub.combat.CombatChargeState;
import com.craisinlord.fightclub.combat.CombatTooltips;
import com.craisinlord.fightclub.combat.CombatWeapons;
import com.craisinlord.fightclub.combat.DaggerProjectile;
import com.craisinlord.fightclub.combat.ExternalWeapons;
import com.craisinlord.fightclub.combat.SpearProjectile;
import com.craisinlord.fightclub.combat.WeaponSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class DevCompatTest {
    private static final String TRIGGER = "fc_compattest", WORLD = "fc_compattest";
    private static Boolean enabled;
    private static String ns = "";
    private static boolean quick;
    private static boolean worldRequested, scheduled;
    private static int tick;
    private static final TreeMap<Integer, Runnable> STEPS = new TreeMap<>();
    private static final Map<String, Float> HEALTH = new LinkedHashMap<>();
    private static final Map<String, Integer> TARGETS = new LinkedHashMap<>();

    private DevCompatTest() {}

    private static String[] items() {
        return switch (ns) {
            case "farmersdelight" -> new String[]{"flint_knife", "iron_knife", "golden_knife", "diamond_knife", "netherite_knife"};
            case "simplyswords" -> new String[]{"iron_sai", "diamond_sai", "iron_spear", "diamond_spear", "iron_longsword",
                    "iron_claymore", "iron_twinblade", "iron_katana", "iron_glaive", "iron_halberd", "iron_cutlass", "iron_rapier",
                    "iron_scythe", "iron_warglaive"};
            default -> new String[]{"iron_dagger", "diamond_dagger", "iron_spear", "diamond_spear", "iron_pike", "iron_longsword",
                    "iron_greatsword", "iron_katana", "iron_glaive", "iron_halberd", "iron_quarterstaff", "iron_saber",
                    "iron_rapier", "iron_parrying_dagger", "iron_scythe", "iron_battleaxe"};
        };
    }

    private static String dagger() {
        return ns.equals("simplyswords") ? "sai" : ns.equals("farmersdelight") ? "knife" : "dagger";
    }

    public static void tick(Minecraft mc) {
        if (enabled == null) {
            File trigger = new File(mc.gameDirectory, TRIGGER);
            enabled = trigger.isFile();
            if (enabled) {
                try {
                    String text = java.nio.file.Files.readString(trigger.toPath()).trim();
                    ns = text.split("\\s+")[0];
                    quick = text.contains("quick");
                } catch (java.io.IOException e) {
                    enabled = false;
                }
            }
        }
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
                FightClub.LOGGER.error("COMPATTEST", e);
            }
        }
    }

    private static void schedule(Minecraft mc, MinecraftServer server) {
        int t = 80;
        STEPS.put(t, () -> onServer(server, () -> {
            for (String c : new String[]{"gamerule doDaylightCycle false", "gamerule doMobSpawning false", "time set 6000",
                    "gamerule sendCommandFeedback false", "kill @e[type=!minecraft:player]"}) run(server, c);
        }));
        STEPS.put(t += 2, () -> org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1600, 900));
        if (ns.equals("fight_club")) {
            STEPS.put(t += 5, DevCompatTest::reportInHandModels);
            for (String path : new String[]{"iron_spear", "generic/silver/spear", "oreganized/electrum/spear"}) {
                t = visualTest(mc, server, t + 10, path);
            }
            STEPS.put(t + 20, () -> {
                log("DONE");
                mc.stop();
            });
            return;
        }
        for (String path : items()) {
            STEPS.put(t += 4, () -> onServer(server, () -> arena(server, path)));
            STEPS.put(t += 4, () -> onServer(server, () -> reportMapping(server, path)));
        }
        STEPS.put(t += 5, () -> onServer(server, () -> reportReplaced(server)));
        STEPS.put(t += 5, () -> reportCreativeTab());
        if (quick) {
            STEPS.put(t += 5, () -> mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)));
            STEPS.put(t += 40, () -> grab(mc, "jei_inventory"));
            STEPS.put(t + 20, () -> {
                log("DONE");
                mc.stop();
            });
            return;
        }
        String d = dagger();
        t = pounceTest(server, t + 10, "iron_" + d);
        t = pounceTest(server, t + 10, "diamond_" + d);
        if (ns.equals("farmersdelight")) {
            t = throwTest(mc, server, t + 10, "iron_" + d);
            t = throwTest(mc, server, t + 10, "diamond_" + d);
            t = visualTest(mc, server, t + 10, "iron_" + d);
            t = visualTest(mc, server, t + 10, "flint_" + d);
            STEPS.put(t + 20, () -> {
                log("DONE");
                mc.stop();
            });
            return;
        }
        t = chargeTest(server, t + 10, "iron_spear");
        t = throwTest(mc, server, t + 10, "iron_" + d);
        t = throwTest(mc, server, t + 10, "diamond_" + d);
        t = throwTest(mc, server, t + 10, "iron_spear");
        t = spinTest(mc, server, t + 10, "iron_longsword");
        t = spinTest(mc, server, t + 10, ns.equals("simplyswords") ? "iron_claymore" : "iron_greatsword");
        t = spinTest(mc, server, t + 10, "iron_halberd");
        t = visualTest(mc, server, t + 10, "iron_" + d);
        t = visualTest(mc, server, t + 10, "iron_spear");
        t = visualTest(mc, server, t + 10, ns.equals("simplyswords") ? "iron_claymore" : "iron_greatsword");
        t = visualTest(mc, server, t + 10, "iron_katana");
        t = visualTest(mc, server, t + 10, "iron_halberd");
        STEPS.put(t + 20, () -> {
            log("DONE");
            mc.stop();
        });
    }

    private static void reportMapping(MinecraftServer server, String path) {
        ServerPlayer player = player(server);
        ItemStack stack = stack(path);
        if (stack.isEmpty()) {
            log("mapping {}: NOT INSTALLED", path);
            return;
        }
        Item item = stack.getItem();
        WeaponSpec spec = CombatTooltips.specFor(stack);
        log("mapping {}: kind={} weapon={} stackDamage={} playerReach={} specReach={} charge={} throw={} scale={} speed={}",
                path, CombatTooltips.kindOf(stack), CombatWeapons.weapon(item), CombatWeapons.stackAttackDamage(stack),
                player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE), spec == null ? -1 : spec.reach(),
                spec == null ? -1 : CombatWeapons.chargeDamage(stack, spec), spec == null ? -1 : CombatWeapons.throwDamage(stack, spec),
                spec == null ? -1 : CombatWeapons.damageScale(stack, spec), player.getAttributeValue(Attributes.ATTACK_SPEED));
    }

    private static void reportInHandModels() {
        int ok = 0, missing = 0;
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
            if (!id.getNamespace().equals("fight_club") || !id.getPath().contains("/")) continue;
            if (!id.getPath().endsWith("/spear") && !id.getPath().endsWith("/longsword")) continue;
            boolean loaded = CombatModels.inHand(new ItemStack(BuiltInRegistries.ITEM.get(id))) != null;
            if (loaded) ok++;
            else missing++;
            log("inHand {}: {}", id, loaded ? "loaded" : "MISSING");
        }
        log("inHand summary: loaded={} missing={}", ok, missing);
    }

    private static void reportReplaced(MinecraftServer server) {
        for (String weapon : new String[]{"dagger", "longsword", "spear"}) {
            StringBuilder line = new StringBuilder();
            for (String material : new String[]{"wood", "stone", "copper", "iron", "gold", "diamond", "netherite"}) {
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath("fight_club", material + "_" + weapon);
                Item item = BuiltInRegistries.ITEM.get(id);
                line.append(material).append('=').append(ExternalWeapons.isReplaced(item) ? "hidden" : "shown")
                        .append(server.getRecipeManager().byKey(id).isPresent() ? "+recipe " : "-recipe ");
            }
            log("replaced {}: {}", weapon, line.toString().trim());
        }
    }

    private static void reportCreativeTab() {
        CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(ResourceLocation.fromNamespaceAndPath("fight_club", "combat"));
        if (tab == null) return;
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.world.item.CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(), true, mc.level.registryAccess());
        log("fight_club tab: {}", tab.getDisplayItems().stream().map(s -> BuiltInRegistries.ITEM.getKey(s.getItem()).getPath()).toList());
    }

    private static int pounceTest(MinecraftServer server, int t, String path) {
        String key = "pounce " + path;
        STEPS.put(t, () -> onServer(server, () -> {
            ServerPlayer player = arena(server, path);
            target(key, dummy(player, 3.2));
            player.resetAttackStrengthTicker();
            CombatChargeState.handle(player, CombatChargeState.START);
        }));
        STEPS.put(t + 40, () -> onServer(server, () ->
                CombatChargeState.handle(player(server), CombatChargeState.RELEASE, TARGETS.get(key))));
        STEPS.put(t + 70, () -> onServer(server, () -> report(server, key)));
        return t + 71;
    }

    private static int chargeTest(MinecraftServer server, int t, String path) {
        String key = "charge " + path;
        STEPS.put(t, () -> onServer(server, () -> {
            ServerPlayer player = arena(server, path);
            target(key, dummy(player, 3.5));
            CombatChargeState.handle(player, CombatChargeState.START);
        }));
        STEPS.put(t + 40, () -> onServer(server, () ->
                CombatChargeState.handle(player(server), CombatChargeState.RELEASE, TARGETS.get(key))));
        STEPS.put(t + 60, () -> onServer(server, () -> report(server, key)));
        return t + 61;
    }

    private static int throwTest(Minecraft mc, MinecraftServer server, int t, String path) {
        String key = "throw " + path;
        STEPS.put(t, () -> onServer(server, () -> target(key, dummy(arena(server, path), 7.0))));
        STEPS.put(t + 15, () -> {
            net.minecraft.client.KeyMapping.click(mc.options.keyUse.getDefaultKey());
            mc.options.keyUse.setDown(true);
        });
        STEPS.put(t + 45, () -> {
            log("{}: client using={} before release", key, mc.player.isUsingItem());
            mc.options.keyUse.setDown(false);
        });
        STEPS.put(t + 48, () -> onServer(server, () -> {
            ServerPlayer player = player(server);
            List<Entity> nearby = player.serverLevel().getEntities(player, player.getBoundingBox().inflate(16),
                    e -> !(e instanceof LivingEntity));
            log("{}: entities={} handAfter={}", key, nearby.stream().map(e -> BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString()).toList(),
                    BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()));
        }));
        STEPS.put(t + 90, () -> onServer(server, () -> {
            ServerPlayer player = player(server);
            ItemStack thrown = stack(path);
            WeaponSpec spec = CombatTooltips.specFor(thrown);
            report(server, key);
            log("{}: expected throw damage {}", key, spec == null ? -1 : (path.endsWith("spear")
                    ? spec.attackDamage() * CombatWeapons.damageScale(thrown, spec) : CombatWeapons.throwDamage(thrown, spec)));
            player.serverLevel().getEntities(player, player.getBoundingBox().inflate(32), e -> !(e instanceof LivingEntity))
                    .forEach(Entity::discard);
        }));
        return t + 91;
    }

    private static int spinTest(Minecraft mc, MinecraftServer server, int t, String path) {
        String key = "spin " + path;
        STEPS.put(t, () -> onServer(server, () -> target(key, dummy(arena(server, path), 2.2))));
        STEPS.put(t + 15, () -> {
            net.minecraft.client.KeyMapping.click(mc.options.keyUse.getDefaultKey());
            mc.options.keyUse.setDown(true);
        });
        STEPS.put(t + 55, () -> {
            log("{}: client using={} before release", key, mc.player.isUsingItem());
            mc.options.keyUse.setDown(false);
        });
        STEPS.put(t + 75, () -> onServer(server, () -> report(server, key)));
        return t + 120;
    }

    private static int visualTest(Minecraft mc, MinecraftServer server, int t, String path) {
        String name = ns + "_" + path.replace('/', '_');
        STEPS.put(t, () -> onServer(server, () -> {
            run(server, "kill @e[type=!minecraft:player]");
            run(server, "fill -3 -60 7 4 -56 7 minecraft:oak_planks");
            run(server, "fill -3 -60 1 4 -56 6 minecraft:air");
            arena(server, path);
        }));
        STEPS.put(t + 20, () -> {
            mc.options.hideGui = false;
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            net.minecraft.client.KeyMapping.click(mc.options.keyUse.getDefaultKey());
            mc.options.keyUse.setDown(true);
        });
        STEPS.put(t + 38, () -> grab(mc, name + "_pose_first"));
        STEPS.put(t + 39, () -> {
            mc.options.hideGui = true;
            camera(2.6, -58.2, 0.9, 90.0F, 8.0F);
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
        });
        STEPS.put(t + 46, () -> grab(mc, name + "_pose_side"));
        STEPS.put(t + 47, () -> camera(3.2, -58.3, 3.6, 90.0F, 4.0F));
        STEPS.put(t + 52, () -> mc.options.keyUse.setDown(false));
        STEPS.put(t + 55, () -> grab(mc, name + "_flight"));
        STEPS.put(t + 75, () -> camera(2.2, -58.0, 5.2, 50.0F, 12.0F));
        STEPS.put(t + 82, () -> grab(mc, name + "_stuck"));
        STEPS.put(t + 84, () -> {
            DevShowcase.cameraOn = false;
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            mc.options.hideGui = false;
        });
        STEPS.put(t + 100, () -> grab(mc, name + "_idle"));
        return t + 101;
    }

    private static void camera(double x, double y, double z, float yaw, float pitch) {
        DevShowcase.cameraOn = true;
        DevShowcase.camX = x;
        DevShowcase.camY = y;
        DevShowcase.camZ = z;
        DevShowcase.camYaw = yaw;
        DevShowcase.camPitch = pitch;
    }

    private static void grab(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "compat_" + name + ".png", mc.getMainRenderTarget(), message -> {});
        log("screenshot compat_{}.png using={}", name, mc.player.isUsingItem());
    }

    private static void target(String key, LivingEntity entity) {
        HEALTH.put(key, entity.getHealth());
        TARGETS.put(key, entity.getId());
    }

    private static void report(MinecraftServer server, String key) {
        ServerPlayer player = player(server);
        Integer id = TARGETS.get(key);
        Entity entity = id == null ? null : player.serverLevel().getEntity(id);
        float before = HEALTH.getOrDefault(key, -1f);
        float after = entity instanceof LivingEntity living ? living.getHealth() : -1f;
        ItemStack stack = player.getMainHandItem();
        WeaponSpec spec = CombatTooltips.specFor(stack);
        log("{} [{}]: damage dealt={} expectedCharge={} stackDamage={}", key,
                stack.isEmpty() ? "empty" : BuiltInRegistries.ITEM.getKey(stack.getItem()), before - after,
                spec == null ? -1 : CombatWeapons.chargeDamage(stack, spec), CombatWeapons.stackAttackDamage(stack));
        if (entity != null) entity.discard();
    }

    private static ItemStack stack(String path) {
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath(ns, path))
                .map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    private static ServerPlayer arena(MinecraftServer server, String path) {
        ServerPlayer player = player(server);
        com.craisinlord.fightclub.combat.CombatRecovery.clear(player);
        CombatChargeState.clear(player);
        player.setGameMode(GameType.SURVIVAL);
        player.getAbilities().invulnerable = true;
        player.teleportTo(player.serverLevel(), 0.5, -60, 0.5, 0.0F, 0.0F);
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        player.connection.teleport(0.5, -60, 0.5, 0.0F, 0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack(path));
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        player.resetAttackStrengthTicker();
        return player;
    }

    private static Zombie dummy(ServerPlayer player, double distance) {
        ServerLevel level = player.serverLevel();
        Zombie zombie = net.minecraft.world.entity.EntityType.HUSK.create(level);
        zombie.setNoAi(true);
        zombie.setPersistenceRequired();
        zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0);
        zombie.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
        zombie.setHealth(500.0F);
        zombie.moveTo(player.getX(), player.getY(), player.getZ() + distance, 180.0F, 0.0F);
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
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput().withPermission(4), command);
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
