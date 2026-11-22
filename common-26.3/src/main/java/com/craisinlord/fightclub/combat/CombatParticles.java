package com.craisinlord.fightclub.combat;

import com.craisinlord.fightclub.FightClub;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.Identifier;

public final class CombatParticles {
    public static final Identifier LONGSWORD_SWEEP_ID = Identifier.fromNamespaceAndPath(FightClub.MOD_ID, "longsword_sweep");

    public static final SimpleParticleType LONGSWORD_SWEEP = new SimpleParticleType(false) {};

    private CombatParticles() {}
}
