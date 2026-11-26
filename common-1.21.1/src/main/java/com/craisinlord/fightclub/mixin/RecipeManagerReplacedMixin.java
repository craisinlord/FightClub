package com.craisinlord.fightclub.mixin;

import com.craisinlord.fightclub.combat.ExternalWeapons;
import com.google.gson.JsonElement;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Mixin(RecipeManager.class)
public abstract class RecipeManagerReplacedMixin {
    @Shadow @Final private HolderLookup.Provider registries;

    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V", at = @At("TAIL"))
    private void fightClub$dropReplacedRecipes(Map<ResourceLocation, JsonElement> map, ResourceManager manager, ProfilerFiller profiler, CallbackInfo ci) {
        Set<Item> replaced = new java.util.HashSet<>();
        if (com.craisinlord.fightclub.CombatConfig.enabled(com.craisinlord.fightclub.CombatConfig.HIDE_REPLACED)) {
            replaced.addAll(ExternalWeapons.replacedIn(ExternalWeapons.readRaw(manager)));
        }
        replaced.addAll(com.craisinlord.fightclub.combat.CombatWeapons.disabledCopperItems());
        if (replaced.isEmpty()) return;
        RecipeManager self = (RecipeManager) (Object) this;
        List<RecipeHolder<?>> kept = new ArrayList<>();
        int dropped = 0;
        for (RecipeHolder<?> holder : self.getRecipes()) {
            Item result;
            try {
                result = holder.value().getResultItem(registries).getItem();
            } catch (RuntimeException exception) {
                kept.add(holder);
                continue;
            }
            if (replaced.contains(result)) dropped++;
            else kept.add(holder);
        }
        if (dropped == 0) return;
        self.replaceRecipes(kept);
        com.craisinlord.fightclub.FightClub.LOGGER.info("Fight Club: disabled {} recipes for hidden weapons", dropped);
    }
}
