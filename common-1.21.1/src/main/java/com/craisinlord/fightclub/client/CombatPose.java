package com.craisinlord.fightclub.client;

public enum CombatPose {
    NONE("none"),
    SPEAR_LOWERED("spear_lowered"),
    TRIDENT_LOWERED("trident_lowered"),
    DAGGER_CHARGE("dagger_charge"),
    DAGGER_POUNCE("dagger_pounce"),
    SWORD_BLOCK("sword_block"),
    DAGGER_THROW("dagger_throw"),
    DAGGER_THROW_FLIP("dagger_throw_flip"),
    RECOVERY_DIP("recovery_dip"),
    BLOCK_RECOIL("block_recoil"),
    SHIELD_BASH("shield_bash"),
    JAB_DAGGER("jab_dagger"),
    JAB_SPEAR("jab_spear"),
    JAB_TRIDENT("jab_trident"),
    HEAVY_LONGSWORD("heavy_longsword"),
    HEAVY_LONGSWORD_BACK("heavy_longsword_back"),
    HEAVY_MACE("heavy_mace"),
    LONGSWORD_SPIN_CHARGE("longsword_spin_charge"),
    LONGSWORD_SPIN("longsword_spin"),
    LONGSWORD_GRIP("longsword_grip");

    private final String jsonKey;

    CombatPose(String jsonKey) { this.jsonKey = jsonKey; }

    public String jsonKey() { return jsonKey; }
}
