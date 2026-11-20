package com.craisinlord.fightclub.fabric;

import com.craisinlord.fightclub.client.CombatClientPoses;
import com.craisinlord.fightclub.client.CombatInputClient;
import com.craisinlord.fightclub.client.CombatPoseConfig;
import com.craisinlord.fightclub.client.WeaponProjectileRenderer;
import com.craisinlord.fightclub.combat.CombatPosePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.server.packs.PackType;

public final class FightClubFabricClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        EntityRendererRegistry.register(FightClubFabric.DAGGER_PROJECTILE, WeaponProjectileRenderer::new);
        EntityRendererRegistry.register(FightClubFabric.SPEAR_PROJECTILE, WeaponProjectileRenderer::new);
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(CombatPoseConfig.ID, new CombatPoseConfig());
        CombatInputClient.setSender(payload -> {
            if (ClientPlayNetworking.canSend(payload.type())) ClientPlayNetworking.send(payload);
        });
        ClientPlayNetworking.registerGlobalReceiver(CombatPosePayload.TYPE,
                (payload, context) -> context.client().execute(() -> CombatClientPoses.handlePacket(payload)));
        ClientTickEvents.END_CLIENT_TICK.register(CombatInputClient::tick);
        com.craisinlord.fightclub.combat.CombatTooltips.setShiftSupplier(() -> net.minecraft.client.Minecraft.getInstance().hasShiftDown());
    }
}
