package com.craisinlord.fightclub.combat;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public record SpearTimeline(int raise, int swayEnd, int lowerEnd, int done) {
    private record Tier(String material, float damage, int dismount, int knockback, int finish) {}

    private static final List<Tier> TIERS = List.of(
            new Tier("wood", 4.0F, 100, 200, 300),
            new Tier("stone", 4.6F, 90, 180, 275),
            new Tier("gold", 4.4F, 70, 170, 275),
            new Tier("copper", 5.0F, 80, 165, 250),
            new Tier("iron", 5.6F, 50, 135, 225),
            new Tier("diamond", 6.8F, 60, 130, 200),
            new Tier("netherite", 7.6F, 50, 110, 175));

    public static SpearTimeline of(ItemStack stack) {
        return of(stack.getItem());
    }

    public static SpearTimeline of(Item item) {
        int raise = CombatChargeState.chargeTicksFor(item);
        Tier tier = tier(item);
        return new SpearTimeline(raise, raise + tier.dismount(), raise + tier.knockback(), raise + tier.finish());
    }

    public static boolean spent(Item item, long heldTicks) {
        return CombatChargeState.isSpearWeapon(item) && heldTicks > of(item).lowerEnd();
    }

    public static boolean wood(Item item) {
        return "wood".equals(CombatWeapons.material(item));
    }

    private static Tier tier(Item item) {
        String material = CombatWeapons.material(item);
        for (Tier tier : TIERS) if (tier.material().equals(material)) return tier;
        WeaponSpec spec = WeaponSpecs.getOrLoadBuiltin(material, "spear");
        float damage = spec == null ? 5.6F : spec.attackDamage();
        Tier best = TIERS.get(4);
        for (Tier tier : TIERS) {
            if (Math.abs(tier.damage() - damage) < Math.abs(best.damage() - damage)) best = tier;
        }
        return best;
    }
}
