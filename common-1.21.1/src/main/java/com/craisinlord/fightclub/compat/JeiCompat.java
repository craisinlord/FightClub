package com.craisinlord.fightclub.compat;

import com.craisinlord.fightclub.FightClub;
import com.craisinlord.fightclub.combat.ExternalWeapons;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

@JeiPlugin
public final class JeiCompat implements IModPlugin {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(FightClub.MOD_ID, "jei");
    private static final List<ItemStack> HIDDEN = new ArrayList<>();
    private static IJeiRuntime runtime;

    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        HIDDEN.clear();
        ExternalWeapons.setReplacedListener(() -> Minecraft.getInstance().execute(JeiCompat::refresh));
        refresh();
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
        HIDDEN.clear();
    }

    private static void refresh() {
        IJeiRuntime current = runtime;
        if (current == null) return;
        IIngredientManager ingredients = current.getIngredientManager();
        if (!HIDDEN.isEmpty()) {
            ingredients.addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, List.copyOf(HIDDEN));
            HIDDEN.clear();
        }
        java.util.Set<Item> replaced = com.craisinlord.fightclub.combat.CombatWeapons.hiddenItems();
        if (replaced.isEmpty()) return;
        for (ItemStack stack : ingredients.getAllIngredients(VanillaTypes.ITEM_STACK)) {
            if (replaced.contains(stack.getItem())) HIDDEN.add(stack);
        }
        if (HIDDEN.isEmpty()) return;
        ingredients.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, List.copyOf(HIDDEN));
    }
}
