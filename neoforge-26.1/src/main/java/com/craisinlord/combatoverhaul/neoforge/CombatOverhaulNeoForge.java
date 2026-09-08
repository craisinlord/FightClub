package com.craisinlord.combatoverhaul.neoforge;

import com.craisinlord.combatoverhaul.CombatOverhaul;
import com.craisinlord.combatoverhaul.combat.CombatWeaponItem;
import com.craisinlord.combatoverhaul.combat.DaggerProjectile;
import com.craisinlord.combatoverhaul.combat.SpearProjectile;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.resources.ResourceKey;
import com.craisinlord.combatoverhaul.combat.WeaponSpecReloadListener;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(CombatOverhaul.MOD_ID)
public final class CombatOverhaulNeoForge {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, CombatOverhaul.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, CombatOverhaul.MOD_ID);
    public static final net.neoforged.neoforge.registries.DeferredHolder<EntityType<?>, EntityType<DaggerProjectile>> DAGGER_PROJECTILE = ENTITY_TYPES.register("dagger_projectile", () -> EntityType.Builder.<DaggerProjectile>of(DaggerProjectile::new, MobCategory.MISC).sized(0.25F, 0.25F).clientTrackingRange(4).build(ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(CombatOverhaul.MOD_ID, "dagger_projectile"))));
    public static final net.neoforged.neoforge.registries.DeferredHolder<EntityType<?>, EntityType<SpearProjectile>> SPEAR_PROJECTILE = ENTITY_TYPES.register("spear_projectile", () -> EntityType.Builder.<SpearProjectile>of(SpearProjectile::new, MobCategory.MISC).sized(0.25F, 0.25F).clientTrackingRange(4).build(ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(CombatOverhaul.MOD_ID, "spear_projectile"))));
    private static final String[] MATERIALS = {"wood", "stone", "copper", "iron", "gold", "diamond", "netherite"};
    private static final String[] WEAPONS = {"longsword", "dagger"};

    public CombatOverhaulNeoForge(IEventBus modBus) {
        for (String material : MATERIALS) for (String weapon : WEAPONS) {
            ITEMS.register(material + "_" + weapon, () -> new CombatWeaponItem(material, weapon, tier(material)));
        }
        ITEMS.register(modBus);
        ENTITY_TYPES.register(modBus);
        modBus.addListener(CombatOverhaulNeoForgeClient::registerRenderers);
        modBus.addListener((net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) -> {
            if (!event.getTabKey().equals(CreativeModeTabs.COMBAT)) return;
            for (String material : MATERIALS) for (String weapon : WEAPONS) {
                BuiltInRegistries.ITEM.get(Identifier.fromNamespaceAndPath(CombatOverhaul.MOD_ID, material + "_" + weapon))
                        .map(reference -> reference.value()).ifPresent(event::accept);
            }
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.AddServerReloadListenersEvent event) ->
                event.addListener(net.minecraft.resources.Identifier.fromNamespaceAndPath(CombatOverhaul.MOD_ID, "weapon_specs"),
                        new WeaponSpecReloadListener()));
        NeoForge.EVENT_BUS.register(new CombatOverhaulNeoForgeEvents());
        CombatOverhaul.LOGGER.info("Combat Overhaul 26.1 NeoForge initialized");
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
