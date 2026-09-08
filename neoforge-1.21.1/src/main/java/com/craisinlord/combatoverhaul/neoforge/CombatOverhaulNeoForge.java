package com.craisinlord.combatoverhaul.neoforge;

import com.craisinlord.combatoverhaul.CombatOverhaul;
import com.craisinlord.combatoverhaul.combat.CombatWeaponItem;
import com.craisinlord.combatoverhaul.combat.DaggerProjectile;
import com.craisinlord.combatoverhaul.combat.SpearProjectile;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import com.craisinlord.combatoverhaul.combat.WeaponSpecReloadListener;
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

@Mod(CombatOverhaul.MOD_ID)
public final class CombatOverhaulNeoForge {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, CombatOverhaul.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, CombatOverhaul.MOD_ID);
    public static final net.neoforged.neoforge.registries.DeferredHolder<EntityType<?>, EntityType<DaggerProjectile>> DAGGER_PROJECTILE = ENTITY_TYPES.register("dagger_projectile", () -> EntityType.Builder.<DaggerProjectile>of(DaggerProjectile::new, MobCategory.MISC).sized(0.25F, 0.25F).build("dagger_projectile"));
    public static final net.neoforged.neoforge.registries.DeferredHolder<EntityType<?>, EntityType<SpearProjectile>> SPEAR_PROJECTILE = ENTITY_TYPES.register("spear_projectile", () -> EntityType.Builder.<SpearProjectile>of(SpearProjectile::new, MobCategory.MISC).sized(0.25F, 0.25F).build("spear_projectile"));
    private static final String[] MATERIALS = {"wood", "stone", "copper", "iron", "gold", "diamond", "netherite"};
    private static final String[] WEAPONS = {"longsword", "spear", "dagger"};
    // The material spear loop below already registers the existing spear IDs.
    // Only these vanilla-style aliases are new registrations.
    private static final String[] VANILLA_SPEARS = {"wooden_spear", "golden_spear"};

    public CombatOverhaulNeoForge(IEventBus modBus) {
        for (String material : MATERIALS) for (String weapon : WEAPONS) {
            ITEMS.register(material + "_" + weapon, () -> new CombatWeaponItem(material, weapon, tier(material)));
        }
        for (String spear : VANILLA_SPEARS) {
            ITEMS.register(spear, () -> new CombatWeaponItem(spearMaterial(spear), "spear", tier(spearMaterial(spear))));
        }
        ITEMS.register(modBus);
        ENTITY_TYPES.register(modBus);
        modBus.addListener(CombatOverhaulNeoForgeClient::registerRenderers);
        modBus.addListener((net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) -> {
            if (!event.getTabKey().equals(CreativeModeTabs.COMBAT)) return;
            for (String material : MATERIALS) for (String weapon : WEAPONS) {
                event.accept(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(CombatOverhaul.MOD_ID, material + "_" + weapon)));
            }
            for (String spear : VANILLA_SPEARS) {
                event.accept(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(CombatOverhaul.MOD_ID, spear)));
            }
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.AddReloadListenerEvent event) ->
                event.addListener(new WeaponSpecReloadListener()));
        NeoForge.EVENT_BUS.register(new CombatOverhaulNeoForgeEvents());
        CombatOverhaul.LOGGER.info("Combat Overhaul 1.21.1 NeoForge initialized");
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

    private static String spearMaterial(String id) {
        return switch (id) {
            case "wooden_spear" -> "wood";
            case "golden_spear" -> "gold";
            default -> id.substring(0, id.length() - "_spear".length());
        };
    }
}
