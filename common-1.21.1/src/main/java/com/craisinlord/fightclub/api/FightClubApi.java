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

/**
 * Fight Club's public API for other mods: add weapons that use Fight Club's longsword, spear and
 * dagger move sets, and ask what a stack or player is doing. Everything here is safe to call from
 * common code on both loaders; nothing touches client-only classes.
 *
 * <p>A weapon is an item plus a <em>spec</em> keyed by {@code <material>/<weapon type>}. The item
 * supplies durability, enchantability and repair through its {@link Tier}; the spec supplies
 * everything else (damage, speed, reach, sweep, charge, throw...). Specs come from datapack files
 * at {@code data/<any namespace>/weapons/<material>/<weapon>.json}, or from
 * {@link #registerDefaultSpec} when no file exists. Material names are shared by every mod, so
 * prefix yours (for example {@code examplemod_mythril}) unless you mean to share Fight Club's.
 *
 * <pre>{@code
 * // Common init, during item registration:
 * Item longsword = FightClubApi.createWeapon(WeaponType.LONGSWORD, "examplemod_mythril", MythrilTier.INSTANCE);
 * Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath("examplemod", "mythril_longsword"), longsword);
 * FightClubApi.registerDefaultSpec(FightClubApi.specBuilder("examplemod_mythril", WeaponType.LONGSWORD)
 *         .copyOf("diamond").attackDamage(11.5f).build());
 * }</pre>
 */
public final class FightClubApi {
    /** Bumped when something here changes in a way callers might need to check for. */
    public static final int API_VERSION = 1;

    private static final Pattern MATERIAL = Pattern.compile("[a-z0-9_.-]+");
    private static final Set<ResourceLocation> IN_HAND_MODELS = ConcurrentHashMap.newKeySet();

    private FightClubApi() {}

    // ---------------------------------------------------------------- Adding weapons

    /**
     * Creates a Fight Club weapon item. Register it yourself, under your own namespace, the same way
     * you register your other items. The item gets the weapon type's full move set, its tooltip,
     * and live attribute values from the spec for {@code material}/{@code type}.
     *
     * @param material spec material name, lowercase {@code [a-z0-9_.-]}
     * @param tier     durability, enchantability, repair ingredient and drop rules
     */
    public static Item createWeapon(WeaponType type, String material, Tier tier) {
        return createWeapon(type, material, tier, new Item.Properties());
    }

    /** As {@link #createWeapon(WeaponType, String, Tier)}, keeping extra properties such as fire resistance or rarity. Weapons never stack. */
    public static Item createWeapon(WeaponType type, String material, Tier tier, Item.Properties properties) {
        checkMaterial(material);
        return new CombatWeaponItem(material, type.id(), tier, properties);
    }

    /**
     * Registers a spec in code. It's used whenever no datapack file has the same material and
     * weapon, including before a world loads (main menu tooltips) and on clients of servers
     * without your data. Datapacks can still override it with a file. Register it on both sides,
     * from common init. A second call for the same material and type replaces the first.
     */
    public static void registerDefaultSpec(WeaponSpec spec) {
        checkMaterial(spec.material());
        if (WeaponType.byId(spec.weapon()) == null) {
            throw new IllegalArgumentException("Fight Club spec weapon must be longsword, spear or dagger, not " + spec.weapon());
        }
        WeaponSpecs.registerDefault(spec);
    }

    /** A builder for a spec, starting from Fight Club's iron spec of the same type. */
    public static WeaponSpecBuilder specBuilder(String material, WeaponType type) {
        checkMaterial(material);
        return new WeaponSpecBuilder(material, type);
    }

    /**
     * Tells Fight Club this item ships a 3D held model at
     * {@code assets/<namespace>/models/item/<path>_in_hand.json}. Fight Club loads it and uses it
     * in hand and when thrown, keeping the normal model in GUIs, on the ground and in item frames
     * (vanilla's trident rule). Without this call the normal model is used everywhere, which is
     * what daggers do. Call from common init; it only records the id.
     */
    public static void registerInHandModel(ResourceLocation itemId) {
        IN_HAND_MODELS.add(itemId);
    }

    /** Items registered with {@link #registerInHandModel}. Used by Fight Club's model loading. */
    public static Set<ResourceLocation> inHandModelItems() {
        return Set.copyOf(IN_HAND_MODELS);
    }

    // ---------------------------------------------------------------- Queries

    /** True for any Fight Club weapon: Fight Club's own, ones made with {@link #createWeapon}, and Vanilla Backport spears. */
    public static boolean isWeapon(ItemStack stack) {
        return !stack.isEmpty() && CombatWeapons.isCombatWeapon(stack.getItem());
    }

    public static Optional<WeaponType> weaponType(ItemStack stack) {
        return isWeapon(stack) ? Optional.ofNullable(WeaponType.byId(CombatWeapons.weapon(stack.getItem()))) : Optional.empty();
    }

    public static Optional<String> material(ItemStack stack) {
        return isWeapon(stack) ? Optional.ofNullable(CombatWeapons.material(stack.getItem())) : Optional.empty();
    }

    /** The spec in effect for this stack: datapack, then {@link #registerDefaultSpec}, then Fight Club's bundled file. */
    public static Optional<WeaponSpec> spec(ItemStack stack) {
        return isWeapon(stack) ? Optional.ofNullable(CombatWeapons.spec(stack.getItem())) : Optional.empty();
    }

    /** The spec in effect for a material and type, or null if there is none. */
    public static WeaponSpec spec(String material, WeaponType type) {
        return WeaponSpecs.getOrLoadBuiltin(material, type.id());
    }

    /** True while the player is in recovery after a special attack (charged hit, throw, smash) and can't use a weapon. */
    public static boolean isRecovering(Player player) {
        return CombatRecovery.active(player);
    }

    /** Server side: true while the player is holding a spear charge or a dagger pounce. */
    public static boolean isCharging(Player player) {
        return CombatChargeState.isLeftCharging(player);
    }

    private static void checkMaterial(String material) {
        if (material == null || !MATERIAL.matcher(material).matches()) {
            throw new IllegalArgumentException("Fight Club material names must be lowercase [a-z0-9_.-], got '" + material + "'");
        }
    }
}
