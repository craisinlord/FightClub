package com.craisinlord.fightclub.combat;

import com.craisinlord.fightclub.FightClub;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;

public final class CombatParticles {
    public static final ResourceLocation LONGSWORD_SWEEP_ID = ResourceLocation.fromNamespaceAndPath(FightClub.MOD_ID, "longsword_sweep");

    public static final SimpleParticleType LONGSWORD_SWEEP = new SimpleParticleType(false) {};

    private CombatParticles() {}
}
