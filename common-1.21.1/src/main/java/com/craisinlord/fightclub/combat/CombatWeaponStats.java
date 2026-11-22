package com.craisinlord.fightclub.combat;

import com.craisinlord.fightclub.compat.BetterCombatCompat;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public final class CombatWeaponStats {
    private static final ResourceLocation REACH_ID = ResourceLocation.fromNamespaceAndPath("fight_club", "weapon_reach");
    private static final double BASE_DAMAGE = 1.0, BASE_SPEED = 4.0, BASE_REACH = 3.0;
    private static final Map<WeaponSpec, ItemAttributeModifiers> DEFAULTS = new ConcurrentHashMap<>();

    private CombatWeaponStats() {}

    public static ItemAttributeModifiers defaultModifiers(Item item) {
        WeaponSpec spec = CombatWeapons.spec(item);
        if (spec == null) return ItemAttributeModifiers.EMPTY;
        return DEFAULTS.computeIfAbsent(spec, s -> {
            ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
            weaponModifiers(s, (attribute, modifier) -> builder.add(attribute, modifier, EquipmentSlotGroup.MAINHAND));
            return builder.build();
        });
    }

    public static void forEachMainHand(ItemStack stack, BiConsumer<Holder<Attribute>, AttributeModifier> action) {
        if (stack.isEmpty() || stack.getItem() instanceof CombatWeaponItem) return;
        WeaponSpec spec = CombatTooltips.specFor(stack);
        if (spec == null) return;
        if (CombatWeapons.isCombatWeapon(stack.getItem()) && CombatWeapons.usesSpecStats(stack.getItem())) {
            weaponModifiers(spec, action);
        } else {
            reachModifier(spec, action);
        }
    }

    private static void weaponModifiers(WeaponSpec spec, BiConsumer<Holder<Attribute>, AttributeModifier> action) {
        action.accept(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID,
                spec.attackDamage() - BASE_DAMAGE, AttributeModifier.Operation.ADD_VALUE));
        action.accept(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID,
                spec.attackSpeed() - BASE_SPEED, AttributeModifier.Operation.ADD_VALUE));
        reachModifier(spec, action);
    }

    private static void reachModifier(WeaponSpec spec, BiConsumer<Holder<Attribute>, AttributeModifier> action) {
        double reachBonus = spec.reach() - BASE_REACH;
        if (!BetterCombatCompat.isLoaded() && Math.abs(reachBonus) > 0.001 && !"shield".equals(spec.weapon())) {
            action.accept(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(REACH_ID,
                    reachBonus, AttributeModifier.Operation.ADD_VALUE));
        }
    }
}
