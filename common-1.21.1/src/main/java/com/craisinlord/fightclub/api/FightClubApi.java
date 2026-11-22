package com.craisinlord.fightclub.api;

import com.craisinlord.fightclub.combat.CombatChargeState;
import com.craisinlord.fightclub.combat.CombatRecovery;
import com.craisinlord.fightclub.combat.CombatWeaponItem;
import com.craisinlord.fightclub.combat.CombatWeapons;
import com.craisinlord.fightclub.combat.WeaponSpec;
import com.craisinlord.fightclub.combat.WeaponSpecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class FightClubApi {
    public static final int API_VERSION = 1;

    private static final Pattern MATERIAL = Pattern.compile("[a-z0-9_.-]+");
    private static final Set<ResourceLocation> IN_HAND_MODELS = ConcurrentHashMap.newKeySet();

    private FightClubApi() {}

    public static Item createWeapon(WeaponType type, String material, Tier tier) {
        return createWeapon(type, material, tier, new Item.Properties());
    }

    public static Item createWeapon(WeaponType type, String material, Tier tier, Item.Properties properties) {
        checkMaterial(material);
        return new CombatWeaponItem(material, type.id(), tier, properties);
    }

    public static void registerDefaultSpec(WeaponSpec spec) {
        checkMaterial(spec.material());
        if (WeaponType.byId(spec.weapon()) == null) {
            throw new IllegalArgumentException("Fight Club spec weapon must be longsword, spear or dagger, not " + spec.weapon());
        }
        WeaponSpecs.registerDefault(spec);
    }

    public static WeaponSpecBuilder specBuilder(String material, WeaponType type) {
        checkMaterial(material);
        return new WeaponSpecBuilder(material, type);
    }

    public static void registerInHandModel(ResourceLocation itemId) {
        IN_HAND_MODELS.add(itemId);
    }

    public static Set<ResourceLocation> inHandModelItems() {
        return Set.copyOf(IN_HAND_MODELS);
    }

    public static boolean isWeapon(ItemStack stack) {
        return !stack.isEmpty() && CombatWeapons.isCombatWeapon(stack.getItem());
    }

    public static Optional<WeaponType> weaponType(ItemStack stack) {
        return isWeapon(stack) ? Optional.ofNullable(WeaponType.byId(CombatWeapons.weapon(stack.getItem()))) : Optional.empty();
    }

    public static Optional<String> material(ItemStack stack) {
        return isWeapon(stack) ? Optional.ofNullable(CombatWeapons.material(stack.getItem())) : Optional.empty();
    }

    public static Optional<WeaponSpec> spec(ItemStack stack) {
        return isWeapon(stack) ? Optional.ofNullable(CombatWeapons.spec(stack.getItem())) : Optional.empty();
    }

    public static WeaponSpec spec(String material, WeaponType type) {
        return WeaponSpecs.getOrLoadBuiltin(material, type.id());
    }

    public static boolean isRecovering(Player player) {
        return CombatRecovery.active(player);
    }

    public static boolean isCharging(Player player) {
        return CombatChargeState.isLeftCharging(player);
    }

    private static void checkMaterial(String material) {
        if (material == null || !MATERIAL.matcher(material).matches()) {
            throw new IllegalArgumentException("Fight Club material names must be lowercase [a-z0-9_.-], got '" + material + "'");
        }
    }
}
