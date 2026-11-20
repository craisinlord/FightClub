package com.craisinlord.fightclub.client;

import com.craisinlord.fightclub.CombatConfig;
import com.craisinlord.fightclub.combat.CombatActionPayload;
import com.craisinlord.fightclub.combat.CombatChargeState;
import com.craisinlord.fightclub.combat.CombatSounds;
import com.craisinlord.fightclub.combat.LongswordSpin;
import com.craisinlord.fightclub.compat.BetterCombatCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

import java.util.function.Consumer;

/**
 * Client side of the longsword spin (charge cues, charge indicator, third-person body turn) and
 * of air swings: a longsword or mace swing that hits nothing is reported to the server, which
 * runs the longsword's air sweep or ends the mace hit streak.
 */
public final class LongswordSpinClient {
    private static boolean wasCharging;
    private static boolean readyPlayed;

    private LongswordSpinClient() {}

    /** The local player is holding right-click to charge a longsword spin. */
    public static boolean isCharging(Player player) {
        return player.isUsingItem() && player.getUsedItemHand() == InteractionHand.MAIN_HAND
                && LongswordSpin.isLongsword(player.getUseItem());
    }

    /** 0..1 spin charge progress for the local player. */
    public static float progress(Minecraft client, float partialTick) {
        LocalPlayer player = client.player;
        if (player == null || !isCharging(player)) return 0.0F;
        return Math.min(1.0F, (player.getTicksUsingItem() + partialTick) / LongswordSpin.chargeTicks(player.getUseItem()));
    }

    /** Called once per client tick from CombatInputClient. */
    static void tick(LocalPlayer player) {
        boolean charging = isCharging(player);
        if (charging && !wasCharging) {
            readyPlayed = false;
            CombatClientPoses.play(player, CombatSounds.CHARGE_START);
        }
        if (charging && !readyPlayed && player.getTicksUsingItem() >= LongswordSpin.chargeTicks(player.getUseItem())) {
            readyPlayed = true;
            CombatClientPoses.play(player, CombatSounds.CHARGE_READY);
        }
        wasCharging = charging;
    }

    /**
     * Minecraft.startAttack hit nothing (called right before vanilla resets the attack timer, so the
     * server can still read its own). Only longswords (air sweep, without Better Combat) and maces
     * (hit streak) care.
     */
    public static void onMissSwing(Consumer<CustomPacketPayload> sender, LocalPlayer player) {
        boolean longsword = LongswordSpin.isLongsword(player.getMainHandItem()) && !BetterCombatCompat.isLoaded()
                && CombatConfig.enabled(CombatConfig.DATA_SWEEPS);
        boolean mace = player.getMainHandItem().is(Items.MACE) && CombatConfig.enabled(CombatConfig.MACE_HIT_STREAK);
        if (longsword || mace) sender.accept(new CombatActionPayload(CombatChargeState.MISS_SWING));
    }

    /**
     * Third person: degrees to add to the body yaw while a spin plays (the whole model turns once;
     * the camera never moves). Right-handers turn left, so the blade cuts right to left.
     */
    private static final float SPIN_TURN_START = 0.12F, SPIN_TURN_END = 0.85F;

    public static float bodySpin(Player player, float partialTick) {
        if (!CombatConfig.enabled(CombatConfig.ANIMATIONS)) return 0.0F;
        CombatClientPoses.View view = CombatClientPoses.view(player, partialTick);
        if (view == null || view.spin() <= 0.0F) return 0.0F;
        // The turn starts once the wind-up is drawn and eases out before the recovery
        // (see the longsword_spin keys in poses.json).
        float turn = CombatClientPoses.smooth((view.spin() - SPIN_TURN_START) / (SPIN_TURN_END - SPIN_TURN_START));
        return (player.getMainArm() == HumanoidArm.RIGHT ? -360.0F : 360.0F) * turn;
    }
}
