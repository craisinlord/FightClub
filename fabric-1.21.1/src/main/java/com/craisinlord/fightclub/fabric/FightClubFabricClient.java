package com.craisinlord.fightclub.fabric;

import com.craisinlord.fightclub.combat.DaggerProjectile;
import com.craisinlord.fightclub.combat.SpearProjectile;
import com.craisinlord.fightclub.client.CombatClientPoses;
import com.craisinlord.fightclub.client.CombatInputClient;
import com.craisinlord.fightclub.client.CombatPoseConfig;
import com.craisinlord.fightclub.combat.CombatPosePayload;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.server.packs.PackType;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;

public final class FightClubFabricClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        EntityRendererRegistry.register(FightClubFabric.DAGGER_PROJECTILE, com.craisinlord.fightclub.client.WeaponProjectileRenderer::new);
        EntityRendererRegistry.register(FightClubFabric.SPEAR_PROJECTILE, com.craisinlord.fightclub.client.WeaponProjectileRenderer::new);
        net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry.getInstance().register(
                com.craisinlord.fightclub.combat.CombatParticles.LONGSWORD_SWEEP,
                com.craisinlord.fightclub.client.LongswordSweepParticle.Provider::new);
        CombatInputClient.setSender(payload -> {
            if (ClientPlayNetworking.canSend(payload.type())) ClientPlayNetworking.send(payload);
        });
        ClientTickEvents.END_CLIENT_TICK.register(CombatInputClient::tick);
        com.craisinlord.fightclub.combat.CombatTooltips.setShiftSupplier(net.minecraft.client.gui.screens.Screen::hasShiftDown);
        ClientPlayNetworking.registerGlobalReceiver(CombatPosePayload.TYPE, (payload, context) ->
                CombatClientPoses.handlePacket(payload));
        ClientPlayNetworking.registerGlobalReceiver(com.craisinlord.fightclub.combat.WeaponSpecSyncPayload.TYPE, (payload, context) ->
                payload.apply());
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                com.craisinlord.fightclub.CombatConfig.reload());
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new PoseReloadListener());
        net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin.register(context ->
                context.addModels(com.craisinlord.fightclub.client.CombatModels.inHandModelIds()));
        com.craisinlord.fightclub.client.CombatModels.setLookup(id -> Minecraft.getInstance().getModelManager().getModel(id));
    }

    private static final class PoseReloadListener extends CombatPoseConfig
            implements net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener {
        @Override public ResourceLocation getFabricId() { return CombatPoseConfig.ID; }
    }

}
