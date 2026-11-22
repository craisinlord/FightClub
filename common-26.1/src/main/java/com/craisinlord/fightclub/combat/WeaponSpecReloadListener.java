package com.craisinlord.fightclub.combat;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class WeaponSpecReloadListener extends SimpleJsonResourceReloadListener<JsonElement> {
    private static final Codec<JsonElement> JSON_CODEC = Codec.PASSTHROUGH.xmap(
            dynamic -> (JsonElement) dynamic.getValue(),
            value -> new Dynamic<>(JsonOps.INSTANCE, value));
    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger("fight_club/weapon_specs");

    public WeaponSpecReloadListener() {
        super(JSON_CODEC, FileToIdConverter.json("weapons"));
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> resources, ResourceManager manager, ProfilerFiller profiler) {
        Map<String, WeaponSpec> parsed = new HashMap<>();
        for (Map.Entry<Identifier, JsonElement> entry : resources.entrySet()) {
            Identifier resourceId = entry.getKey();
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
        LOGGER.info("Loaded {} Fight Club weapon specifications", parsed.size());
        ExternalWeapons.load(manager);
    }
}
