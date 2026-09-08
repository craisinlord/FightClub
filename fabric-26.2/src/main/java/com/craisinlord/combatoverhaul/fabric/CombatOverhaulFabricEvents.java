package com.craisinlord.combatoverhaul.fabric;

import com.craisinlord.combatoverhaul.combat.CombatVanillaRules;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import com.craisinlord.combatoverhaul.combat.CombatWeaponItem;

public final class CombatOverhaulFabricEvents {
    private static final ThreadLocal<Boolean> REDUCED_DAMAGE = ThreadLocal.withInitial(() -> false);

    private CombatOverhaulFabricEvents() {}

    public static void register() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (hand == net.minecraft.world.InteractionHand.MAIN_HAND && stack.getItem() instanceof CombatWeaponItem weapon
                    && weapon.combatWeapon().equals("sword")
                    && player.getOffhandItem().is(Items.SHIELD)) return InteractionResult.PASS;
            if (hand == net.minecraft.world.InteractionHand.MAIN_HAND && stack.getItem() instanceof CombatWeaponItem weapon
                    && weapon.combatWeapon().equals("sword")) {
                player.startUsingItem(hand);
                return InteractionResult.CONSUME;
            }
            return InteractionResult.PASS;
        });
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            CombatVanillaRules.applyLongswordRules(player);
            if (CombatVanillaRules.shieldBash(player, entity) || CombatVanillaRules.maceSmash(player, entity)) {
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (REDUCED_DAMAGE.get() || !(entity instanceof net.minecraft.world.entity.player.Player player)) return true;
            if (!(source.getDirectEntity() instanceof net.minecraft.world.entity.LivingEntity attacker)
                    || !CombatVanillaRules.swordBlocks(player, attacker)) return true;
            REDUCED_DAMAGE.set(true);
            try {
                entity.hurt(source, amount * CombatVanillaRules.swordBlockDamageMultiplier(player.getUseItem()));
            } finally {
                REDUCED_DAMAGE.set(false);
            }
            return false;
        });
    }
}
