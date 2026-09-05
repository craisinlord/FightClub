package com.craisinlord.combatoverhaul.neoforge;

import com.craisinlord.combatoverhaul.CombatOverhaul;
import com.craisinlord.combatoverhaul.combat.CombatWeaponItem;
import com.craisinlord.combatoverhaul.combat.WeaponSpecReloadListener;
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

@Mod(CombatOverhaul.MOD_ID)
public final class CombatOverhaulNeoForge {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, CombatOverhaul.MOD_ID);
    private static final String[] MATERIALS = {"wood", "stone", "copper", "iron", "gold", "diamond", "netherite"};
    private static final String[] WEAPONS = {"longsword", "dagger"};

    public CombatOverhaulNeoForge(IEventBus modBus) {
        for (String material : MATERIALS) for (String weapon : WEAPONS) {
            Identifier id = Identifier.fromNamespaceAndPath(CombatOverhaul.MOD_ID, material + "_" + weapon);
            ITEMS.register(material + "_" + weapon, () -> new CombatWeaponItem(material, weapon, tier(material), ResourceKey.create(Registries.ITEM, id)));
        }
        ITEMS.register(modBus);
        modBus.addListener((net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) -> {
            if (!event.getTabKey().equals(CreativeModeTabs.COMBAT)) return;
            for (String material : MATERIALS) for (String weapon : WEAPONS) {
                ITEMS.getEntries().stream()
                        .filter(entry -> entry.getId().getPath().equals(material + "_" + weapon))
                        .findFirst().ifPresent(entry -> event.accept(entry.get()));
            }
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.AddServerReloadListenersEvent event) ->
                event.addListener(net.minecraft.resources.Identifier.fromNamespaceAndPath(CombatOverhaul.MOD_ID, "weapon_specs"),
                        new WeaponSpecReloadListener()));
        NeoForge.EVENT_BUS.register(new CombatOverhaulNeoForgeEvents());
        CombatOverhaul.LOGGER.info("Combat Overhaul 26.2 NeoForge initialized");
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
