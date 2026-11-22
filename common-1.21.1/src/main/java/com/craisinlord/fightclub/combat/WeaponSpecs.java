package com.craisinlord.fightclub.combat;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

public final class WeaponSpecs {
    private static final AtomicReference<Map<String, WeaponSpec>> VALUES =
            new AtomicReference<>(Map.of());

    private WeaponSpecs() {}

    private static final AtomicReference<Map<String, String>> RAW_JSON = new AtomicReference<>(Map.of());

    public static void replace(Map<String, WeaponSpec> specs) {
        VALUES.set(Collections.unmodifiableMap(Map.copyOf(specs)));
    }

    public static void replace(Map<String, WeaponSpec> specs, Map<String, String> rawJson) {
        replace(specs);
        RAW_JSON.set(Map.copyOf(rawJson));
    }

    public static Map<String, String> rawJson() {
        return RAW_JSON.get();
    }

    public static void applySynced(Map<String, String> rawJson) {
        Map<String, WeaponSpec> parsed = new java.util.HashMap<>();
        rawJson.forEach((key, json) -> {
            try {
                parsed.put(key, WeaponSpec.fromJson(ResourceLocation.fromNamespaceAndPath("fight_club", "weapons/" + key),
                        JsonParser.parseString(json).getAsJsonObject()));
            } catch (RuntimeException exception) {
                com.craisinlord.fightclub.FightClub.LOGGER.warn("Ignoring synced weapon spec {}: {}", key, exception.getMessage());
            }
        });
        if (!parsed.isEmpty()) replace(parsed, rawJson);
    }

    private static final AtomicReference<Map<String, WeaponSpec>> MAPPED = new AtomicReference<>(Map.of());

    public static void replaceMapped(Map<String, WeaponSpec> specs) {
        MAPPED.set(Map.copyOf(specs));
    }

    public static WeaponSpec get(String material, String weapon) {
        String key = material + "/" + weapon;
        WeaponSpec spec = VALUES.get().get(key);
        return spec != null ? spec : MAPPED.get().get(key);
    }

    private static final Map<String, WeaponSpec> DEFAULTS = new java.util.concurrent.ConcurrentHashMap<>();

    public static void registerDefault(WeaponSpec spec) {
        DEFAULTS.put(spec.material() + "/" + spec.weapon(), spec);
    }

    private static final java.util.Set<String> MISSING = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static WeaponSpec getOrLoadBuiltin(String material, String weapon) {
        WeaponSpec loaded = get(material, weapon);
        if (loaded != null) return loaded;
        String key = material + "/" + weapon;
        WeaponSpec registered = DEFAULTS.get(key);
        if (registered != null) return registered;
        if (MISSING.contains(key)) return null;
        String resource = "/data/fight_club/weapons/" + key + ".json";
        try (var stream = WeaponSpecs.class.getResourceAsStream(resource)) {
            if (stream == null) {
                MISSING.add(key);
                return null;
            }
            WeaponSpec spec = WeaponSpec.fromJson(
                    ResourceLocation.fromNamespaceAndPath("fight_club", "weapons/" + key + ".json"),
                    JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject());
            VALUES.updateAndGet(current -> {
                Map<String, WeaponSpec> merged = new java.util.HashMap<>(current);
                merged.put(key, spec);
                return Collections.unmodifiableMap(merged);
            });
            return spec;
        } catch (Exception exception) {
            com.craisinlord.fightclub.FightClub.LOGGER.error("Invalid built-in weapon spec {}: {}", resource, exception.getMessage());
            MISSING.add(key);
            return null;
        }
    }

    public static Map<String, WeaponSpec> all() {
        return VALUES.get();
    }
}
