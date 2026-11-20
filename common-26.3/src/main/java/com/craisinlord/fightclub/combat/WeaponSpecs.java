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

    public static WeaponSpec get(String material, String weapon) {
        return VALUES.get().get(material + "/" + weapon);
    }

    public static WeaponSpec getOrLoadBuiltin(String material, String weapon) {
        WeaponSpec loaded = get(material, weapon);
        if (loaded != null) return loaded;
        String resource = "/data/fight_club/weapons/" + material + "/" + weapon + ".json";
        try (var stream = WeaponSpecs.class.getResourceAsStream(resource)) {
            if (stream == null) return null;
            WeaponSpec spec = WeaponSpec.fromJson(
                    Identifier.fromNamespaceAndPath("fight_club", "weapons/" + material + "/" + weapon + ".json"),
                    JsonParser.parseReader(new java.io.InputStreamReader(stream)).getAsJsonObject());
            Map<String, WeaponSpec> merged = new java.util.HashMap<>(VALUES.get());
            merged.put(material + "/" + weapon, spec);
            VALUES.set(Collections.unmodifiableMap(merged));
            return spec;
        } catch (Exception exception) {
            return null;
        }
    }

    public static Map<String, WeaponSpec> all() {
        return VALUES.get();
    }
}
