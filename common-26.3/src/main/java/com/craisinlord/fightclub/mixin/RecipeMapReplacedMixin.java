package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.combat.ExternalWeapons;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;
import java.util.stream.Collectors;

@Mixin(RecipeMap.class)
public abstract class RecipeMapReplacedMixin {
    @Inject(method = "create", at = @At("RETURN"), cancellable = true)
    private static void fightClub$dropReplacedRecipes(HolderLookup<Recipe<?>> lookup, CallbackInfoReturnable<RecipeMap> cir) {
        if (!com.craisinlord.fightclub.CombatConfig.enabled(com.craisinlord.fightclub.CombatConfig.HIDE_REPLACED)
                || ExternalWeapons.reloadingManager == null) return;
        Set<Item> replaced = ExternalWeapons.replacedIn(ExternalWeapons.readRaw(ExternalWeapons.reloadingManager));
        if (replaced.isEmpty()) return;
        Set<Identifier> ids = replaced.stream().map(BuiltInRegistries.ITEM::getKey).collect(Collectors.toSet());
        ImmutableMultimap.Builder<RecipeType<?>, RecipeHolder<?>> byType = ImmutableMultimap.builder();
        ImmutableMap.Builder<ResourceKey<Recipe<?>>, RecipeHolder<?>> byKey = ImmutableMap.builder();
        int dropped = 0;
        for (RecipeHolder<?> holder : cir.getReturnValue().values()) {
            if (ids.contains(holder.id().identifier())) {
                dropped++;
                continue;
            }
            byType.put(holder.value().getType(), holder);
            byKey.put(holder.id(), holder);
        }
        if (dropped == 0) return;
        com.craisinlord.fightclub.FightClub.LOGGER.info("Fight Club: disabled {} recipes for weapons replaced by other mods", dropped);
        cir.setReturnValue(RecipeMapAccessor.fightClub$create(byType.build(), byKey.buildOrThrow()));
    }
}
