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

public final class LongswordSpinClient {
    private static boolean wasCharging;
    private static boolean readyPlayed;
    private static boolean swingHitEntity;

    private LongswordSpinClient() {}

    public static boolean isCharging(Player player) {
        return player.isUsingItem() && player.getUsedItemHand() == InteractionHand.MAIN_HAND
                && LongswordSpin.isLongsword(player.getUseItem());
    }

    public static float progress(Minecraft client, float partialTick) {
        LocalPlayer player = client.player;
        if (player == null || !isCharging(player)) return 0.0F;
        return Math.min(1.0F, (player.getTicksUsingItem() + partialTick) / LongswordSpin.chargeTicks(player.getUseItem()));
    }

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

    public static void onMissSwing(Consumer<CustomPacketPayload> sender, LocalPlayer player) {
        boolean mace = player.getMainHandItem().is(Items.MACE) && CombatConfig.enabled(CombatConfig.MACE_HIT_STREAK);
        if (mace) sender.accept(new CombatActionPayload(CombatChargeState.MISS_SWING));
    }

    public static boolean attackNotReady(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || BetterCombatCompat.isLoaded() || !LongswordSpin.isLongsword(player.getMainHandItem())) return false;
        if (client.hitResult instanceof net.minecraft.world.phys.BlockHitResult block && client.level != null
                && !client.level.getBlockState(block.getBlockPos()).isAir()) return false;
        return player.getAttackStrengthScale(0.0F) < 1.0F;
    }

    public static void onStartAttack() {
        swingHitEntity = false;
    }

    public static void onAttackEntity() {
        swingHitEntity = true;
    }

    public static void onSwing(Consumer<CustomPacketPayload> sender, LocalPlayer player) {
        if (swingHitEntity) return;
        if (LongswordSpin.isLongsword(player.getMainHandItem()) && !BetterCombatCompat.isLoaded()
                && CombatConfig.enabled(CombatConfig.DATA_SWEEPS)) {
            sender.accept(new CombatActionPayload(CombatChargeState.LONGSWORD_SWING));
        }
    }

    private static final float SPIN_TURN_START = 0.12F, SPIN_TURN_END = 0.85F;

    public static float bodySpin(Player player, float partialTick) {
        if (!CombatConfig.enabled(CombatConfig.ANIMATIONS)) return 0.0F;
        CombatClientPoses.View view = CombatClientPoses.view(player, partialTick);
        if (view == null || view.spin() <= 0.0F) return 0.0F;
        float turn = CombatClientPoses.smooth((view.spin() - SPIN_TURN_START) / (SPIN_TURN_END - SPIN_TURN_START));
        return (player.getMainArm() == HumanoidArm.RIGHT ? -360.0F : 360.0F) * turn;
    }
}
