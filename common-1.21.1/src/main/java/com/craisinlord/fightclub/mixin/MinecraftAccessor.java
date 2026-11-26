package com.craisinlord.fightclub.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Invoker("startAttack")
    boolean fightClub$startAttack();

    @Invoker("startUseItem")
    void fightClub$startUseItem();
}
