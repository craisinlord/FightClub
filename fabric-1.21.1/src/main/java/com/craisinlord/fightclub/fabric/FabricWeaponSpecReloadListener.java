package com.craisinlord.fightclub.fabric;

import com.craisinlord.fightclub.combat.WeaponSpecReloadListener;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.ResourceLocation;

public final class FabricWeaponSpecReloadListener extends WeaponSpecReloadListener implements IdentifiableResourceReloadListener {
    @Override
    public ResourceLocation getFabricId() {
        return ResourceLocation.fromNamespaceAndPath("fight_club", "weapon_specs");
    }
}
