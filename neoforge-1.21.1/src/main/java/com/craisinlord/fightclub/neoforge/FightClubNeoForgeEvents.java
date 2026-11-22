package com.craisinlord.fightclub.neoforge;

import com.craisinlord.fightclub.combat.CombatVanillaRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

public final class FightClubNeoForgeEvents {
    private static final ThreadLocal<Boolean> REDUCED_DAMAGE = ThreadLocal.withInitial(() -> false);

    @SubscribeEvent
    public void onCriticalHit(net.neoforged.neoforge.event.entity.player.CriticalHitEvent event) {
        if (CombatVanillaRules.suppressesCrits(event.getEntity())) event.setCriticalHit(false);
    }

    @SubscribeEvent
    public void onSweep(net.neoforged.neoforge.event.entity.player.SweepAttackEvent event) {
        if (CombatVanillaRules.blocksVanillaSweep(event.getEntity())) event.setSweeping(false);
    }

    @SubscribeEvent
    public void onDatapackSync(net.neoforged.neoforge.event.OnDatapackSyncEvent event) {
        var payload = com.craisinlord.fightclub.combat.WeaponSpecSyncPayload.current();
        event.getRelevantPlayers().forEach(player -> net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload));
    }

    @SubscribeEvent
    public void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (event.getEntity().level().isClientSide) return;
        CombatVanillaRules.applyLongswordRules(event.getEntity());
        com.craisinlord.fightclub.combat.CombatChargeState.tick(event.getEntity());
    }

    @SubscribeEvent
    public void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        CombatVanillaRules.clearPlayerState(event.getEntity());
    }

    @SubscribeEvent
    public void onRespawn(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) {
        CombatVanillaRules.clearPlayerState(event.getEntity());
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        CombatVanillaRules.beforeAttack(event.getEntity());
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
