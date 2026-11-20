package com.craisinlord.fightclub.combat;

import com.craisinlord.fightclub.compat.BetterCombatCompat;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.BiConsumer;

/**
 * Main-hand attribute modifiers taken live from the weapon specs, so datapack edits change the
 * actual weapon (and its tooltip) without a restart. Fight Club weapons get damage, speed
 * and reach exactly as written in their spec (no tier bonus on top); vanilla weapons keep their
 * vanilla damage and speed and only get reach where their spec differs from vanilla.
 */
public final class CombatWeaponStats {
    private static final ResourceLocation REACH_ID = ResourceLocation.fromNamespaceAndPath("fight_club", "weapon_reach");
    /** Vanilla player defaults the spec values are measured against. */
    private static final double BASE_DAMAGE = 1.0, BASE_SPEED = 4.0, BASE_REACH = 3.0;

    private CombatWeaponStats() {}

    public static void forEachMainHand(ItemStack stack, BiConsumer<Holder<Attribute>, AttributeModifier> action) {
        if (stack.isEmpty()) return;
        WeaponSpec spec = CombatTooltips.specFor(stack);
        if (spec == null) return;
        if (CombatWeapons.isCombatWeapon(stack.getItem())) {
            action.accept(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID,
                    spec.attackDamage() - BASE_DAMAGE, AttributeModifier.Operation.ADD_VALUE));
            action.accept(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID,
                    spec.attackSpeed() - BASE_SPEED, AttributeModifier.Operation.ADD_VALUE));
        }
        // With Better Combat installed, it applies the same reach through our weapon_attributes
        // files (range_bonus); adding it here too would double it.
        double reachBonus = spec.reach() - BASE_REACH;
        if (!BetterCombatCompat.isLoaded() && Math.abs(reachBonus) > 0.001 && !"shield".equals(spec.weapon())) {
            action.accept(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(REACH_ID,
                    reachBonus, AttributeModifier.Operation.ADD_VALUE));
        }
    }
}
