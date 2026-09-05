package com.craisinlord.combatoverhaul.fabric;

import com.craisinlord.combatoverhaul.CombatOverhaul;
import com.craisinlord.combatoverhaul.combat.CombatWeaponItem;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

import java.util.ArrayList;
import java.util.List;

public final class CombatOverhaulFabric implements ModInitializer {
    private static final String[] MATERIALS = {"wood", "stone", "copper", "iron", "gold", "diamond", "netherite"};
    private static final String[] WEAPONS = {"longsword", "dagger"};

    @Override public void onInitialize() {
        List<Item> combatItems = new ArrayList<>();
        for (String material : MATERIALS) for (String weapon : WEAPONS) {
            Identifier id = Identifier.fromNamespaceAndPath(CombatOverhaul.MOD_ID, material + "_" + weapon);
            Item item = Registry.register(BuiltInRegistries.ITEM,
                    id,
                    new CombatWeaponItem(material, weapon, tier(material), ResourceKey.create(Registries.ITEM, id)));
            combatItems.add(item);
        }
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT)
                .register(output -> combatItems.forEach(output::prepend));
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new FabricWeaponSpecReloadListener());
        CombatOverhaulFabricEvents.register();
        CombatOverhaul.LOGGER.info("Combat Overhaul 26.2 Fabric initialized");
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
