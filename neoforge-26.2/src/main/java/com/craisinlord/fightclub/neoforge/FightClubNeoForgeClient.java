package com.craisinlord.fightclub.neoforge;

import com.craisinlord.fightclub.FightClub;
import com.craisinlord.fightclub.client.CombatClientPoses;
import com.craisinlord.fightclub.client.CombatInputClient;
import com.craisinlord.fightclub.client.CombatPoseConfig;
import com.craisinlord.fightclub.client.WeaponProjectileRenderer;
import com.craisinlord.fightclub.combat.CombatPosePayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client-only setup; NeoForge only constructs this on the physical client. */
@Mod(value = FightClub.MOD_ID, dist = Dist.CLIENT)
public final class FightClubNeoForgeClient {
    public FightClubNeoForgeClient(IEventBus modBus) {
        modBus.addListener(FightClubNeoForgeClient::registerRenderers);
        modBus.addListener((RegisterClientPayloadHandlersEvent event) -> event.register(CombatPosePayload.TYPE,
                (payload, context) -> context.enqueueWork(() -> CombatClientPoses.handlePacket(payload))));
        modBus.addListener((AddClientReloadListenersEvent event) -> event.addListener(CombatPoseConfig.ID, new CombatPoseConfig()));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> CombatInputClient.tick(Minecraft.getInstance()));
        com.craisinlord.fightclub.combat.CombatTooltips.setShiftSupplier(() -> Minecraft.getInstance().hasShiftDown());
        CombatInputClient.setSender(payload -> {
            if (Minecraft.getInstance().getConnection() != null) ClientPacketDistributor.sendToServer(payload);
        });
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(FightClubNeoForge.DAGGER_PROJECTILE.get(), WeaponProjectileRenderer::new);
        event.registerEntityRenderer(FightClubNeoForge.SPEAR_PROJECTILE.get(), WeaponProjectileRenderer::new);
    }
}
