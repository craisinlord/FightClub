package com.craisinlord.fightclub.client;

import com.craisinlord.fightclub.FightClub;
import com.craisinlord.fightclub.combat.CombatWeapons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Perspective-specific models, the same way vanilla handles the trident and spyglass: weapons
 * render with {@code models/item/<id>_in_hand.json} when held or thrown, and keep their flat
 * inventory icon in GUIs, on the ground and in item frames.
 *
 * <p>1.21.1 doesn't support the newer {@code assets/<ns>/items/} definitions, so each loader
 * registers these extra models and supplies the lookup.
 */
public final class CombatModels {
    private static final String[] MATERIALS = {"wood", "stone", "copper", "iron", "gold", "diamond", "netherite"};
    // Daggers use their normal item model everywhere: the 2D texture extruded one pixel, like swords.
    private static final String[] WEAPONS = {"longsword", "spear"};
    private static Function<ResourceLocation, BakedModel> lookup = id -> null;

    private CombatModels() {}

    /** Model ids (e.g. {@code fight_club:item/iron_dagger_in_hand}) the loaders must load. */
    public static List<ResourceLocation> inHandModelIds() {
        List<ResourceLocation> ids = new ArrayList<>();
        for (String material : MATERIALS) for (String weapon : WEAPONS) {
            ids.add(ResourceLocation.fromNamespaceAndPath(FightClub.MOD_ID, "item/" + material + "_" + weapon + "_in_hand"));
        }
        if (CombatWeapons.backportSpears()) for (String material : CombatWeapons.BACKPORT_SPEAR_MATERIALS) {
            ids.add(ResourceLocation.fromNamespaceAndPath(FightClub.MOD_ID, "item/" + material + "_spear"));
        }
        // Other mods' weapons that ship an _in_hand model (FightClubApi.registerInHandModel).
        for (ResourceLocation item : com.craisinlord.fightclub.api.FightClubApi.inHandModelItems()) {
            ids.add(ResourceLocation.fromNamespaceAndPath(item.getNamespace(), "item/" + item.getPath() + "_in_hand"));
        }
        return ids;
    }

    public static void setLookup(Function<ResourceLocation, BakedModel> modelLookup) {
        lookup = modelLookup;
    }

    private static float modelSpin;

    /**
     * Turns the next item render's model by this many degrees around its own face normal, pivoting
     * on the model's center, after its display transform. Used to re-point a weapon for a
     * different hold without separate model files. Callers reset it to 0 right after rendering.
     */
    public static void setModelSpin(float degrees) {
        modelSpin = degrees;
    }

    public static float modelSpin() {
        return modelSpin;
    }

    /** True for display contexts that keep the flat icon (vanilla's rule for the trident). */
    public static boolean usesIcon(ItemDisplayContext context) {
        return context == ItemDisplayContext.GUI || context == ItemDisplayContext.GROUND || context == ItemDisplayContext.FIXED;
    }

    /** The in-hand model for this stack, or null to keep the model vanilla picked. */
    public static BakedModel inHand(ItemStack stack) {
        String weapon = CombatWeapons.weapon(stack.getItem());
        if (weapon == null || weapon.equals("dagger")) return null;
        return model(modelPath(stack, "_in_hand"));
    }

    public static BakedModel icon(ItemStack stack) {
        String material = CombatWeapons.backportSpearMaterial(stack.getItem());
        return material == null ? null : model(modelPath(stack, ""));
    }

    private static ResourceLocation modelPath(ItemStack stack, String suffix) {
        String backport = CombatWeapons.backportSpearMaterial(stack.getItem());
        if (backport != null) return ResourceLocation.fromNamespaceAndPath(FightClub.MOD_ID, "item/" + backport + "_spear" + suffix);
        ResourceLocation item = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return ResourceLocation.fromNamespaceAndPath(item.getNamespace(), "item/" + item.getPath() + suffix);
    }

    private static BakedModel model(ResourceLocation id) {
        BakedModel model = lookup.apply(id);
        if (model == null || model == Minecraft.getInstance().getModelManager().getMissingModel()) return null;
        return model;
    }
}
