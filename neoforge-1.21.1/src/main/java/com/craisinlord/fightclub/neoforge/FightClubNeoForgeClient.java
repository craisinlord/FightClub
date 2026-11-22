package com.craisinlord.fightclub.neoforge;

import com.craisinlord.fightclub.FightClub;
import com.craisinlord.fightclub.client.CombatInputClient;
import com.craisinlord.fightclub.client.CombatPoseConfig;
import com.craisinlord.fightclub.combat.DaggerProjectile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

@Mod(value = FightClub.MOD_ID, dist = Dist.CLIENT)
public final class FightClubNeoForgeClient {
    public FightClubNeoForgeClient(IEventBus modBus) {
        modBus.addListener(FightClubNeoForgeClient::registerRenderers);
        modBus.addListener(FightClubNeoForgeClient::registerReloadListeners);
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent event) -> event.registerSpriteSet(
                com.craisinlord.fightclub.combat.CombatParticles.LONGSWORD_SWEEP, com.craisinlord.fightclub.client.LongswordSweepParticle.Provider::new));
        NeoForge.EVENT_BUS.addListener(FightClubNeoForgeClient::onClientTick);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) ->
                com.craisinlord.fightclub.CombatConfig.reload());
        modBus.addListener((net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional event) ->
                com.craisinlord.fightclub.client.CombatModels.inHandModelIds().forEach(id ->
                        event.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(id))));
        com.craisinlord.fightclub.client.CombatModels.setLookup(id -> Minecraft.getInstance().getModelManager()
                .getModel(net.minecraft.client.resources.model.ModelResourceLocation.standalone(id)));
        CombatInputClient.setSender(PacketDistributor::sendToServer);
        com.craisinlord.fightclub.combat.CombatTooltips.setShiftSupplier(net.minecraft.client.gui.screens.Screen::hasShiftDown);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        CombatInputClient.tick(Minecraft.getInstance());
    }

    private static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new CombatPoseConfig());
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(FightClubNeoForge.DAGGER_PROJECTILE.get(), com.craisinlord.fightclub.client.WeaponProjectileRenderer::new);
        event.registerEntityRenderer(FightClubNeoForge.SPEAR_PROJECTILE.get(), com.craisinlord.fightclub.client.WeaponProjectileRenderer::new);
    }
}
