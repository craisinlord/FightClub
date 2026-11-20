package com.craisinlord.fightclub.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets the spear/trident tap fire its held-back attack on release, through vanilla (and Better Combat's hook). */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Invoker("startAttack")
    boolean fightClub$startAttack();
}
