package com.craisinlord.fightclub.client;

/**
 * Every tunable first-person animation. Held poses blend in and out; one-shot effects (jabs,
 * swings, recoil, bash) use translation/rotation as their peak offset and blend_in_ticks as
 * their duration.
 */
public enum CombatPose {
    NONE("none"),
    // Held poses
    /** Spear lowered for a charge, tip toward the crosshair. */
    SPEAR_LOWERED("spear_lowered"),
    /** Trident lowered for a charge. Separate from the spear: the trident is a 3D model, the spear a flat sprite. */
    TRIDENT_LOWERED("trident_lowered"),
    /** Dagger drawn back for a pounce; draws further as the charge fills. */
    DAGGER_CHARGE("dagger_charge"),
    /** Dagger thrust out ahead while dashing at a pounce target. */
    DAGGER_POUNCE("dagger_pounce"),
    /** Sword raised across the body while right-click blocking. */
    SWORD_BLOCK("sword_block"),
    /** Dagger cocked over the shoulder while aiming a throw. */
    DAGGER_THROW("dagger_throw"),
    // Modifiers and one-shot effects
    /** Weapon lowered during recovery; fades as the cooldown runs out. */
    RECOVERY_DIP("recovery_dip"),
    /** Kick when a sword block absorbs a hit. */
    BLOCK_RECOIL("block_recoil"),
    /** Shield shoved forward for a bash. */
    SHIELD_BASH("shield_bash"),
    /** Forward stab replacing the swing (without Better Combat). */
    JAB_DAGGER("jab_dagger"),
    JAB_SPEAR("jab_spear"),
    /**
     * Longsword swing (without Better Combat): one big, slow horizontal cut across the body, right
     * to left (wind-up, fast cut, follow-through, recovery), keyframed in first and third person by
     * the "keys" in poses.json.
     */
    HEAVY_LONGSWORD("heavy_longsword"),
    /**
     * The return cut, left to right: consecutive longsword swings alternate between heavy_longsword and
     * this (CombatClientPoses.backhand). Its duration is heavy_longsword's.
     */
    HEAVY_LONGSWORD_BACK("heavy_longsword_back"),
    /** Held while charging a longsword spin (right-click): blade drawn back to the side. */
    LONGSWORD_SPIN_CHARGE("longsword_spin_charge"),
    /** One-shot longsword spin, keyframed like heavy_longsword; in third person the whole body also turns once. */
    LONGSWORD_SPIN("longsword_spin"),
    /** Third person only: two-handed longsword grip when the offhand is empty (without Better Combat). */
    LONGSWORD_GRIP("longsword_grip");

    private final String jsonKey;

    CombatPose(String jsonKey) { this.jsonKey = jsonKey; }

    public String jsonKey() { return jsonKey; }
}
