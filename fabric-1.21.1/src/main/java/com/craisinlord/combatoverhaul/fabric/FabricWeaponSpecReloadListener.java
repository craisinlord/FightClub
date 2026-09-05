package com.craisinlord.combatoverhaul.fabric;

import com.craisinlord.combatoverhaul.combat.WeaponSpecReloadListener;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.ResourceLocation;

public final class FabricWeaponSpecReloadListener extends WeaponSpecReloadListener implements IdentifiableResourceReloadListener {
    @Override
    public ResourceLocation getFabricId() {
        return ResourceLocation.fromNamespaceAndPath("combat_overhaul", "weapon_specs");
    }
}
