package com.craisinlord.fightclub.combat;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;

public final class WeaponSpecs {
    private static final AtomicReference<Map<String, WeaponSpec>> VALUES =
            new AtomicReference<>(Map.of());

    private WeaponSpecs() {}

    public static void replace(Map<String, WeaponSpec> specs) {
        VALUES.set(Collections.unmodifiableMap(Map.copyOf(specs)));
    }

    private static final AtomicReference<Map<String, WeaponSpec>> MAPPED = new AtomicReference<>(Map.of());
    private static final java.util.Set<String> MISSING = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static void replaceMapped(Map<String, WeaponSpec> specs) {
        MAPPED.set(Map.copyOf(specs));
    }

    public static WeaponSpec get(String material, String weapon) {
        return get(material + "/" + weapon);
    }

    private static WeaponSpec get(String key) {
        WeaponSpec spec = VALUES.get().get(key);
        return spec != null ? spec : MAPPED.get().get(key);
    }

    public static WeaponSpec getOrLoadBuiltin(String material, String weapon) {
        String key = material + "/" + weapon;
        WeaponSpec loaded = get(key);
        if (loaded != null || MISSING.contains(key)) return loaded;
        String resource = "/data/fight_club/weapons/" + key + ".json";
        try (var stream = WeaponSpecs.class.getResourceAsStream(resource)) {
            if (stream == null) {
                MISSING.add(key);
                return null;
            }
            WeaponSpec spec = WeaponSpec.fromJson(
                    Identifier.fromNamespaceAndPath("fight_club", "weapons/" + key + ".json"),
                    JsonParser.parseReader(new java.io.InputStreamReader(stream)).getAsJsonObject());
            Map<String, WeaponSpec> merged = new java.util.HashMap<>(VALUES.get());
            merged.put(key, spec);
            VALUES.set(Collections.unmodifiableMap(merged));
            return spec;
        } catch (Exception exception) {
            MISSING.add(key);
            return null;
        }
    }

    public static Map<String, WeaponSpec> all() {
        return VALUES.get();
    }
}
