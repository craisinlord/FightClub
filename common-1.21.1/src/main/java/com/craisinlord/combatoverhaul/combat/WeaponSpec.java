package com.craisinlord.combatoverhaul.combat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.resources.ResourceLocation;

public record WeaponSpec(
        String weapon,
        String material,
        AttackStyle attackStyle,
        float attackDamage,
        float attackSpeed,
        float reach,
        float sweepRadius,
        float sweepAngleDegrees,
        float thrustWidth,
        int penetrationCount,
        boolean criticalHits,
        float offhandSpeedMultiplier,
        int chargeTicks,
        float chargeDamage,
        float chargeKnockback,
        int recoveryTicks,
        boolean blocking,
        float blockingAngleDegrees,
        float blockingDamageMultiplier,
        boolean throwing,
        float throwVelocity,
        float throwRange
) {
    public enum AttackStyle { SWEEP, THRUST, IMPACT, SINGLE_TARGET }

    public static WeaponSpec fromJson(ResourceLocation id, JsonObject json) {
        String weapon = requiredString(id, json, "weapon");
        String material = requiredString(id, json, "material");
        AttackStyle style;
        try {
            style = AttackStyle.valueOf(requiredString(id, json, "attack_style").toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new JsonParseException(id + ": attack_style must be sweep, thrust, impact, or single_target");
        }

        float attackDamage = number(id, json, "attack_damage", 0.0f, 1000.0f);
        float attackSpeed = number(id, json, "attack_speed", 0.01f, 100.0f);
        float reach = number(id, json, "reach", 0.0f, 32.0f);
        float sweepRadius = number(id, json, "sweep_radius", 0.0f, 32.0f);
        float sweepAngle = number(id, json, "sweep_angle_degrees", 0.0f, 360.0f);
        float thrustWidth = number(id, json, "thrust_width", 0.0f, 16.0f);
        int penetration = integer(id, json, "penetration_count", 0, 64);
        boolean criticalHits = booleanValue(id, json, "critical_hits");
        float offhandMultiplier = number(id, json, "offhand_speed_multiplier", 0.0f, 1.0f);
        int chargeTicks = integer(id, json, "charge_ticks", 0, 20 * 60);
        float chargeDamage = number(id, json, "charge_damage", 0.0f, 1000.0f);
        float chargeKnockback = number(id, json, "charge_knockback", 0.0f, 100.0f);
        int recoveryTicks = integer(id, json, "recovery_ticks", 0, 20 * 60);
        boolean blocking = booleanValue(id, json, "blocking");
        float blockingAngle = number(id, json, "blocking_angle_degrees", 0.0f, 360.0f);
        float blockingMultiplier = number(id, json, "blocking_damage_multiplier", 0.0f, 1.0f);
        boolean throwing = booleanValue(id, json, "throwing");
        float throwVelocity = number(id, json, "throw_velocity", 0.0f, 100.0f);
        float throwRange = number(id, json, "throw_range", 0.0f, 256.0f);

        if (blocking && blockingMultiplier >= 1.0f) {
            throw new JsonParseException(id + ": blocking_damage_multiplier must reduce damage when blocking is enabled");
        }
        if (throwing && (throwVelocity <= 0.0f || throwRange <= 0.0f)) {
            throw new JsonParseException(id + ": throwing weapons need positive throw_velocity and throw_range");
        }
        return new WeaponSpec(weapon, material, style, attackDamage, attackSpeed, reach, sweepRadius,
                sweepAngle, thrustWidth, penetration, criticalHits, offhandMultiplier, chargeTicks,
                chargeDamage, chargeKnockback, recoveryTicks, blocking, blockingAngle,
                blockingMultiplier, throwing, throwVelocity, throwRange);
    }

    private static String requiredString(ResourceLocation id, JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive() || !json.get(key).getAsJsonPrimitive().isString()) {
            throw new JsonParseException(id + ": missing string field '" + key + "'");
        }
        return json.get(key).getAsString();
    }

    private static boolean booleanValue(ResourceLocation id, JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive() || !json.get(key).getAsJsonPrimitive().isBoolean()) {
            throw new JsonParseException(id + ": missing boolean field '" + key + "'");
        }
        return json.get(key).getAsBoolean();
    }

    private static float number(ResourceLocation id, JsonObject json, String key, float min, float max) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive() || !json.get(key).getAsJsonPrimitive().isNumber()) {
            throw new JsonParseException(id + ": missing numeric field '" + key + "'");
        }
        float value = json.get(key).getAsFloat();
        if (!Float.isFinite(value) || value < min || value > max) {
            throw new JsonParseException(id + ": field '" + key + "' must be between " + min + " and " + max);
        }
        return value;
    }

    private static int integer(ResourceLocation id, JsonObject json, String key, int min, int max) {
        float value = number(id, json, key, min, max);
        if (value != Math.round(value)) {
            throw new JsonParseException(id + ": field '" + key + "' must be an integer");
        }
        return Math.round(value);
    }
}
