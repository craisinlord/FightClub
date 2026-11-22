package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.combat.ExternalWeapons;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mixin(RecipeManager.class)
public abstract class RecipeManagerReplacedMixin {
    @ModifyVariable(method = "apply(Lnet/minecraft/world/item/crafting/RecipeMap;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("HEAD"), argsOnly = true)
    private RecipeMap fightClub$dropReplacedRecipes(RecipeMap recipes, RecipeMap original, ResourceManager manager, ProfilerFiller profiler) {
        if (!com.craisinlord.fightclub.CombatConfig.enabled(com.craisinlord.fightclub.CombatConfig.HIDE_REPLACED)) return recipes;
        Set<Item> replaced = ExternalWeapons.replacedIn(ExternalWeapons.readRaw(manager));
        if (replaced.isEmpty()) return recipes;
        Set<Identifier> ids = replaced.stream().map(BuiltInRegistries.ITEM::getKey).collect(Collectors.toSet());
        List<RecipeHolder<?>> kept = new ArrayList<>();
        int dropped = 0;
        for (RecipeHolder<?> holder : recipes.values()) {
            if (ids.contains(holder.id().identifier())) dropped++;
            else kept.add(holder);
        }
        if (dropped == 0) return recipes;
        com.craisinlord.fightclub.FightClub.LOGGER.info("Fight Club: disabled {} recipes for weapons replaced by other mods", dropped);
        return RecipeMap.create(kept);
    }
}
