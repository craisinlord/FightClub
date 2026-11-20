package com.craisinlord.fightclub.combat;

import com.craisinlord.fightclub.compat.BetterCombatCompat;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

import java.util.function.BiConsumer;

/**
 * Main-hand attribute modifiers taken live from the weapon specs, so datapack edits change the
 * actual weapon (and its tooltip) without a restart.
 *
 * <p>26.x differs from 1.21.1: Fight Club weapons already get their damage and speed from the
 * spec when they're registered (CombatWeaponItem bakes them into the item's attribute
 * component), so only reach is added here. Vanilla weapons are left alone: the vanilla spears
 * have their own attack range component, and the other vanilla weapons keep vanilla reach.
 */
public final class CombatWeaponStats {
    private static final Identifier REACH_ID = Identifier.fromNamespaceAndPath("fight_club", "weapon_reach");
    /** Vanilla player default the spec reach is measured against. */
    private static final double BASE_REACH = 3.0;

    private CombatWeaponStats() {}

    public static void forEachMainHand(ItemStack stack, BiConsumer<Holder<Attribute>, AttributeModifier> action) {
        if (stack.isEmpty() || !(stack.getItem() instanceof CombatWeaponItem)) return;
        WeaponSpec spec = CombatTooltips.specFor(stack);
        if (spec == null) return;
        // With Better Combat installed, it applies the same reach through our weapon_attributes
        // files (range_bonus); adding it here too would double it.
        double reachBonus = spec.reach() - BASE_REACH;
        if (!BetterCombatCompat.isLoaded() && Math.abs(reachBonus) > 0.001) {
            action.accept(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(REACH_ID,
                    reachBonus, AttributeModifier.Operation.ADD_VALUE));
        }
    }
}
