package com.craisinlord.fightclub.neoforge;

import com.craisinlord.fightclub.FightClub;
import com.craisinlord.fightclub.combat.CombatPlatform;
import com.craisinlord.fightclub.combat.CombatWeaponItem;
import com.craisinlord.fightclub.compat.CompatMaterial;
import com.craisinlord.fightclub.combat.DaggerProjectile;
import com.craisinlord.fightclub.combat.SpearProjectile;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import com.craisinlord.fightclub.combat.WeaponSpecReloadListener;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import com.craisinlord.fightclub.combat.CombatActionPayload;
import com.craisinlord.fightclub.combat.CombatChargeState;

@Mod(FightClub.MOD_ID)
public final class FightClubNeoForge {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, FightClub.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, FightClub.MOD_ID);
    public static final net.neoforged.neoforge.registries.DeferredHolder<EntityType<?>, EntityType<DaggerProjectile>> DAGGER_PROJECTILE = ENTITY_TYPES.register("dagger_projectile", () -> EntityType.Builder.<DaggerProjectile>of(DaggerProjectile::new, MobCategory.MISC).sized(0.25F, 0.25F).build("dagger_projectile"));
    public static final net.neoforged.neoforge.registries.DeferredHolder<EntityType<?>, EntityType<SpearProjectile>> SPEAR_PROJECTILE = ENTITY_TYPES.register("spear_projectile", () -> EntityType.Builder.<SpearProjectile>of(SpearProjectile::new, MobCategory.MISC).sized(0.25F, 0.25F).build("spear_projectile"));
    private static final String[] MATERIALS = {"wood", "stone", "copper", "iron", "gold", "diamond", "netherite"};
    private static final String[] WEAPONS = {"longsword", "spear", "dagger"};

    public FightClubNeoForge(IEventBus modBus) {
        com.craisinlord.fightclub.CombatConfig.load(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get());
        for (String material : MATERIALS) for (String weapon : WEAPONS) {
            if (!com.craisinlord.fightclub.combat.CombatWeapons.registersOwn(material, weapon)) continue;
            ITEMS.register(material + "_" + weapon, () -> new CombatWeaponItem(material, weapon, tier(material)));
        }
        java.util.List<net.neoforged.neoforge.registries.DeferredHolder<Item, Item>> compatItems = new java.util.ArrayList<>();
        for (CompatMaterial compat : CompatMaterial.values()) {
            if (!compat.isActive(CombatPlatform::isModLoaded)) continue;
            for (String weapon : CompatMaterial.WEAPONS) {
                compatItems.add(ITEMS.register(compat.itemPath(weapon), () -> new CombatWeaponItem(compat.material, weapon, compat, com.craisinlord.fightclub.compat.CompatAbilities.properties(compat))));
            }
            FightClub.LOGGER.info("Fight Club: enabled {} weapons", compat.material);
        }
        ITEMS.register(modBus);
        ENTITY_TYPES.register(modBus);
        DeferredRegister<net.minecraft.sounds.SoundEvent> sounds = DeferredRegister.create(Registries.SOUND_EVENT, FightClub.MOD_ID);
        com.craisinlord.fightclub.combat.CombatSounds.ALL.forEach((path, sound) -> sounds.register(path, () -> sound));
        sounds.register(modBus);
        DeferredRegister<net.minecraft.core.particles.ParticleType<?>> particleTypes = DeferredRegister.create(Registries.PARTICLE_TYPE, FightClub.MOD_ID);
        particleTypes.register(com.craisinlord.fightclub.combat.CombatParticles.LONGSWORD_SWEEP_ID.getPath(),
                () -> com.craisinlord.fightclub.combat.CombatParticles.LONGSWORD_SWEEP);
        particleTypes.register(modBus);
        modBus.addListener(FightClubNeoForge::registerPayloadHandlers);
        modBus.addListener((net.neoforged.neoforge.event.ModifyDefaultComponentsEvent event) -> {
            int size = com.craisinlord.fightclub.combat.CombatDefaults.potionStackSize();
            if (size > 1) com.craisinlord.fightclub.combat.CombatDefaults.POTIONS.forEach(potion -> event.modify(potion, builder ->
                    builder.set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE, size)));
            if (com.craisinlord.fightclub.combat.CombatWeapons.backportSpears()) {
                for (String material : com.craisinlord.fightclub.combat.CombatWeapons.BACKPORT_SPEAR_MATERIALS) {
                    Item spear = com.craisinlord.fightclub.combat.CombatWeapons.backportSpear(material);
                    com.craisinlord.fightclub.combat.CombatWeapons.captureMobComponents(spear);
                    event.modify(spear, builder -> {
                        builder.set(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS, net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY);
                        com.craisinlord.fightclub.combat.CombatWeapons.backportComponentTypes().forEach(builder::remove);
                    });
                }
            }
        });
        modBus.addListener((net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey().equals(CreativeModeTabs.COMBAT)) tabItems(compatItems).forEach(event::accept);
        });
        DeferredRegister<net.minecraft.world.item.CreativeModeTab> tabs = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, FightClub.MOD_ID);
        tabs.register("combat", () -> net.minecraft.world.item.CreativeModeTab.builder()
                .title(net.minecraft.network.chat.Component.translatable("itemGroup.fight_club.combat"))
                .icon(() -> new net.minecraft.world.item.ItemStack(BuiltInRegistries.ITEM.get(
                        ResourceLocation.fromNamespaceAndPath(FightClub.MOD_ID, "diamond_dagger"))))
                .displayItems((parameters, output) -> tabItems(compatItems).forEach(output::accept))
                .build());
        tabs.register(modBus);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.AddReloadListenerEvent event) ->
                event.addListener(new WeaponSpecReloadListener()));
        NeoForge.EVENT_BUS.register(new FightClubNeoForgeEvents());
        FightClub.LOGGER.info("Fight Club 1.21.1 NeoForge initialized");
    }

    private static java.util.List<Item> tabItems(java.util.List<net.neoforged.neoforge.registries.DeferredHolder<Item, Item>> compatItems) {
        java.util.List<Item> items = new java.util.ArrayList<>();
        for (String weapon : WEAPONS) {
            for (String material : MATERIALS) {
                if (!com.craisinlord.fightclub.combat.CombatWeapons.registersOwn(material, weapon)) continue;
                items.add(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(FightClub.MOD_ID, material + "_" + weapon)));
            }
            for (var holder : compatItems) {
                if (holder.get() instanceof CombatWeaponItem item && item.combatWeapon().equals(weapon)) items.add(item);
            }
        }
        return items;
    }

    private static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("2").playToServer(CombatActionPayload.TYPE, CombatActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                        CombatChargeState.handle(player, payload.action(), payload.target());
                    }
                }));
        event.registrar("1").playToClient(com.craisinlord.fightclub.combat.CombatPosePayload.TYPE,
                com.craisinlord.fightclub.combat.CombatPosePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> NeoForgeClientPackets.handlePose(payload)));
        event.registrar("1").playToClient(com.craisinlord.fightclub.combat.WeaponSpecSyncPayload.TYPE,
                com.craisinlord.fightclub.combat.WeaponSpecSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(payload::apply));
    }

    private static Tier tier(String material) {
        return switch (material) {
            case "wood" -> Tiers.WOOD;
            case "stone" -> Tiers.STONE;
            case "gold" -> Tiers.GOLD;
            case "diamond" -> Tiers.DIAMOND;
            case "netherite" -> Tiers.NETHERITE;
            default -> Tiers.IRON;
        };
    }
}
