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
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
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
import java.util.TreeMap;

public final class DevSilentGearTest {
    private static final String TRIGGER = "fc_sgtest", WORLD = "fc_sgtest";
    private static final String[] GEAR = {"dagger", "knife", "katana", "spear", "sword", "machete", "trident", "mace", "shield"};
    private static Boolean enabled;
    private static boolean worldRequested, scheduled;
    private static int tick;
    private static final TreeMap<Integer, Runnable> STEPS = new TreeMap<>();
    private static final Map<String, List<ItemStack>> SAMPLES = new LinkedHashMap<>();
    private static final Map<String, Float> HEALTH = new LinkedHashMap<>();

    private DevSilentGearTest() {}

    public static void tick(Minecraft mc) {
        if (enabled == null) enabled = new File(mc.gameDirectory, TRIGGER).isFile();
        if (!enabled) return;
        mc.options.pauseOnLostFocus = false;
        if (mc.level != null && mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        if (mc.level == null || mc.player == null) {
            if (!worldRequested && mc.screen instanceof TitleScreen) {
                worldRequested = true;
                File old = new File(mc.gameDirectory, "saves/" + WORLD);
                deleteTree(old);
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
                FightClub.LOGGER.error("SGTEST", e);
            }
        }
    }

    private static void schedule(Minecraft mc, MinecraftServer server) {
        int t = 80;
        STEPS.put(t, () -> onServer(server, () -> {
            for (String c : new String[]{"gamerule doDaylightCycle false", "gamerule doMobSpawning false", "time set 18000",
                    "gamerule sendCommandFeedback false", "kill @e[type=!minecraft:player]"}) run(server, c);
        }));
        STEPS.put(t += 10, () -> collectSamples(mc));
        if (visualOnly(mc)) {
            STEPS.put(t += 2, () -> org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1600, 900));
            STEPS.put(t += 2, () -> onServer(server, () -> run(server, "time set 6000")));
            t = spinTest(mc, server, t + 10, "katana", "Diamond");
            t = visualTest(mc, server, t + 10, "sg_katana", () -> sample("katana", "Diamond"));
            t = visualTest(mc, server, t + 10, "fc_longsword", () -> new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("fight_club", "iron_longsword"))));
            t = visualTest(mc, server, t + 10, "sg_dagger", () -> sample("dagger", "Diamond"));
            t = visualTest(mc, server, t + 10, "fc_dagger", () -> new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("fight_club", "iron_dagger"))));
            t = visualTest(mc, server, t + 10, "sg_knife", () -> sample("knife", "Diamond"));
            t = visualTest(mc, server, t + 10, "sg_spear", () -> sample("spear", "Diamond"));
            t = visualTest(mc, server, t + 10, "fc_spear", () -> new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("fight_club", "iron_spear"))));
            STEPS.put(t + 20, () -> {
                log("DONE");
                mc.stop();
            });
            return;
        }
        STEPS.put(t += 5, () -> onServer(server, () -> reportMappings(server)));
        STEPS.put(t += 5, () -> reportCreativeTab(mc));
        t = pounceTest(server, t + 10, "dagger", "Iron");
        t = pounceTest(server, t + 10, "dagger", "Diamond");
        t = pounceTest(server, t + 10, "knife", "Iron");
        t = chargeTest(server, t + 10, "spear", "Iron");
        t = chargeTest(server, t + 10, "spear", "Diamond");
        t = throwTest(mc, server, t + 10, "dagger", "Iron");
        t = throwTest(mc, server, t + 10, "dagger", "Diamond");
        t = throwTest(mc, server, t + 10, "spear", "Iron");
        t = spinTest(mc, server, t + 10, "katana", "Iron");
        t = spinTest(mc, server, t + 10, "katana", "Diamond");
        t = swingTest(server, t + 10, "trident", "Iron");
        t = visualTest(mc, server, t + 10, "sg_dagger", () -> sample("dagger", "Diamond"));
        t = visualTest(mc, server, t + 10, "fc_dagger", () -> new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("fight_club", "iron_dagger"))));
        t = visualTest(mc, server, t + 10, "sg_knife", () -> sample("knife", "Diamond"));
        t = visualTest(mc, server, t + 10, "sg_spear", () -> sample("spear", "Diamond"));
        t = visualTest(mc, server, t + 10, "fc_spear", () -> new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("fight_club", "iron_spear"))));
        t = shotTest(mc, server, t + 10, "katana");
        STEPS.put(t + 20, () -> {
            log("DONE");
            mc.stop();
        });
    }

    private static void collectSamples(Minecraft mc) {
        CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(), true, mc.level.registryAccess());
        for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
            for (ItemStack stack : tab.getDisplayItems()) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (!id.getNamespace().equals("silentgear")) continue;
                for (String gear : GEAR) {
                    if (id.getPath().equals(gear)) {
                        List<ItemStack> list = SAMPLES.computeIfAbsent(gear, k -> new ArrayList<>());
                        if (list.stream().noneMatch(s -> ItemStack.isSameItemSameComponents(s, stack))) list.add(stack.copy());
                    }
                }
            }
        }
        SAMPLES.forEach((gear, list) -> log("samples {}: {} -> {}", gear, list.size(),
                list.stream().map(s -> s.getHoverName().getString()).toList()));
    }

    private static void reportMappings(MinecraftServer server) {
        ServerPlayer player = player(server);
        for (String gear : GEAR) {
            List<ItemStack> list = SAMPLES.get(gear);
            if (list == null || list.isEmpty()) {
                log("mapping {}: NO SAMPLE", gear);
                continue;
            }
            for (String material : new String[]{"Iron", "Diamond"}) {
                ItemStack stack = sample(gear, material);
                Item item = stack.getItem();
                WeaponSpec spec = CombatTooltips.specFor(stack);
                double[] range = {3.0};
                stack.forEachModifier(net.minecraft.world.entity.EquipmentSlot.MAINHAND, (attribute, modifier) -> {
                    if (attribute.is(Attributes.ENTITY_INTERACTION_RANGE)) range[0] += modifier.amount();
                });
                double damage = CombatWeapons.stackAttackDamage(stack);
                log("mapping {} [{}]: kind={} weapon={} trident={} mace={} stackDamage={} playerDamage={} reach={} specReach={} charge={} throw={} scale={}",
                        gear, stack.getHoverName().getString(), CombatTooltips.kindOf(stack), CombatWeapons.weapon(item),
                        CombatWeapons.isTrident(item), CombatWeapons.isMace(item), CombatWeapons.stackAttackDamage(stack), damage, range[0],
                        spec == null ? -1 : spec.reach(), spec == null ? -1 : CombatWeapons.chargeDamage(stack, spec),
                        spec == null ? -1 : CombatWeapons.throwDamage(stack, spec), spec == null ? -1 : CombatWeapons.damageScale(stack, spec));
            }
        }
        for (String weapon : new String[]{"dagger", "longsword", "spear"}) {
            for (String material : new String[]{"wood", "iron", "netherite"}) {
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath("fight_club", material + "_" + weapon);
                Item item = BuiltInRegistries.ITEM.get(id);
                boolean recipe = server.getRecipeManager().byKey(id).isPresent();
                log("replaced {}: isReplaced={} recipePresent={}", id, ExternalWeapons.isReplaced(item), recipe);
            }
        }
        log("vanilla iron sword untouched: weapon={}", CombatWeapons.weapon(BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace("iron_sword"))));
    }

    private static void reportCreativeTab(Minecraft mc) {
        CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(ResourceLocation.fromNamespaceAndPath("fight_club", "combat"));
        List<String> ids = tab.getDisplayItems().stream().map(s -> BuiltInRegistries.ITEM.getKey(s.getItem()).getPath()).toList();
        log("fight_club tab ({} items): {}", ids.size(), ids);
        List<String> combat = BuiltInRegistries.CREATIVE_MODE_TAB.get(CreativeModeTabs.COMBAT).getDisplayItems().stream()
                .map(s -> BuiltInRegistries.ITEM.getKey(s.getItem()))
                .filter(id -> id.getNamespace().equals("fight_club")).map(ResourceLocation::getPath).toList();
        log("vanilla combat tab fight_club entries: {}", combat);
    }

    private static int pounceTest(MinecraftServer server, int t, String gear, String sample) {
        String key = "pounce " + sample + " " + gear;
        STEPS.put(t, () -> onServer(server, () -> {
            ServerPlayer player = arena(server, gear, sample);
            Zombie zombie = dummy(server, player, 3.2);
            HEALTH.put(key, zombie.getHealth());
            zombieIds.put(key, zombie.getId());
            player.resetAttackStrengthTicker();
            CombatChargeState.handle(player, CombatChargeState.START);
        }));
        int hold = 40;
        STEPS.put(t + hold, () -> onServer(server, () ->
                CombatChargeState.handle(player(server), CombatChargeState.RELEASE, zombieIds.get(key))));
        STEPS.put(t + hold + 30, () -> onServer(server, () -> report(server, key)));
        return t + hold + 31;
    }

    private static int chargeTest(MinecraftServer server, int t, String gear, String sample) {
        String key = "charge " + sample + " " + gear;
        STEPS.put(t, () -> onServer(server, () -> {
            ServerPlayer player = arena(server, gear, sample);
            Zombie zombie = dummy(server, player, 3.5);
            HEALTH.put(key, zombie.getHealth());
            zombieIds.put(key, zombie.getId());
            CombatChargeState.handle(player, CombatChargeState.START);
        }));
        STEPS.put(t + 40, () -> onServer(server, () ->
                CombatChargeState.handle(player(server), CombatChargeState.RELEASE, zombieIds.get(key))));
        STEPS.put(t + 60, () -> onServer(server, () -> report(server, key)));
        return t + 61;
    }

    private static int throwTest(Minecraft mc, MinecraftServer server, int t, String gear, String sample) {
        String key = "throw " + sample + " " + gear;
        STEPS.put(t, () -> onServer(server, () -> {
            ServerPlayer player = arena(server, gear, sample);
            LivingEntity target = dummy(server, player, 7.0);
            HEALTH.put(key, target.getHealth());
            zombieIds.put(key, target.getId());
        }));
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
            List<net.minecraft.world.entity.Entity> projectiles = player.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.Entity.class,
                    player.getBoundingBox().inflate(16), e -> e instanceof DaggerProjectile || e instanceof SpearProjectile);
            log("{}: projectiles={} handAfter={}", key, projectiles.size(), BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()));
            for (net.minecraft.world.entity.Entity e : projectiles) {
                if (e instanceof com.craisinlord.fightclub.combat.CombatProjectile projectile) {
                    log("{}: projectile carries {} with {} component changes", key, projectile.getItem().getHoverName().getString(),
                            projectile.getItem().getComponentsPatch().size());
                }
            }
        }));
        STEPS.put(t + 90, () -> onServer(server, () -> {
            ServerPlayer player = player(server);
            ItemStack thrown = sample(gear, sample);
            WeaponSpec spec = CombatTooltips.specFor(thrown);
            report(server, key);
            log("{}: expected throw damage {}", key, spec == null ? -1 : (gear.equals("spear")
                    ? spec.attackDamage() * CombatWeapons.damageScale(thrown, spec) : CombatWeapons.throwDamage(thrown, spec)));
            player.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.Entity.class, player.getBoundingBox().inflate(32),
                    e -> e instanceof DaggerProjectile || e instanceof SpearProjectile || e instanceof net.minecraft.world.entity.item.ItemEntity)
                    .forEach(net.minecraft.world.entity.Entity::discard);
        }));
        return t + 91;
    }

    private static int spinTest(Minecraft mc, MinecraftServer server, int t, String gear, String sample) {
        String key = "spin " + sample + " " + gear;
        STEPS.put(t, () -> onServer(server, () -> {
            ServerPlayer player = arena(server, gear, sample);
            LivingEntity target = dummy(server, player, 2.2);
            HEALTH.put(key, target.getHealth());
            zombieIds.put(key, target.getId());
        }));
        STEPS.put(t + 15, () -> {
            net.minecraft.client.KeyMapping.click(mc.options.keyUse.getDefaultKey());
            mc.options.keyUse.setDown(true);
        });
        STEPS.put(t + 55, () -> {
            log("{}: client using={} before release", key, mc.player.isUsingItem());
            mc.options.keyUse.setDown(false);
        });
        STEPS.put(t + 75, () -> onServer(server, () -> report(server, key)));
        return t + 76;
    }

    private static int swingTest(MinecraftServer server, int t, String gear, String sample) {
        String key = "swing " + sample + " " + gear;
        STEPS.put(t, () -> onServer(server, () -> {
            ServerPlayer player = arena(server, gear, sample);
            LivingEntity first = dummy(server, player, 2.5);
            LivingEntity behind = dummy(server, player, 3.6);
            HEALTH.put(key, first.getHealth());
            zombieIds.put(key, first.getId());
            HEALTH.put(key + " (behind)", behind.getHealth());
            zombieIds.put(key + " (behind)", behind.getId());
        }));
        STEPS.put(t + 30, () -> onServer(server, () -> {
            ServerPlayer player = player(server);
            Integer id = zombieIds.get(key);
            if (id != null && player.serverLevel().getEntity(id) instanceof LivingEntity first) player.attack(first);
        }));
        STEPS.put(t + 45, () -> onServer(server, () -> {
            report(server, key);
            report(server, key + " (behind)");
        }));
        return t + 46;
    }

    private static int visualTest(Minecraft mc, MinecraftServer server, int t, String name, java.util.function.Supplier<ItemStack> item) {
        STEPS.put(t, () -> onServer(server, () -> {
            run(server, "kill @e[type=!minecraft:player]");
            run(server, "fill -3 -60 7 4 -56 7 minecraft:oak_planks");
            run(server, "fill -3 -60 1 4 -56 6 minecraft:air");
            ServerPlayer player = arena(server, "dagger", "Iron");
            player.setItemInHand(InteractionHand.MAIN_HAND, item.get());
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
        STEPS.put(t + 54, () -> grab(mc, name + "_flight"));
        STEPS.put(t + 56, () -> grab(mc, name + "_flight2"));
        STEPS.put(t + 75, () -> camera(2.2, -58.0, 5.2, 50.0F, 12.0F));
        STEPS.put(t + 82, () -> grab(mc, name + "_stuck"));
        STEPS.put(t + 84, () -> {
            DevShowcase.cameraOn = false;
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            mc.options.hideGui = false;
        });
        return t + 85;
    }

    private static void camera(double x, double y, double z, float yaw, float pitch) {
        DevShowcase.cameraOn = true;
        DevShowcase.camX = x;
        DevShowcase.camY = y;
        DevShowcase.camZ = z;
        DevShowcase.camYaw = yaw;
        DevShowcase.camPitch = pitch;
    }

    private static boolean visualOnly(Minecraft mc) {
        try {
            return java.nio.file.Files.readString(new File(mc.gameDirectory, TRIGGER).toPath()).contains("visual");
        } catch (java.io.IOException e) {
            return false;
        }
    }

    private static void grab(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "sgtest_" + name + ".png", mc.getMainRenderTarget(), message -> {});
        log("screenshot sgtest_{}.png using={}", name, mc.player.isUsingItem());
    }

    private static int shotTest(Minecraft mc, MinecraftServer server, int t, String gear) {
        STEPS.put(t, () -> onServer(server, () -> {
            run(server, "kill @e[type=!minecraft:player]");
            arena(server, gear, "Diamond");
        }));
        STEPS.put(t + 30, () -> {
            mc.options.hideGui = false;
            Screenshot.grab(mc.gameDirectory, "sgtest_" + gear + ".png", mc.getMainRenderTarget(), message -> {});
            log("screenshot sgtest_{}.png", gear);
        });
        return t + 31;
    }

    private static final Map<String, Integer> zombieIds = new LinkedHashMap<>();

    private static void report(MinecraftServer server, String key) {
        ServerPlayer player = player(server);
        Integer id = zombieIds.get(key);
        net.minecraft.world.entity.Entity entity = id == null ? null : player.serverLevel().getEntity(id);
        float before = HEALTH.getOrDefault(key, -1f);
        float after = entity instanceof LivingEntity living ? living.getHealth() : -1f;
        ItemStack stack = player.getMainHandItem();
        WeaponSpec spec = CombatTooltips.specFor(stack);
        log("{} [{}]: damage dealt={} (before={} after={}) expectedCharge={} stackDamage={}", key,
                stack.isEmpty() ? "empty" : stack.getHoverName().getString(), before - after, before, after,
                spec == null ? -1 : CombatWeapons.chargeDamage(stack, spec), CombatWeapons.stackAttackDamage(stack));
        if (entity != null) entity.discard();
    }

    private static ServerPlayer arena(MinecraftServer server, String gear, String sample) {
        ServerPlayer player = player(server);
        com.craisinlord.fightclub.combat.CombatRecovery.clear(player);
        CombatChargeState.clear(player);
        player.setGameMode(GameType.SURVIVAL);
        player.getAbilities().invulnerable = true;
        player.teleportTo(player.serverLevel(), 0.5, -60, 0.5, 0.0F, 0.0F);
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        player.connection.teleport(0.5, -60, 0.5, 0.0F, 0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, sample(gear, sample));
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        player.resetAttackStrengthTicker();
        return player;
    }

    private static ItemStack sample(String gear, String material) {
        List<ItemStack> list = SAMPLES.get(gear);
        if (list == null) return ItemStack.EMPTY;
        return list.stream().filter(s -> s.getHoverName().getString().startsWith(material + " ")).findFirst()
                .orElse(ItemStack.EMPTY).copy();
    }

    private static Zombie dummy(MinecraftServer server, ServerPlayer player, double distance) {
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
                FightClub.LOGGER.error("SGTEST", e);
            }
        });
    }

    private static void run(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput().withPermission(4), command);
    }

    private static void log(String message, Object... args) {
        FightClub.LOGGER.info("SGTEST " + message, args);
    }

    private static void deleteTree(File file) {
        if (!file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }
}
