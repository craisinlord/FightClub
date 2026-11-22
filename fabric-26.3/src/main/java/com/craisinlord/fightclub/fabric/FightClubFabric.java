package com.craisinlord.fightclub.fabric;

import com.craisinlord.fightclub.FightClub;
import com.craisinlord.fightclub.combat.CombatWeaponItem;
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
import com.craisinlord.fightclub.combat.DaggerProjectile;
import com.craisinlord.fightclub.combat.SpearProjectile;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

import java.util.ArrayList;
import java.util.List;

public final class FightClubFabric implements ModInitializer {
    public static EntityType<DaggerProjectile> DAGGER_PROJECTILE;
    public static EntityType<SpearProjectile> SPEAR_PROJECTILE;
    private static final String[] MATERIALS = {"wood", "stone", "copper", "iron", "gold", "diamond", "netherite"};
    private static final String[] WEAPONS = {"longsword", "dagger"};

    @Override public void onInitialize() {
        com.craisinlord.fightclub.CombatConfig.load(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir());
        com.craisinlord.fightclub.combat.CombatSounds.ALL.forEach((path, sound) ->
                Registry.register(BuiltInRegistries.SOUND_EVENT, com.craisinlord.fightclub.combat.CombatSounds.id(path), sound));
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, com.craisinlord.fightclub.combat.CombatParticles.LONGSWORD_SWEEP_ID,
                com.craisinlord.fightclub.combat.CombatParticles.LONGSWORD_SWEEP);
        Identifier daggerId = Identifier.fromNamespaceAndPath(FightClub.MOD_ID, "dagger_projectile");
        Identifier spearId = Identifier.fromNamespaceAndPath(FightClub.MOD_ID, "spear_projectile");
        DAGGER_PROJECTILE = Registry.register(BuiltInRegistries.ENTITY_TYPE, daggerId, EntityType.Builder.<DaggerProjectile>of(DaggerProjectile::new, MobCategory.MISC).sized(0.25F, 0.25F).clientTrackingRange(4).build(ResourceKey.create(Registries.ENTITY_TYPE, daggerId)));
        SPEAR_PROJECTILE = Registry.register(BuiltInRegistries.ENTITY_TYPE, spearId, EntityType.Builder.<SpearProjectile>of(SpearProjectile::new, MobCategory.MISC).sized(0.25F, 0.25F).clientTrackingRange(4).build(ResourceKey.create(Registries.ENTITY_TYPE, spearId)));
        List<Item> combatItems = new ArrayList<>();
        for (String material : MATERIALS) for (String weapon : WEAPONS) {
            Identifier id = Identifier.fromNamespaceAndPath(FightClub.MOD_ID, material + "_" + weapon);
            Item item = Registry.register(BuiltInRegistries.ITEM,
                    id,
                    new CombatWeaponItem(material, weapon, tier(material), ResourceKey.create(Registries.ITEM, id)));
            combatItems.add(item);
        }
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT)
                .register(output -> combatItems.stream().filter(item -> !com.craisinlord.fightclub.combat.ExternalWeapons.isReplaced(item)).forEach(output::prepend));
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new FabricWeaponSpecReloadListener());
        FightClubFabricEvents.register();
        FightClub.LOGGER.info("Fight Club 26.3 Fabric initialized");
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
