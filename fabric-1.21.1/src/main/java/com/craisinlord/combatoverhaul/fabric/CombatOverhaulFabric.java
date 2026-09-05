package com.craisinlord.combatoverhaul.fabric;

import com.craisinlord.combatoverhaul.CombatOverhaul;
import com.craisinlord.combatoverhaul.combat.CombatWeaponItem;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;

public final class CombatOverhaulFabric implements ModInitializer {
    private static final String[] MATERIALS = {"wood", "stone", "copper", "iron", "gold", "diamond", "netherite"};
    private static final String[] WEAPONS = {"longsword", "spear", "dagger"};
    // The material spear loop below already registers the existing spear IDs.
    // Only these vanilla-style aliases are new registrations.
    private static final String[] VANILLA_SPEARS = {"wooden_spear", "golden_spear"};

    @Override public void onInitialize() {
        List<Item> combatItems = new ArrayList<>();
        for (String material : MATERIALS) for (String weapon : WEAPONS) {
            Item item = Registry.register(BuiltInRegistries.ITEM,
                    ResourceLocation.fromNamespaceAndPath(CombatOverhaul.MOD_ID, material + "_" + weapon),
                    new CombatWeaponItem(material, weapon, tier(material)));
            combatItems.add(item);
        }
        for (String spear : VANILLA_SPEARS) {
            String material = spearMaterial(spear);
            combatItems.add(Registry.register(BuiltInRegistries.ITEM,
                    ResourceLocation.fromNamespaceAndPath(CombatOverhaul.MOD_ID, spear),
                    new CombatWeaponItem(material, "spear", tier(material))));
        }
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.COMBAT)
                .register(entries -> combatItems.forEach(entries::accept));
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new FabricWeaponSpecReloadListener());
        CombatOverhaulFabricEvents.register();
        CombatOverhaul.LOGGER.info("Combat Overhaul 1.21.1 Fabric initialized");
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
