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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import com.craisinlord.combatoverhaul.combat.DaggerProjectile;
import com.craisinlord.combatoverhaul.combat.SpearProjectile;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

import java.util.ArrayList;
import java.util.List;

public final class CombatOverhaulFabric implements ModInitializer {
    public static EntityType<DaggerProjectile> DAGGER_PROJECTILE;
    public static EntityType<SpearProjectile> SPEAR_PROJECTILE;
    private static final String[] MATERIALS = {"wood", "stone", "copper", "iron", "gold", "diamond", "netherite"};
    private static final String[] WEAPONS = {"longsword", "dagger"};

    @Override public void onInitialize() {
        Identifier daggerId = Identifier.fromNamespaceAndPath(CombatOverhaul.MOD_ID, "dagger_projectile");
        Identifier spearId = Identifier.fromNamespaceAndPath(CombatOverhaul.MOD_ID, "spear_projectile");
        DAGGER_PROJECTILE = Registry.register(BuiltInRegistries.ENTITY_TYPE, daggerId, EntityType.Builder.<DaggerProjectile>of(DaggerProjectile::new, MobCategory.MISC).sized(0.25F, 0.25F).clientTrackingRange(4).build(ResourceKey.create(Registries.ENTITY_TYPE, daggerId)));
        SPEAR_PROJECTILE = Registry.register(BuiltInRegistries.ENTITY_TYPE, spearId, EntityType.Builder.<SpearProjectile>of(SpearProjectile::new, MobCategory.MISC).sized(0.25F, 0.25F).clientTrackingRange(4).build(ResourceKey.create(Registries.ENTITY_TYPE, spearId)));
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
