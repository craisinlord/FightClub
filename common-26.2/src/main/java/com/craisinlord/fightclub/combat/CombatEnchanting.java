package com.craisinlord.fightclub.combat;

import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.effects.EnchantmentAttributeEffect;

public final class CombatEnchanting {
    private static ItemStack sword;
    private static ItemStack spear;

    private CombatEnchanting() {}

    public static boolean supports(Enchantment enchantment, ItemStack stack) {
        if (loyalty(enchantment, stack)) return true;
        ItemStack reference = reference(enchantment, stack);
        return reference != null && enchantment.isSupportedItem(reference);
    }

    public static boolean primary(Enchantment enchantment, ItemStack stack) {
        if (loyalty(enchantment, stack)) return true;
        ItemStack reference = reference(enchantment, stack);
        return reference != null && enchantment.isPrimaryItem(reference);
    }

    private static boolean loyalty(Enchantment enchantment, ItemStack stack) {
        if (!enchantment.effects().has(EnchantmentEffectComponents.TRIDENT_RETURN_ACCELERATION)) return false;
        String weapon = CombatWeapons.weapon(stack.getItem());
        return weapon != null && CombatWeaponItem.canThrow(CombatWeapons.material(stack.getItem()), weapon);
    }

    private static ItemStack reference(Enchantment enchantment, ItemStack stack) {
        if (!(stack.getItem() instanceof CombatWeaponItem weapon)) return null;
        String kind = weapon.combatWeapon();
        if (kind.equals("spear")) {
            if (spear == null) spear = new ItemStack(Items.IRON_SPEAR);
            return spear;
        }
        if (!kind.equals("sword") && !kind.equals("longsword") && sweeping(enchantment)) return null;
        if (sword == null) sword = new ItemStack(Items.IRON_SWORD);
        return sword;
    }

    private static boolean sweeping(Enchantment enchantment) {
        for (EnchantmentAttributeEffect effect : enchantment.getEffects(EnchantmentEffectComponents.ATTRIBUTES)) {
            if (effect.attribute().value() == Attributes.SWEEPING_DAMAGE_RATIO.value()) return true;
        }
        return false;
    }
}
