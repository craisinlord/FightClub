package com.craisinlord.fightclub.neoforge;

import com.craisinlord.fightclub.combat.CombatVanillaRules;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import com.craisinlord.fightclub.combat.CombatWeaponItem;

public final class FightClubNeoForgeEvents {
    private static final ThreadLocal<Boolean> REDUCED_DAMAGE = ThreadLocal.withInitial(() -> false);

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        if (event.getHand() != InteractionHand.MAIN_HAND || !(stack.getItem() instanceof CombatWeaponItem weapon)
                || !weapon.combatWeapon().equals("sword")) return;
        if (com.craisinlord.fightclub.combat.CombatWeapons.isShield(event.getEntity().getOffhandItem())) return;
        event.getEntity().startUsingItem(event.getHand());
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onDatapackSync(net.neoforged.neoforge.event.OnDatapackSyncEvent event) {
        var payload = com.craisinlord.fightclub.combat.WeaponItemsPayload.current();
        event.getRelevantPlayers().forEach(player -> net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload));
    }

    @SubscribeEvent
    public void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (!event.getEntity().level().isClientSide()) com.craisinlord.fightclub.combat.CombatChargeState.tick(event.getEntity());
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        CombatVanillaRules.applyLongswordRules(event.getEntity());
        if (CombatVanillaRules.shieldBash(event.getEntity(), event.getTarget())) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onDamage(LivingDamageEvent.Pre event) {
        if (REDUCED_DAMAGE.get() || !(event.getEntity() instanceof net.minecraft.world.entity.player.Player player)
                || !(event.getSource().getDirectEntity() instanceof net.minecraft.world.entity.LivingEntity attacker)
                || !CombatVanillaRules.swordBlocks(player, attacker)) return;
        event.setNewDamage(event.getNewDamage() * CombatVanillaRules.swordBlockDamageMultiplier(player.getUseItem()));
        CombatVanillaRules.playSwordBlockEffects(player, attacker);
    }
}
