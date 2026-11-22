package com.craisinlord.fightclub.combat;

import com.craisinlord.fightclub.compat.BetterCombatCompat;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.BiConsumer;

public final class CombatWeaponStats {
    private static final Identifier REACH_ID = Identifier.fromNamespaceAndPath("fight_club", "weapon_reach");
    private static final double BASE_REACH = 3.0;

    private CombatWeaponStats() {}

    public static void forEachMainHand(ItemStack stack, BiConsumer<Holder<Attribute>, AttributeModifier> action) {
        if (stack.isEmpty() || !CombatWeapons.isCombatWeapon(stack.getItem())) return;
        WeaponSpec spec = CombatTooltips.specFor(stack);
        if (spec == null) return;
        if (!(stack.getItem() instanceof CombatWeaponItem) && CombatWeapons.usesSpecStats(stack.getItem())) {
            action.accept(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID,
                    spec.attackDamage() - 1.0, AttributeModifier.Operation.ADD_VALUE));
            action.accept(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID,
                    spec.attackSpeed() - 4.0, AttributeModifier.Operation.ADD_VALUE));
        }
        double reachBonus = spec.reach() - BASE_REACH;
        if (!BetterCombatCompat.isLoaded() && Math.abs(reachBonus) > 0.001) {
            action.accept(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(REACH_ID,
                    reachBonus, AttributeModifier.Operation.ADD_VALUE));
        }
    }
}
