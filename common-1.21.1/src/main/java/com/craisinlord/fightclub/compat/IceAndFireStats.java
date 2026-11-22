package com.craisinlord.fightclub.compat;

import com.craisinlord.fightclub.FightClub;
import com.craisinlord.fightclub.combat.CombatPlatform;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;

public final class IceAndFireStats {
    private static final String MOD_ID = "iceandfire";
    private static final String CONFIG = "com.iafenvoy.iceandfire.config.IafCommonConfig";
    private static final double DEFAULT_DRAGONSTEEL_DAMAGE = 25.0;
    private static final double SWORD_BONUS = 3.0;
    private static final List<String> DAMAGE_FIELDS = List.of("attack_damage", "charge_damage", "throw_damage");
    private static boolean warned;

    private IceAndFireStats() {}

    public static boolean isDragonsteel(String material) {
        return material.startsWith("dragonsteel_");
    }

    public static JsonObject scaleSpec(String material, JsonObject json) {
        if (!isDragonsteel(material)) return json;
        double factor = dragonsteelDamageFactor();
        if (Math.abs(factor - 1.0) < 1.0e-4) return json;
        JsonObject scaled = json.deepCopy();
        for (String field : DAMAGE_FIELDS) {
            JsonElement value = scaled.get(field);
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) continue;
            double amount = Math.round(value.getAsDouble() * factor * 10.0) / 10.0;
            scaled.addProperty(field, Math.min(1000.0, Math.max(0.0, amount)));
        }
        return scaled;
    }

    public static int dragonsteelDurability(int fallback) {
        Object value = configValue("dragonSteelBaseDurability");
        return value instanceof Number number && number.intValue() > 0 ? number.intValue() : fallback;
    }

    private static double dragonsteelDamageFactor() {
        Object value = configValue("dragonSteelBaseDamage");
        if (!(value instanceof Number number)) return 1.0;
        double damage = number.doubleValue();
        if (!(damage > 0.0)) return 1.0;
        return (damage + SWORD_BONUS) / (DEFAULT_DRAGONSTEEL_DAMAGE + SWORD_BONUS);
    }

    private static Object configValue(String field) {
        if (!CombatPlatform.isModLoaded(MOD_ID)) return null;
        try {
            Class<?> config = Class.forName(CONFIG);
            Object instance = config.getField("INSTANCE").get(null);
            Object armors = config.getField("armors").get(instance);
            Object entry = armors.getClass().getField(field).get(armors);
            return entry.getClass().getMethod("getValue").invoke(entry);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            if (!warned) {
                warned = true;
                FightClub.LOGGER.warn("Fight Club: couldn't read Ice and Fire's dragonsteel config, using bundled values ({})", e.toString());
            }
            return null;
        }
    }
}
