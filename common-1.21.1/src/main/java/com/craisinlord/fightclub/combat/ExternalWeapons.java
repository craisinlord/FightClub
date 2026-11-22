package com.craisinlord.fightclub.combat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public final class ExternalWeapons {
    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger("fight_club/weapon_items");
    public static final String DIRECTORY = "weapon_items";
    private static final Set<String> WEAPONS = Set.of("dagger", "longsword", "spear", "sword", "shield");

    public record Entry(String weapon, String material, boolean rightClick, boolean specStats) {}

    private record Mappings(Map<Item, Entry> items, List<Map.Entry<TagKey<Item>, Entry>> tags) {
        static final Mappings EMPTY = new Mappings(Map.of(), List.of());
    }

    private static final AtomicReference<Mappings> VALUES = new AtomicReference<>(Mappings.EMPTY);
    private static final AtomicReference<Map<String, String>> RAW_JSON = new AtomicReference<>(Map.of());

    private ExternalWeapons() {}

    public static Entry get(Item item) {
        Mappings mappings = VALUES.get();
        if (mappings.items().isEmpty() && mappings.tags().isEmpty()) return null;
        Entry entry = mappings.items().get(item);
        if (entry != null) return entry;
        if (mappings.tags().isEmpty()) return null;
        Holder<Item> holder = item.builtInRegistryHolder();
        for (Map.Entry<TagKey<Item>, Entry> tag : mappings.tags()) {
            if (holder.is(tag.getKey())) return tag.getValue();
        }
        return null;
    }

    public static Map<String, String> rawJson() {
        return RAW_JSON.get();
    }

    public static void load(ResourceManager manager) {
        Map<String, String> raw = new HashMap<>();
        for (Map.Entry<ResourceLocation, Resource> file
                : manager.listResources(DIRECTORY, path -> path.getPath().endsWith(".json")).entrySet()) {
            try (Reader reader = file.getValue().openAsReader()) {
                raw.put(file.getKey().toString(), JsonParser.parseReader(reader).toString());
            } catch (Exception exception) {
                LOGGER.error("Ignoring unreadable weapon item file {}: {}", file.getKey(), exception.getMessage());
            }
        }
        apply(raw);
    }

    public static void apply(Map<String, String> raw) {
        Map<Item, Entry> items = new HashMap<>();
        List<Map.Entry<TagKey<Item>, Entry>> tags = new ArrayList<>();
        Map<String, String> valid = new HashMap<>();
        Map<String, WeaponSpec> mapped = new HashMap<>();
        for (String file : new java.util.TreeSet<>(raw.keySet())) {
            try {
                JsonObject json = JsonParser.parseString(raw.get(file)).getAsJsonObject();
                String weapon = json.get("weapon").getAsString();
                if (!WEAPONS.contains(weapon)) throw new IllegalStateException("unknown weapon \"" + weapon + "\"");
                String material = json.has("material") ? json.get("material").getAsString()
                        : weapon.equals("shield") ? "base" : "iron";
                if (json.has("spec")) {
                    WeaponSpec base = WeaponSpecs.getOrLoadBuiltin(material, weapon);
                    if (base == null) throw new IllegalStateException("\"spec\" needs a base spec " + material + "/" + weapon);
                    String own = "mapped_" + file.replaceAll("\\.json$", "").replaceAll("[^a-z0-9_]", "_");
                    JsonObject merged = base.toJson();
                    json.getAsJsonObject("spec").entrySet().forEach(field -> merged.add(field.getKey(), field.getValue()));
                    merged.addProperty("weapon", weapon);
                    merged.addProperty("material", own);
                    mapped.put(own + "/" + weapon, WeaponSpec.fromJson(ResourceLocation.parse(file), merged));
                    material = own;
                }
                Entry entry = new Entry(weapon, material,
                        !json.has("right_click") || json.get("right_click").getAsBoolean(),
                        json.has("spec_stats") && json.get("spec_stats").getAsBoolean());
                if (!json.has("spec") && WeaponSpecs.getOrLoadBuiltin(material, weapon) == null) {
                    LOGGER.warn("{}: no weapon spec {}/{} (add weapons/{}/{}.json); its items get no Fight Club stats",
                            file, material, weapon, material, weapon);
                }
                for (JsonElement element : json.getAsJsonArray("items")) {
                    String id = element.getAsString();
                    if (id.startsWith("#")) {
                        tags.add(Map.entry(TagKey.create(Registries.ITEM, ResourceLocation.parse(id.substring(1))), entry));
                        continue;
                    }
                    ResourceLocation itemId = ResourceLocation.parse(id);
                    BuiltInRegistries.ITEM.getOptional(itemId).ifPresentOrElse(item -> {
                        if (!(item instanceof CombatWeaponItem)) items.put(item, entry);
                    }, () -> LOGGER.debug("{}: item {} is not installed, skipping", file, itemId));
                }
                valid.put(file, raw.get(file));
            } catch (RuntimeException exception) {
                LOGGER.error("Ignoring invalid weapon item file {}: {}", file, exception.getMessage());
            }
        }
        WeaponSpecs.replaceMapped(mapped);
        VALUES.set(new Mappings(Map.copyOf(items), List.copyOf(tags)));
        RAW_JSON.set(Map.copyOf(valid));
        if (!valid.isEmpty()) LOGGER.info("Loaded {} Fight Club weapon item mappings ({} items, {} tags)", valid.size(), items.size(), tags.size());
    }
}
