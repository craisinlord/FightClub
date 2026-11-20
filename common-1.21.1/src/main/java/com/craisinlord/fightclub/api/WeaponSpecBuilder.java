package com.craisinlord.fightclub.api;

import com.craisinlord.fightclub.combat.WeaponSpec;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

/**
 * Builds a weapon spec in code, starting from one of Fight Club's own specs so only the numbers
 * that differ need setting. Field names and limits are the same as in the spec files; see the
 * README's weapon spec table. {@link #build()} validates the result the same way a spec file is
 * validated and throws {@link com.google.gson.JsonParseException} with the field name if a value is out of range.
 *
 * <pre>{@code
 * WeaponSpec spec = FightClubApi.specBuilder("mythril", WeaponType.LONGSWORD)
 *         .copyOf("diamond")
 *         .attackDamage(11.5f)
 *         .attackSpeed(1.1f)
 *         .build();
 * }</pre>
 */
public final class WeaponSpecBuilder {
    private final String material;
    private final WeaponType type;
    private JsonObject json;

    WeaponSpecBuilder(String material, WeaponType type) {
        this.material = material;
        this.type = type;
        copyOf("iron");
    }

    /**
     * Starts again from another material's spec for the same weapon type, such as {@code "diamond"}
     * or {@code "netherite"}. New builders start from iron.
     */
    public WeaponSpecBuilder copyOf(String baseMaterial) {
        WeaponSpec base = FightClubApi.spec(baseMaterial, type);
        if (base == null) throw new IllegalArgumentException("No Fight Club spec for " + baseMaterial + "/" + type.id());
        json = base.toJson();
        json.addProperty("weapon", type.id());
        json.addProperty("material", material);
        return this;
    }

    public WeaponSpecBuilder attackDamage(float value) { return set("attack_damage", value); }
    public WeaponSpecBuilder attackSpeed(float value) { return set("attack_speed", value); }
    public WeaponSpecBuilder reach(float value) { return set("reach", value); }
    public WeaponSpecBuilder sweep(float radius, float angleDegrees) { return set("sweep_radius", radius).set("sweep_angle_degrees", angleDegrees); }
    public WeaponSpecBuilder thrust(float width, int penetrationCount) { return set("thrust_width", width).set("penetration_count", penetrationCount); }
    public WeaponSpecBuilder criticalHits(boolean value) { return set("critical_hits", value); }
    public WeaponSpecBuilder offhandSpeedMultiplier(float value) { return set("offhand_speed_multiplier", value); }
    public WeaponSpecBuilder charge(int ticks, float damage, float knockback) {
        return set("charge_ticks", ticks).set("charge_damage", damage).set("charge_knockback", knockback);
    }
    public WeaponSpecBuilder recoveryTicks(int value) { return set("recovery_ticks", value); }
    public WeaponSpecBuilder bashHitCooldownTicks(int value) { return set("bash_hit_cooldown_ticks", value); }
    public WeaponSpecBuilder throwing(boolean enabled, float velocity, float range) {
        return set("throwing", enabled).set("throw_velocity", velocity).set("throw_range", range);
    }
    public WeaponSpecBuilder throwChargeTicks(int value) { return set("throw_charge_ticks", value); }
    public WeaponSpecBuilder pounce(float range, float angleDegrees) { return set("pounce_range", range).set("pounce_angle_degrees", angleDegrees); }
    /** Longswords: the right-click spin's radius and item cooldown (its charge time, damage and knockback come from {@link #charge}). */
    public WeaponSpecBuilder spin(float radius, int cooldownTicks) { return set("spin_radius", radius).set("spin_cooldown_ticks", cooldownTicks); }

    /** Sets any spec field by its file name, for fields without a method above. */
    public WeaponSpecBuilder set(String field, Number value) {
        json.addProperty(field, value);
        return this;
    }

    public WeaponSpecBuilder set(String field, boolean value) {
        json.addProperty(field, value);
        return this;
    }

    public WeaponSpecBuilder set(String field, String value) {
        json.addProperty(field, value);
        return this;
    }

    public WeaponSpec build() {
        return WeaponSpec.fromJson(ResourceLocation.fromNamespaceAndPath("fight_club", "api/" + material + "/" + type.id()), json.deepCopy());
    }
}
