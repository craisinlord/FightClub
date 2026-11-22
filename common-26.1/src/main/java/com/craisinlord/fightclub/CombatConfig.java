package com.craisinlord.fightclub;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CombatConfig {
    private record Option(String section, String key, String comment, Object defaultValue, boolean synced, boolean restart) {}

    private static final Map<String, Option> OPTIONS = new LinkedHashMap<>();
    private static final Map<String, Object> VALUES = new java.util.concurrent.ConcurrentHashMap<>();
    private static Path file;

    public static final String SWORD_BLOCKING = gameplay("sword_blocking", true, "Right-click with a sword to block frontal attacks.");
    public static final String SHIELD_BASH = gameplay("shield_bash", true, "Press attack while blocking with a shield to bash enemies back.");
    public static final String NARROW_SHIELD = gameplay("narrow_shield_angle", true, "Shields only block within their blocking angle (weapons/base/shield.json) instead of vanilla's 180 degrees.");
    public static final String LONGSWORD_OFFHAND = gameplay("longsword_offhand_penalty", true, "Longswords attack slower while anything is in the offhand.");
    public static final String LONGSWORD_NO_CRITS = gameplay("longsword_no_crits", true, "Longswords can't land normal critical hits.");
    public static final String SPEAR_CHARGE_CRITS = gameplay("spear_charge_crits", true, "Spears and tridents crit only on charged hits: every charge hit plays the crit effects (no extra damage), and normal jump crits are off.");
    public static final String PENETRATION = gameplay("thrust_penetration", true, "Spear and trident thrusts pierce mobs lined up behind the target.");
    public static final String DATA_SWEEPS = gameplay("data_driven_sweeps", true, "Sweeps use each weapon's sweep radius and angle (longsword wider, mace sweeps). Off: vanilla sweeps.");
    public static final String CHARGED_ATTACKS = gameplay("charged_attacks", true, "Hold attack with a spear or trident to lower it for a charge, or with a dagger to pounce on the mob nearest your crosshair.");
    public static final String RUNNING_CHARGE = gameplay("running_charge", true, "With a spear or trident lowered, running into a mob strikes it without releasing.");
    public static final String THROWING = gameplay("weapon_throwing", true, "Right-click to throw spears and daggers.");
    public static final String RECOVERY = gameplay("recovery", true, "Special attacks (charged hits, throws, smashes) have a short recovery before the weapon can be used again.");
    public static final String LONGSWORD_SPIN = gameplay("longsword_spin", true, "Hold right-click with a longsword to charge a spin attack that hits everything around you.");
    public static final String MACE_HIT_STREAK = gameplay("mace_hit_streak", true, "Consecutive mace hits stack an attack speed bonus (weapons/base/mace.json); a miss or a pause ends the streak.");
    public static final String ZOMBIE_LONGSWORDS = gameplay("zombie_longswords", true, "Some zombies, husks and zombie villagers spawn holding a longsword.");
    public static final String SKELETON_KNIVES = gameplay("skeleton_knives", true, "Some skeletons, strays and bogged spawn with a Loyalty dagger they throw. Off also stops existing ones from throwing.");
    public static final String NO_ANVIL_LIMIT = option("vanilla", "remove_anvil_limit", true, true, false, "Remove the anvil's \"Too Expensive!\" limit. XP costs still rise as in vanilla.");
    public static final String POTION_STACK = option("vanilla", "potion_stack_size", 16, false, true, "Stack size for potions, splash potions and lingering potions (1 = vanilla). Needs a restart.");
    public static final String ANIMATIONS = option("client", "animations", true, false, false, "Fight Club's weapon poses and attack animations (first and third person).");
    public static final String SWING_SOUNDS = option("client", "swing_sounds", true, false, false, "Swing whooshes for daggers, spears, tridents and longswords (off with Better Combat, which has its own).");
    public static final String CHARGE_INDICATOR = option("client", "charge_indicator", true, false, false, "Show charge progress on the attack indicator while holding a charge.");
    public static final String POUNCE_OUTLINE = option("client", "pounce_outline", true, false, false, "Outline the mob a charged dagger will pounce on.");
    public static final String POUNCE_FOV = option("client", "pounce_fov", true, false, false, "Narrow the view when a dagger pounce is ready and widen it during the dash (scaled by FOV Effects).");

    private CombatConfig() {}

    private static String gameplay(String key, boolean defaultValue, String comment) {
        return option("gameplay", key, defaultValue, true, false, comment);
    }

    private static String option(String section, String key, Object defaultValue, boolean synced, boolean restart, String comment) {
        OPTIONS.put(key, new Option(section, key, comment, defaultValue, synced, restart));
        VALUES.put(key, defaultValue);
        return key;
    }

    public static boolean enabled(String key) {
        return VALUES.get(key) instanceof Boolean value ? value : (Boolean) OPTIONS.get(key).defaultValue();
    }

    public static int intValue(String key) {
        return VALUES.get(key) instanceof Number value ? value.intValue() : ((Number) OPTIONS.get(key).defaultValue()).intValue();
    }

    public static synchronized void load(Path configDir) {
        file = configDir.resolve("fight_club.toml");
        Map<String, String> raw = new HashMap<>();
        if (Files.isRegularFile(file)) {
            try {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    String text = line.strip();
                    int hash = text.indexOf('#');
                    if (hash >= 0) text = text.substring(0, hash).strip();
                    int equals = text.indexOf('=');
                    if (text.isEmpty() || text.startsWith("[") || equals < 0) continue;
                    raw.put(text.substring(0, equals).strip(), text.substring(equals + 1).strip());
                }
            } catch (IOException exception) {
                FightClub.LOGGER.error("Couldn't read {}; using defaults: {}", file, exception.getMessage());
            }
        }
        for (Option option : OPTIONS.values()) {
            String value = raw.get(option.key());
            if (value == null) continue;
            Object parsed = parse(option, value);
            if (parsed != null) VALUES.put(option.key(), parsed);
            else FightClub.LOGGER.warn("Invalid value '{}' for {} in {}; using {}", value, option.key(), file, VALUES.get(option.key()));
        }
        save();
    }

    public static void reload() {
        if (file != null) load(file.getParent());
    }

    private static Object parse(Option option, String value) {
        if (option.defaultValue() instanceof Boolean) {
            if (value.equalsIgnoreCase("true")) return true;
            if (value.equalsIgnoreCase("false")) return false;
            return null;
        }
        try {
            return Math.max(1, Math.min(64, Integer.parseInt(value)));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static void save() {
        List<String> lines = new ArrayList<>();
        lines.add("# Fight Club configuration.");
        lines.add("# [gameplay] and [vanilla] are set by the server and sent to players; [client] is per player.");
        lines.add("# Weapon numbers (damage, reach, sweep, charge...) live in the weapon datapack files:");
        lines.add("# data/fight_club/weapons/<material>/<weapon>.json");
        String section = null;
        for (Option option : OPTIONS.values()) {
            if (!option.section().equals(section)) {
                section = option.section();
                lines.add("");
                lines.add("[" + section + "]");
            }
            lines.add("# " + option.comment() + " Default: " + option.defaultValue());
            lines.add(option.key() + " = " + VALUES.get(option.key()));
        }
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            FightClub.LOGGER.error("Couldn't write {}: {}", file, exception.getMessage());
        }
    }

    public static Map<String, String> syncedValues() {
        Map<String, String> synced = new HashMap<>();
        OPTIONS.values().stream().filter(Option::synced).forEach(option -> synced.put(option.key(), String.valueOf(VALUES.get(option.key()))));
        return synced;
    }

    public static void applySynced(Map<String, String> values) {
        values.forEach((key, value) -> {
            Option option = OPTIONS.get(key);
            if (option == null || !option.synced()) return;
            Object parsed = parse(option, value);
            if (parsed != null) VALUES.put(key, parsed);
        });
    }
}
