package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.client.CombatInputClient;
import com.craisinlord.fightclub.client.DevPoseGallery;
import com.craisinlord.fightclub.client.DevShowcase;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CombatInputClient.class, remap = false)
public abstract class DevToolsTickMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private static void fightClub$devTools(Minecraft client, CallbackInfo ci) {
        DevPoseGallery.tick(client);
        DevShowcase.tick(client);
        com.craisinlord.fightclub.client.DevSilentGearTest.tick(client);
        com.craisinlord.fightclub.client.DevCompatTest.tick(client);
        com.craisinlord.fightclub.client.DevBackportTest.tick(client);
        com.craisinlord.fightclub.client.DevLongswordReachTest.tick(client);
        com.craisinlord.fightclub.client.DevLongswordFpShot.tick(client);
        com.craisinlord.fightclub.client.DevSmokeTest.tick(client);
    }
}
