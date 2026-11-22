package com.craisinlord.fightclub.combat;

import com.craisinlord.fightclub.FightClub;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CombatSounds {
    public static final Map<String, SoundEvent> ALL = new LinkedHashMap<>();

    public static final SoundEvent DAGGER_SWING = create("weapon.dagger.swing");
    public static final SoundEvent SPEAR_THRUST = create("weapon.spear.thrust");
    public static final SoundEvent LONGSWORD_SWING = create("weapon.longsword.swing");
    public static final SoundEvent SWORD_SWING = create("weapon.sword.swing");
    public static final SoundEvent AXE_SWING = create("weapon.axe.swing");
    public static final SoundEvent MACE_SWING = create("weapon.mace.swing");
    public static final SoundEvent LONGSWORD_SPIN = create("weapon.longsword.spin");
    public static final SoundEvent CHARGE_START = create("weapon.charge.start");
    public static final SoundEvent CHARGE_READY = create("weapon.charge.ready");
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
