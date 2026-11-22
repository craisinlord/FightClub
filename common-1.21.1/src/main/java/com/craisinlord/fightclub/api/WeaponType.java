package com.craisinlord.fightclub.api;

public enum WeaponType {
    LONGSWORD("longsword"),
    SPEAR("spear"),
    DAGGER("dagger");

    private final String id;

    WeaponType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static WeaponType byId(String id) {
        for (WeaponType type : values()) if (type.id.equals(id)) return type;
        return null;
    }
}
