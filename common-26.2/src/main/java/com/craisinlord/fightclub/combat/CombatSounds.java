package com.craisinlord.fightclub.combat;

import com.craisinlord.fightclub.FightClub;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fight Club's sound events. Each one is defined in assets/fight_club/sounds.json as a
 * pitch/volume variation of existing vanilla audio, so resource packs can retune or replace them.
 * The loaders register every entry of {@link #ALL} in the sound event registry.
 */
public final class CombatSounds {
    public static final Map<String, SoundEvent> ALL = new LinkedHashMap<>();

    public static final SoundEvent DAGGER_SWING = create("weapon.dagger.swing");
    public static final SoundEvent SPEAR_THRUST = create("weapon.spear.thrust");
    public static final SoundEvent LONGSWORD_SWING = create("weapon.longsword.swing");
    public static final SoundEvent LONGSWORD_SPIN = create("weapon.longsword.spin");
    public static final SoundEvent CHARGE_START = create("weapon.charge.start");
    public static final SoundEvent CHARGE_READY = create("weapon.charge.ready");
    /** A dagger charge just filled: a short high metallic ting (played per client, see CombatClientPoses). */
    public static final SoundEvent DAGGER_READY = create("weapon.dagger.ready");
    public static final SoundEvent SWORD_BLOCK = create("weapon.sword.block");
    public static final SoundEvent SHIELD_BASH = create("weapon.shield.bash");
    public static final SoundEvent CHARGE_HIT = create("weapon.charge.hit");
    public static final SoundEvent DAGGER_CHARGED_STAB = create("weapon.dagger.charged_stab");
    public static final SoundEvent DAGGER_THROW = create("weapon.dagger.throw");
    public static final SoundEvent SPEAR_THROW = create("weapon.spear.throw");
    public static final SoundEvent PROJECTILE_HIT = create("weapon.projectile.hit");
    public static final SoundEvent PROJECTILE_STICK = create("weapon.projectile.stick");

    private CombatSounds() {}

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(FightClub.MOD_ID, path);
    }

    private static SoundEvent create(String path) {
        SoundEvent event = SoundEvent.createVariableRangeEvent(id(path));
        ALL.put(path, event);
        return event;
    }
}
