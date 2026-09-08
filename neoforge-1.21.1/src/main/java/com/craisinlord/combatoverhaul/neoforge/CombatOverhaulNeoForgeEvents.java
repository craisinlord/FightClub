package com.craisinlord.combatoverhaul.neoforge;

import com.craisinlord.combatoverhaul.combat.CombatVanillaRules;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import com.craisinlord.combatoverhaul.combat.CombatWeaponItem;

public final class CombatOverhaulNeoForgeEvents {
    private static final ThreadLocal<Boolean> REDUCED_DAMAGE = ThreadLocal.withInitial(() -> false);

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        if (event.getHand() != InteractionHand.MAIN_HAND || stack.getItem() instanceof CombatWeaponItem
                || !(stack.getItem() instanceof SwordItem)) return;
        if (event.getEntity().getOffhandItem().is(Items.SHIELD)) return;
        event.getEntity().startUsingItem(event.getHand());
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        CombatVanillaRules.applyLongswordRules(event.getEntity());
        if (CombatVanillaRules.shieldBash(event.getEntity(), event.getTarget())
                || CombatVanillaRules.maceSmash(event.getEntity(), event.getTarget())) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onDamage(LivingDamageEvent.Pre event) {
        if (REDUCED_DAMAGE.get() || !(event.getEntity() instanceof net.minecraft.world.entity.player.Player player)
                || !(event.getSource().getDirectEntity() instanceof net.minecraft.world.entity.LivingEntity attacker)
                || !CombatVanillaRules.swordBlocks(player, attacker)) return;
        event.setNewDamage(event.getNewDamage() * CombatVanillaRules.swordBlockDamageMultiplier(player.getUseItem()));
    }
}
