package com.craisinlord.fightclub.mixin;

import net.minecraft.commands.Commands;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.flag.FeatureFlagSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(ReloadableServerResources.class)
public abstract class ReloadableServerResourcesCaptureMixin {

    @Inject(method = "loadResources", at = @At("HEAD"))
    private static void fightClub$captureManager(ResourceManager resources, LayeredRegistryAccess<RegistryLayer> registries,
                                                 List<Registry.PendingTags<?>> tags, FeatureFlagSet features,
                                                 Commands.CommandSelection selection, PermissionSet permissions,
                                                 Executor background, Executor main,
                                                 CallbackInfoReturnable<CompletableFuture<ReloadableServerResources>> cir) {
        com.craisinlord.fightclub.combat.ExternalWeapons.reloadingManager = resources;
    }
}
