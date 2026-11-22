package com.craisinlord.fightclub.combat;

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
    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger("fight_club/weapon_specs");

    public WeaponSpecReloadListener() {
        super(GSON, "weapons");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager manager, ProfilerFiller profiler) {
        com.craisinlord.fightclub.CombatConfig.reload();
        Map<String, WeaponSpec> parsed = new HashMap<>();
        Map<String, String> raw = new HashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : resources.entrySet()) {
            ResourceLocation resourceId = entry.getKey();
            String[] parts = resourceId.getPath().split("/");
            if (parts.length != 2 || !entry.getValue().isJsonObject()) {
                LOGGER.warn("Ignoring weapon spec {}: expected weapons/<material>/<weapon>.json", resourceId);
                continue;
            }
            try {
                JsonObject json = com.craisinlord.fightclub.compat.IceAndFireStats.scaleSpec(parts[0], entry.getValue().getAsJsonObject());
                WeaponSpec spec = WeaponSpec.fromJson(resourceId, json);
                String weapon = parts[1].endsWith(".json") ? parts[1].substring(0, parts[1].length() - 5) : parts[1];
                parsed.put(parts[0] + "/" + weapon, spec);
                raw.put(parts[0] + "/" + weapon, json.toString());
            } catch (JsonParseException | IllegalStateException exception) {
                LOGGER.error("Ignoring invalid weapon spec {}: {}", resourceId, exception.getMessage());
            }
        }
        WeaponSpecs.replace(parsed, raw);
        LOGGER.info("Loaded {} Fight Club weapon specifications", parsed.size());
        ExternalWeapons.load(manager);
    }
}
