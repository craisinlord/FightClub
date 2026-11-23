package com.craisinlord.fightclub.client;

import com.craisinlord.fightclub.FightClub;
import com.craisinlord.fightclub.combat.CombatWeapons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class CombatModels {
    private static final String[] MATERIALS = {"wood", "stone", "copper", "iron", "gold", "diamond", "netherite"};
    private static final String[] WEAPONS = {"longsword", "spear"};
    private static final ResourceLocation IRON_DAGGER = ResourceLocation.fromNamespaceAndPath(FightClub.MOD_ID, "item/iron_dagger");
    private static final ResourceLocation IRON_SPEAR_IN_HAND = ResourceLocation.fromNamespaceAndPath(FightClub.MOD_ID, "item/iron_spear_in_hand");
    private static final ResourceLocation IRON_LONGSWORD_IN_HAND = ResourceLocation.fromNamespaceAndPath(FightClub.MOD_ID, "item/iron_longsword_in_hand");
    private static final Map<Item, ResourceLocation> IN_HAND_IDS = new ConcurrentHashMap<>();
    private static final Map<Item, ResourceLocation> ICON_IDS = new ConcurrentHashMap<>();
    private static Function<ResourceLocation, BakedModel> lookup = id -> null;

    private CombatModels() {}

    public static List<ResourceLocation> inHandModelIds() {
        List<ResourceLocation> ids = new ArrayList<>();
        for (String material : MATERIALS) for (String weapon : WEAPONS) {
            ids.add(ResourceLocation.fromNamespaceAndPath(FightClub.MOD_ID, "item/" + material + "_" + weapon + "_in_hand"));
        }
        ids.add(ResourceLocation.fromNamespaceAndPath(FightClub.MOD_ID, "item/iron_dagger"));
        if (CombatWeapons.backportSpears()) for (String material : CombatWeapons.BACKPORT_SPEAR_MATERIALS) {
            ids.add(ResourceLocation.fromNamespaceAndPath(FightClub.MOD_ID, "item/" + material + "_spear"));
        }
        for (com.craisinlord.fightclub.compat.CompatMaterial compat : com.craisinlord.fightclub.compat.CompatMaterial.values()) {
            if (!compat.isActive(com.craisinlord.fightclub.combat.CombatPlatform::isModLoaded)) continue;
            for (String weapon : WEAPONS) {
                ids.add(ResourceLocation.fromNamespaceAndPath(FightClub.MOD_ID, "item/" + compat.itemPath(weapon) + "_in_hand"));
            }
        }
        for (ResourceLocation item : com.craisinlord.fightclub.api.FightClubApi.inHandModelItems()) {
            ids.add(ResourceLocation.fromNamespaceAndPath(item.getNamespace(), "item/" + item.getPath() + "_in_hand"));
        }
        return ids;
    }

    public static void setLookup(Function<ResourceLocation, BakedModel> modelLookup) {
        lookup = modelLookup;
    }

    private static float modelSpin;

    public static void setModelSpin(float degrees) {
        modelSpin = degrees;
    }

    public static float modelSpin() {
        return modelSpin;
    }

    public static final float TRIDENT_POLE_X = -0.5F, TRIDENT_POLE_Z = -0.5F;

    private static float modelRoll, rollPivotX, rollPivotZ;

    public static void setModelRoll(float degrees, float pivotX, float pivotZ) {
        modelRoll = degrees;
        rollPivotX = pivotX;
        rollPivotZ = pivotZ;
    }

    public static float modelRoll() {
        return modelRoll;
    }

    public static float rollPivotX() {
        return rollPivotX;
    }

    public static float rollPivotZ() {
        return rollPivotZ;
    }

    public static boolean usesIcon(ItemDisplayContext context) {
        return context == ItemDisplayContext.GUI || context == ItemDisplayContext.GROUND || context == ItemDisplayContext.FIXED;
    }

    public static BakedModel inHand(ItemStack stack) {
        String weapon = CombatWeapons.weapon(stack.getItem());
        if (weapon == null || weapon.equals("dagger")) return null;
        return model(IN_HAND_IDS.computeIfAbsent(stack.getItem(), item -> modelPath(item, "_in_hand")));
    }

    public static BakedModel inHandReference(ItemStack stack, ItemDisplayContext context) {
        if (usesIcon(context) || stack.getItem() instanceof com.craisinlord.fightclub.combat.CombatWeaponItem
                || CombatWeapons.backportSpearMaterial(stack.getItem()) != null || inHand(stack) != null) return null;
        String weapon = CombatWeapons.weapon(stack.getItem());
        if ("dagger".equals(weapon)) return model(IRON_DAGGER);
        if ("spear".equals(weapon)) return model(IRON_SPEAR_IN_HAND);
        if ("longsword".equals(weapon)) return model(IRON_LONGSWORD_IN_HAND);
        return null;
    }

    public static float inHandReferenceRoll(ItemStack stack) {
        return "spear".equals(CombatWeapons.weapon(stack.getItem())) ? 90.0F : 0.0F;
    }

    public static BakedModel icon(ItemStack stack) {
        String material = CombatWeapons.backportSpearMaterial(stack.getItem());
        return material == null ? null : model(ICON_IDS.computeIfAbsent(stack.getItem(), item -> modelPath(item, "")));
    }

    private static ResourceLocation modelPath(Item item, String suffix) {
        String backport = CombatWeapons.backportSpearMaterial(item);
        if (backport != null) return ResourceLocation.fromNamespaceAndPath(FightClub.MOD_ID, "item/" + backport + "_spear" + suffix);
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "item/" + id.getPath() + suffix);
    }

    private static BakedModel model(ResourceLocation id) {
        BakedModel model = lookup.apply(id);
        if (model == null || model == Minecraft.getInstance().getModelManager().getMissingModel()) return null;
        return model;
    }
}
