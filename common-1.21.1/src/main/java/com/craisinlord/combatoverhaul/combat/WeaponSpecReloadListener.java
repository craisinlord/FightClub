package com.craisinlord.combatoverhaul.combat;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class WeaponSpecReloadListener extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();
    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger("combat_overhaul/weapon_specs");

    public WeaponSpecReloadListener() {
        super(GSON, "weapons");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager manager, ProfilerFiller profiler) {
        Map<String, WeaponSpec> parsed = new HashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : resources.entrySet()) {
            ResourceLocation resourceId = entry.getKey();
            String[] parts = resourceId.getPath().split("/");
            if (parts.length != 2 || !entry.getValue().isJsonObject()) {
                LOGGER.warn("Ignoring weapon spec {}: expected weapons/<material>/<weapon>.json", resourceId);
                continue;
            }
            try {
                WeaponSpec spec = WeaponSpec.fromJson(resourceId, entry.getValue().getAsJsonObject());
                String key = parts[0] + "/" + parts[1];
                parsed.put(key.substring(0, key.length() - 5), spec);
            } catch (JsonParseException | IllegalStateException exception) {
                LOGGER.error("Ignoring invalid weapon spec {}: {}", resourceId, exception.getMessage());
            }
        }
        WeaponSpecs.replace(parsed);
        LOGGER.info("Loaded {} Combat Overhaul weapon specifications", parsed.size());
    }
}
