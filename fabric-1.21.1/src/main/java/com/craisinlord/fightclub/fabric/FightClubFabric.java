package com.craisinlord.fightclub.fabric;

import com.craisinlord.fightclub.FightClub;
import com.craisinlord.fightclub.combat.CombatPlatform;
import com.craisinlord.fightclub.combat.CombatWeaponItem;
import com.craisinlord.fightclub.compat.CompatMaterial;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import com.craisinlord.fightclub.combat.DaggerProjectile;
import com.craisinlord.fightclub.combat.SpearProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;

public final class FightClubFabric implements ModInitializer {
    public static EntityType<DaggerProjectile> DAGGER_PROJECTILE;
    public static EntityType<SpearProjectile> SPEAR_PROJECTILE;
    private static final String[] MATERIALS = {"wood", "stone", "copper", "iron", "gold", "diamond", "netherite"};
    private static final String[] WEAPONS = {"longsword", "spear", "dagger"};

    @Override public void onInitialize() {
        com.craisinlord.fightclub.CombatConfig.load(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir());
        ResourceLocation daggerId = ResourceLocation.fromNamespaceAndPath(FightClub.MOD_ID, "dagger_projectile");
        ResourceLocation spearId = ResourceLocation.fromNamespaceAndPath(FightClub.MOD_ID, "spear_projectile");
        DAGGER_PROJECTILE = Registry.register(BuiltInRegistries.ENTITY_TYPE, daggerId, EntityType.Builder.<DaggerProjectile>of(DaggerProjectile::new, MobCategory.MISC).sized(0.25F, 0.25F).build("dagger_projectile"));
        SPEAR_PROJECTILE = Registry.register(BuiltInRegistries.ENTITY_TYPE, spearId, EntityType.Builder.<SpearProjectile>of(SpearProjectile::new, MobCategory.MISC).sized(0.25F, 0.25F).build("spear_projectile"));
        com.craisinlord.fightclub.combat.CombatSounds.ALL.forEach((path, sound) ->
                Registry.register(BuiltInRegistries.SOUND_EVENT, sound.getLocation(), sound));
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, com.craisinlord.fightclub.combat.CombatParticles.LONGSWORD_SWEEP_ID,
                com.craisinlord.fightclub.combat.CombatParticles.LONGSWORD_SWEEP);
        List<Item> combatItems = new ArrayList<>();
        for (String material : MATERIALS) for (String weapon : WEAPONS) {
            if (!com.craisinlord.fightclub.combat.CombatWeapons.registersOwn(material, weapon)) continue;
            Item item = Registry.register(BuiltInRegistries.ITEM,
                    ResourceLocation.fromNamespaceAndPath(FightClub.MOD_ID, material + "_" + weapon),
                    new CombatWeaponItem(material, weapon, tier(material)));
            combatItems.add(item);
        }
        for (CompatMaterial compat : CompatMaterial.values()) {
            if (!compat.isActive(CombatPlatform::isModLoaded)) continue;
            for (String weapon : CompatMaterial.WEAPONS) {
                combatItems.add(Registry.register(BuiltInRegistries.ITEM,
                        ResourceLocation.fromNamespaceAndPath(FightClub.MOD_ID, compat.itemPath(weapon)),
                        new CombatWeaponItem(compat.material, weapon, compat, com.craisinlord.fightclub.compat.CompatAbilities.properties(compat))));
            }
            FightClub.LOGGER.info("Fight Club: enabled {} weapons", compat.material);
        }
        combatItems.sort(java.util.Comparator.comparingInt(item -> CompatMaterial.WEAPONS.indexOf(((CombatWeaponItem) item).combatWeapon())));
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.COMBAT)
                .register(entries -> combatItems.stream().filter(item -> !com.craisinlord.fightclub.combat.ExternalWeapons.isReplaced(item)).forEach(entries::accept));
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, ResourceLocation.fromNamespaceAndPath(FightClub.MOD_ID, "combat"),
                net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup.builder()
                        .title(Component.translatable("itemGroup.fight_club.combat"))
                        .icon(() -> new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(FightClub.MOD_ID, "diamond_dagger"))))
                        .displayItems((parameters, output) -> combatItems.stream().filter(item -> !com.craisinlord.fightclub.combat.ExternalWeapons.isReplaced(item)).forEach(output::accept))
                        .build());
        net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents.MODIFY.register(context -> {
            int size = com.craisinlord.fightclub.combat.CombatDefaults.potionStackSize();
            if (size > 1) com.craisinlord.fightclub.combat.CombatDefaults.POTIONS.forEach(potion -> context.modify(potion, builder ->
                    builder.set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE, size)));
            if (com.craisinlord.fightclub.combat.CombatWeapons.backportSpears()) {
                for (String material : com.craisinlord.fightclub.combat.CombatWeapons.BACKPORT_SPEAR_MATERIALS) {
                    Item spear = com.craisinlord.fightclub.combat.CombatWeapons.backportSpear(material);
                    com.craisinlord.fightclub.combat.CombatWeapons.captureMobComponents(spear);
                    context.modify(spear, builder -> {
                        builder.set(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS, net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY);
                        com.craisinlord.fightclub.combat.CombatWeapons.backportComponentTypes().forEach(type -> builder.set(type, null));
                    });
                }
            }
        });
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new FabricWeaponSpecReloadListener());
        FightClubFabricEvents.register();
        FightClub.LOGGER.info("Fight Club 1.21.1 Fabric initialized");
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
