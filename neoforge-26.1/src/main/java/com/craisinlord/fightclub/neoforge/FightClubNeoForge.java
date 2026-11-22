package com.craisinlord.fightclub.neoforge;

import com.craisinlord.fightclub.FightClub;
import com.craisinlord.fightclub.combat.CombatWeaponItem;
import com.craisinlord.fightclub.combat.DaggerProjectile;
import com.craisinlord.fightclub.combat.SpearProjectile;
import com.craisinlord.fightclub.combat.CombatActionPayload;
import com.craisinlord.fightclub.combat.CombatChargeState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import com.craisinlord.fightclub.combat.WeaponSpecReloadListener;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(FightClub.MOD_ID)
public final class FightClubNeoForge {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, FightClub.MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, FightClub.MOD_ID);
    public static final net.neoforged.neoforge.registries.DeferredHolder<EntityType<?>, EntityType<DaggerProjectile>> DAGGER_PROJECTILE = ENTITY_TYPES.register("dagger_projectile", () -> EntityType.Builder.<DaggerProjectile>of(DaggerProjectile::new, MobCategory.MISC).sized(0.25F, 0.25F).clientTrackingRange(4).build(ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(FightClub.MOD_ID, "dagger_projectile"))));
    public static final net.neoforged.neoforge.registries.DeferredHolder<EntityType<?>, EntityType<SpearProjectile>> SPEAR_PROJECTILE = ENTITY_TYPES.register("spear_projectile", () -> EntityType.Builder.<SpearProjectile>of(SpearProjectile::new, MobCategory.MISC).sized(0.25F, 0.25F).clientTrackingRange(4).build(ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(FightClub.MOD_ID, "spear_projectile"))));
    private static final String[] MATERIALS = {"wood", "stone", "copper", "iron", "gold", "diamond", "netherite"};
    private static final String[] WEAPONS = {"longsword", "dagger"};

    private static final DeferredRegister<net.minecraft.sounds.SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, FightClub.MOD_ID);
    private static final DeferredRegister<net.minecraft.core.particles.ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(Registries.PARTICLE_TYPE, FightClub.MOD_ID);

    public FightClubNeoForge(IEventBus modBus) {
        com.craisinlord.fightclub.CombatConfig.load(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get());
        com.craisinlord.fightclub.combat.CombatSounds.ALL.forEach((path, sound) -> SOUNDS.register(path, () -> sound));
        SOUNDS.register(modBus);
        PARTICLE_TYPES.register(com.craisinlord.fightclub.combat.CombatParticles.LONGSWORD_SWEEP_ID.getPath(),
                () -> com.craisinlord.fightclub.combat.CombatParticles.LONGSWORD_SWEEP);
        PARTICLE_TYPES.register(modBus);
        for (String material : MATERIALS) for (String weapon : WEAPONS) {
            Identifier id = Identifier.fromNamespaceAndPath(FightClub.MOD_ID, material + "_" + weapon);
            ITEMS.register(material + "_" + weapon, () -> new CombatWeaponItem(material, weapon, tier(material), ResourceKey.create(Registries.ITEM, id)));
        }
        ITEMS.register(modBus);
        ENTITY_TYPES.register(modBus);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) -> {
            CombatChargeState.clear(event.getEntity());
            com.craisinlord.fightclub.combat.CombatRecovery.clear(event.getEntity());
        });
        modBus.addListener(FightClubNeoForge::registerPayloadHandlers);
        modBus.addListener((net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) -> {
            if (!event.getTabKey().equals(CreativeModeTabs.COMBAT)) return;
            for (String material : MATERIALS) for (String weapon : WEAPONS) {
                ITEMS.getEntries().stream()
                        .filter(entry -> entry.getId().getPath().equals(material + "_" + weapon))
                        .findFirst().filter(entry -> !com.craisinlord.fightclub.combat.ExternalWeapons.isReplaced(entry.get())).ifPresent(entry -> event.accept(entry.get()));
            }
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.AddServerReloadListenersEvent event) ->
                event.addListener(net.minecraft.resources.Identifier.fromNamespaceAndPath(FightClub.MOD_ID, "weapon_specs"),
                        new WeaponSpecReloadListener()));
        NeoForge.EVENT_BUS.register(new FightClubNeoForgeEvents());
        FightClub.LOGGER.info("Fight Club 26.1 NeoForge initialized");
    }

    private static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("2").playToServer(CombatActionPayload.TYPE, CombatActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                        CombatChargeState.handle(player, payload.action(), payload.target());
                    }
                }))
                .playToClient(com.craisinlord.fightclub.combat.CombatPosePayload.TYPE,
                        com.craisinlord.fightclub.combat.CombatPosePayload.STREAM_CODEC)
                .playToClient(com.craisinlord.fightclub.combat.WeaponItemsPayload.TYPE,
                        com.craisinlord.fightclub.combat.WeaponItemsPayload.STREAM_CODEC);
    }

    private static ToolMaterial tier(String material) {
        return switch (material) {
            case "wood" -> ToolMaterial.WOOD;
            case "stone" -> ToolMaterial.STONE;
            case "copper" -> ToolMaterial.COPPER;
            case "gold" -> ToolMaterial.GOLD;
            case "diamond" -> ToolMaterial.DIAMOND;
            case "netherite" -> ToolMaterial.NETHERITE;
            default -> ToolMaterial.IRON;
        };
    }
}
