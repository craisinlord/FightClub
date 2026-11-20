package com.craisinlord.fightclub.api;

/**
 * The Fight Club weapon families another mod can make items for. Each one brings its whole
 * move set (animations, charge, throw, sweep or pierce, recovery, tooltips); the numbers come
 * from the weapon spec for the item's material.
 */
public enum WeaponType {
    /** Wide sweeping two-hander. No crits, slower with an offhand item, short recovery after a hit. */
    LONGSWORD("longsword"),
    /** Thrust that pierces lined-up mobs, hold attack to lower it for a charge, right-click to throw. */
    SPEAR("spear"),
    /** Fast single-target stabs, hold attack to pounce on a mob, right-click to throw. */
    DAGGER("dagger");

    private final String id;

    WeaponType(String id) {
        this.id = id;
    }

    /** The name used in spec files and item ids: {@code longsword}, {@code spear} or {@code dagger}. */
    public String id() {
        return id;
    }

    /** The type with this id, or null. */
    public static WeaponType byId(String id) {
        for (WeaponType type : values()) if (type.id.equals(id)) return type;
        return null;
    }
}
