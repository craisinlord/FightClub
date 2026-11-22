package com.craisinlord.fightclub.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.function.Predicate;

public enum CompatMaterial implements Tier {
    SILVER("generic", "silver", List.of("iceandfire", "eidolon", "caverns_and_chasms", "galosphere", "oreganized"),
            460, 2.0f, 2, 14, "#c:ingots/silver"),
    ELECTRUM("oreganized", "electrum", List.of("oreganized"), 1561, 3.0f, 4, 14, "oreganized:electrum_ingot"),
    NECROMIUM("caverns_and_chasms", "necromium", List.of("caverns_and_chasms"), 2031, 4.0f, 4, 15, "caverns_and_chasms:necromium_ingot"),
    DARK_METAL("born_in_chaos", "dark_metal", List.of("born_in_chaos_v1"), 1100, 3.0f, 4, 10, "born_in_chaos_v1:dark_metal_ingot"),
    DRAGONSTEEL_FIRE("iceandfire", "dragonsteel_fire", List.of("iceandfire"), 8000, 4.0f, 5, 10, "iceandfire:dragonsteel_fire_ingot"),
    DRAGONSTEEL_ICE("iceandfire", "dragonsteel_ice", List.of("iceandfire"), 8000, 4.0f, 5, 10, "iceandfire:dragonsteel_ice_ingot"),
    DRAGONSTEEL_LIGHTNING("iceandfire", "dragonsteel_lightning", List.of("iceandfire"), 8000, 4.0f, 5, 10, "iceandfire:dragonsteel_lightning_ingot"),
    DRAGONBONE("iceandfire", "dragonbone", List.of("iceandfire"), 1660, 4.0f, 4, 10, "iceandfire:dragonbone"),
    DRAGONBONE_FIRE("iceandfire", "dragonbone_fire", List.of("iceandfire"), 2000, 4.0f, 4, 10, "iceandfire:dragonbone"),
    DRAGONBONE_ICE("iceandfire", "dragonbone_ice", List.of("iceandfire"), 2000, 4.0f, 4, 10, "iceandfire:dragonbone"),
    DRAGONBONE_LIGHTNING("iceandfire", "dragonbone_lightning", List.of("iceandfire"), 2000, 4.0f, 4, 10, "iceandfire:dragonbone"),
    ULTIMATE("antarchy", "ultimate", List.of("antarchy"), 3072, 0.0f, 5, 25, "antarchy:titanium_ingot"),
    ZANITE("aether", "zanite", List.of("aether"), 250, 2.0f, 2, 14, "#aether:gems/zanite"),
    GRAVITITE("aether", "gravitite", List.of("aether"), 1561, 3.0f, 3, 10, "#aether:processed/gravitite"),
    IRONWOOD("twilightforest", "ironwood", List.of("twilightforest"), 512, 2.0f, 2, 25, "#c:ingots/ironwood"),
    STEELEAF("twilightforest", "steeleaf", List.of("twilightforest"), 131, 3.0f, 3, 9, "#c:ingots/steeleaf"),
    KNIGHTMETAL("twilightforest", "knightmetal", List.of("twilightforest"), 512, 3.0f, 3, 8, "#c:ingots/knightmetal"),
    FIERY("twilightforest", "fiery", List.of("twilightforest"), 1024, 4.0f, 3, 10, "#c:ingots/fiery"),
    BRONZE("mekanism", "bronze", List.of("mekanism"), 375, 2.0f, 2, 10, "#c:ingots/bronze"),
    LAPIS_LAZULI("mekanism", "lapis_lazuli", List.of("mekanism"), 128, 1.0f, 1, 32, "#c:gems/lapis"),
    OSMIUM("mekanism", "osmium", List.of("mekanism"), 1024, 4.0f, 3, 14, "#c:ingots/osmium"),
    REFINED_GLOWSTONE("mekanism", "refined_glowstone", List.of("mekanism"), 384, 2.0f, 3, 20, "#c:ingots/refined_glowstone"),
    REFINED_OBSIDIAN("mekanism", "refined_obsidian", List.of("mekanism"), 4096, 8.0f, 4, 18, "#c:ingots/refined_obsidian"),
    STEEL("mekanism", "steel", List.of("mekanism"), 500, 3.0f, 3, 16, "#c:ingots/steel"),
    RESONARIUM("deeperdarker", "resonarium", List.of("deeperdarker"), 1601, 3.0f, 3, 11, "deeperdarker:resonarium_plate"),
    WARDEN("deeperdarker", "warden", List.of("deeperdarker"), 2519, 5.0f, 4, 18, "deeperdarker:reinforced_echo_shard"),
    CLOGGRUM("undergarden", "cloggrum", List.of("undergarden"), 286, 3.0f, 2, 8, "undergarden:cloggrum_ingot"),
    FROSTSTEEL("undergarden", "froststeel", List.of("undergarden"), 575, 2.0f, 2, 20, "undergarden:froststeel_ingot"),
    UTHERIUM("undergarden", "utherium", List.of("undergarden"), 1279, 3.5f, 3, 17, "undergarden:utherium_crystal"),
    FORGOTTEN("undergarden", "forgotten", List.of("undergarden"), 1876, 3.0f, 3, 10, "undergarden:forgotten_ingot");

    public static final List<String> WEAPONS = List.of("longsword", "spear", "dagger");

    public final String folder;
    public final String material;
    public final List<String> mods;
    private final int uses;
    private final float attackDamageBonus;
    private final int miningLevel;
    private final int enchantability;
    private final String repairIngredient;

    CompatMaterial(String folder, String material, List<String> mods, int uses, float attackDamageBonus,
                   int miningLevel, int enchantability, String repairIngredient) {
        this.folder = folder;
        this.material = material;
        this.mods = mods;
        this.uses = uses;
        this.attackDamageBonus = attackDamageBonus;
        this.miningLevel = miningLevel;
        this.enchantability = enchantability;
        this.repairIngredient = repairIngredient;
    }

    public boolean isActive(Predicate<String> modLoaded) {
        return mods.stream().anyMatch(modLoaded);
    }

    public String itemPath(String weapon) {
        return folder + "/" + material + "/" + weapon;
    }

    @Override public int getUses() { return uses; }
    @Override public float getSpeed() { return 8.0f; }
    @Override public float getAttackDamageBonus() { return attackDamageBonus; }
    @Override public int getEnchantmentValue() { return enchantability; }

    @Override
    public TagKey<Block> getIncorrectBlocksForDrops() {
        return switch (miningLevel) {
            case 5, 4 -> Tiers.NETHERITE.getIncorrectBlocksForDrops();
            case 3 -> Tiers.DIAMOND.getIncorrectBlocksForDrops();
            case 2 -> Tiers.IRON.getIncorrectBlocksForDrops();
            case 1 -> Tiers.STONE.getIncorrectBlocksForDrops();
            default -> BlockTags.INCORRECT_FOR_WOODEN_TOOL;
        };
    }

    @Override
    public Ingredient getRepairIngredient() {
        if (repairIngredient.startsWith("#")) {
            return Ingredient.of(TagKey.create(Registries.ITEM, ResourceLocation.parse(repairIngredient.substring(1))));
        }
        return Ingredient.of(BuiltInRegistries.ITEM.get(ResourceLocation.parse(repairIngredient)));
    }
}
