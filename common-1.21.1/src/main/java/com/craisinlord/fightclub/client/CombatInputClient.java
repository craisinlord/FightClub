package com.craisinlord.fightclub.client;

import com.craisinlord.fightclub.CombatConfig;
import com.craisinlord.fightclub.combat.CombatActionPayload;
import com.craisinlord.fightclub.combat.CombatChargeState;
import com.craisinlord.fightclub.combat.CombatSounds;
import com.craisinlord.fightclub.combat.CombatVanillaRules;
import com.craisinlord.fightclub.combat.CombatWeapons;
import com.craisinlord.fightclub.combat.WeaponSpec;
import com.craisinlord.fightclub.combat.WeaponSpecs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

public final class CombatInputClient {
    public static final int HOLD_THRESHOLD_TICKS = 5;

    private static Consumer<CustomPacketPayload> sender = payload -> {};
    private static int holdTicks;
    private static boolean pendingTap;
    private static Item pendingItem;
    private static boolean charging;
    private static Item chargeItem;
    private static long chargeStartTick;
    private static boolean wasDown;
    private static boolean readyPlayed;
    private static net.minecraft.world.entity.LivingEntity lockTarget;
    private static boolean waitForRelease;

    static void serverEndedCharge() {
        if (!charging) return;
        charging = false;
        lockTarget = null;
        chargeItem = null;
        holdTicks = 0;
        waitForRelease = true;
    }

    private static boolean thrustCharge(ItemStack stack) {
        return CombatConfig.enabled(CombatConfig.CHARGED_ATTACKS) && CombatChargeState.isChargeWeapon(stack.getItem())
                && !CombatChargeState.isDagger(stack.getItem());
    }

    public static void beforeKeybinds(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || !thrustCharge(player.getMainHandItem())) return;
        var attackKey = client.options.keyAttack;
        if (charging || pendingTap || waitForRelease) {
            while (attackKey.consumeClick()) {}
            return;
        }
        if (player.isUsingItem() || player.isSpectator() || PounceClient.active()) return;
        if (client.hitResult != null && client.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) return;
        boolean clicked = false;
        while (attackKey.consumeClick()) clicked = true;
        if (!clicked) return;
        clearTapBuffer();
        pendingTap = true;
        pendingItem = player.getMainHandItem().getItem();
        holdTicks = 0;
    }

    private static void endPending() {
        pendingTap = false;
        pendingItem = null;
        holdTicks = 0;
    }

    public static final int TAP_BUFFER_TICKS = 6;
    private static int tapBufferTicks;
    private static Item tapItem;
    private static boolean attackWentThrough;

    public static void markAttackWentThrough() { attackWentThrough = true; }

    private static void clearTapBuffer() {
        tapBufferTicks = 0;
        tapItem = null;
    }

    private static void queueTap(Minecraft client, LocalPlayer player) {
        tapItem = player.getMainHandItem().getItem();
        tapBufferTicks = TAP_BUFFER_TICKS;
        tickTapBuffer(client, player);
    }

    private static void tickTapBuffer(Minecraft client, LocalPlayer player) {
        if (tapBufferTicks <= 0) return;
        if (client.screen != null || player.getMainHandItem().getItem() != tapItem || player.isUsingItem() || player.isSpectator()) {
            clearTapBuffer();
        } else if (tryTap(client) || --tapBufferTicks <= 0) {
            clearTapBuffer();
        }
    }

    private static boolean tryTap(Minecraft client) {
        attackWentThrough = false;
        int bcStarts = com.craisinlord.fightclub.compat.BetterCombatCompat.clientAttackStarts();
        ((com.craisinlord.fightclub.mixin.MinecraftAccessor) client).fightClub$startAttack();
        return attackWentThrough || com.craisinlord.fightclub.compat.BetterCombatCompat.clientAttackStarts() != bcStarts;
    }
    private static long bashReadyTick;
    private static int bashLockTicks;

    private CombatInputClient() {}

    private static WeaponSpec shieldSpec() {
        LocalPlayer player = Minecraft.getInstance().player;
        return CombatWeapons.shieldSpec(player == null ? ItemStack.EMPTY : player.getUseItem());
    }

    private static int bashCooldownTicks() {
        WeaponSpec spec = shieldSpec();
        return spec == null || spec.recoveryTicks() <= 0 ? 25 : spec.recoveryTicks();
    }

    private static int bashHitCooldownTicks() {
        WeaponSpec spec = shieldSpec();
        return spec == null ? WeaponSpec.DEFAULT_BASH_HIT_COOLDOWN : spec.bashHitCooldownTicks();
    }

    public static void bashLanded() {
        int extra = bashHitCooldownTicks() - bashLockTicks;
        if (extra > 0) {
            bashReadyTick += extra;
            bashLockTicks += extra;
        }
    }

    public static float bashReadiness(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null || bashLockTicks <= 0 || !player.isUsingItem()
                || !CombatVanillaRules.isShield(player.getUseItem())) return 1.0F;
        long left = bashReadyTick - client.level.getGameTime();
        return left <= 0 ? 1.0F : 1.0F - (float) left / bashLockTicks;
    }

    public static void onMissSwing(LocalPlayer player) {
        LongswordSpinClient.onMissSwing(sender, player);
    }

    public static void onSwing(LocalPlayer player) {
        LongswordSpinClient.onSwing(sender, player);
    }

    public static void setSender(Consumer<CustomPacketPayload> packetSender) {
        sender = packetSender;
    }

    public static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            reset();
            return;
        }
        ItemStack stack = player.getMainHandItem();
        boolean chargeWeapon = CombatConfig.enabled(CombatConfig.CHARGED_ATTACKS) && CombatChargeState.isChargeWeapon(stack.getItem());
        boolean down = client.screen == null && client.options.keyAttack.isDown();
        boolean pressed = down && !wasDown;
        wasDown = down;

        if (pressed && CombatConfig.enabled(CombatConfig.SHIELD_BASH) && player.isUsingItem() && CombatVanillaRules.isShield(player.getUseItem())
                && client.level.getGameTime() >= bashReadyTick) {
            bashLockTicks = bashCooldownTicks();
            bashReadyTick = client.level.getGameTime() + bashLockTicks;
            sender.accept(new CombatActionPayload(CombatChargeState.BASH));
            CombatClientPoses.playBash(player);
        }

        if (!charging && !pendingTap) tickTapBuffer(client, player);

        if (charging) {
            boolean full = chargeProgress(client, 0.0F) >= 1.0F;
            boolean dagger = CombatChargeState.isDagger(stack.getItem());
            lockTarget = dagger && stack.getItem() == chargeItem ? pounceTarget(player, stack) : null;
            boolean ready = full && (!dagger || lockTarget != null);
            if (ready && !readyPlayed) {
                readyPlayed = true;
                CombatClientPoses.play(player, CombatSounds.CHARGE_READY);
            } else if (!ready && dagger) {
                readyPlayed = false;
            }
            if (!down || stack.getItem() != chargeItem || player.isUsingItem() || player.isSpectator()) {
                boolean released = !down && stack.getItem() == chargeItem && full;
                if (released && CombatChargeState.isDagger(stack.getItem())) {
                    net.minecraft.world.entity.LivingEntity target = lockTarget;
                    stopCharging(target == null ? -1 : target.getId());
                    if (target != null) PounceClient.start(player, target);
                } else {
                    stopCharging(-1);
                    if (released) player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                }
            }
        } else if (waitForRelease) {
            if (!down) {
                waitForRelease = false;
                com.craisinlord.fightclub.compat.BetterCombatCompat.setLocalAttacksPaused(player, false);
            }
            holdTicks = 0;
        } else if (pendingTap) {
            if (client.screen != null || stack.getItem() != pendingItem || player.isUsingItem() || player.isSpectator()) {
                endPending();
            } else if (!down) {
                endPending();
                queueTap(client, player);
            } else if (++holdTicks >= HOLD_THRESHOLD_TICKS && !player.getCooldowns().isOnCooldown(stack.getItem())) {
                endPending();
                startCharging(client, player, stack);
            }
        } else if (down && chargeWeapon && CombatChargeState.isDagger(stack.getItem())
                && !PounceClient.active() && !player.isUsingItem() && !player.isSpectator()) {
            if (++holdTicks >= HOLD_THRESHOLD_TICKS) startCharging(client, player, stack);
        } else {
            holdTicks = 0;
        }
        PounceClient.tick(player);
        LongswordSpinClient.tick(player);
        CombatClientPoses.tick(client);
    }

    private static net.minecraft.world.entity.LivingEntity pounceTarget(LocalPlayer player, ItemStack stack) {
        if (player.isPassenger() || player.isFallFlying() || player.getCooldowns().isOnCooldown(stack.getItem())
                || !CombatWeapons.is(stack.getItem(), "dagger")) return null;
        WeaponSpec spec = WeaponSpecs.getOrLoadBuiltin(CombatWeapons.material(stack.getItem()), "dagger");
        return spec == null ? null : com.craisinlord.fightclub.combat.PounceTargeting.find(player, spec);
    }

    public static net.minecraft.world.entity.Entity outlinedTarget() {
        if (PounceClient.active()) return PounceClient.target();
        return charging ? lockTarget : null;
    }

    public static boolean pounceReady(Minecraft client) {
        return charging && lockTarget != null && chargeProgress(client, 0.0F) >= 1.0F;
    }

    public static boolean blocksAttack() { return isCharging() || pendingTap || waitForRelease || PounceClient.active(); }

    private static void startCharging(Minecraft client, LocalPlayer player, ItemStack stack) {
        charging = true;
        readyPlayed = false;
        holdTicks = 0;
        chargeItem = stack.getItem();
        chargeStartTick = client.level.getGameTime();
        clearTapBuffer();
        if (!CombatChargeState.isDagger(chargeItem)) com.craisinlord.fightclub.compat.BetterCombatCompat.setLocalAttacksPaused(player, true);
        sender.accept(new CombatActionPayload(CombatChargeState.START));
        CombatClientPoses.play(player, CombatSounds.CHARGE_START);
    }

    private static void stopCharging(int pounceTarget) {
        boolean thrust = chargeItem != null && !CombatChargeState.isDagger(chargeItem);
        charging = false;
        lockTarget = null;
        chargeItem = null;
        holdTicks = 0;
        Minecraft client = Minecraft.getInstance();
        if (thrust && client.player != null) {
            boolean stillHeld = client.screen == null && client.options.keyAttack.isDown();
            if (stillHeld) waitForRelease = true;
            else com.craisinlord.fightclub.compat.BetterCombatCompat.setLocalAttacksPaused(client.player, false);
        }
        sender.accept(new CombatActionPayload(CombatChargeState.RELEASE, pounceTarget));
    }

    private static void reset() {
        endPending();
        clearTapBuffer();
        waitForRelease = false;
        wasDown = false;
        bashReadyTick = 0;
        bashLockTicks = 0;
        charging = false;
        chargeItem = null;
        holdTicks = 0;
        lockTarget = null;
        PounceClient.reset();
        CombatClientPoses.clear();
    }

    static float debugProgress = -1.0F;

    public static boolean isCharging() { return charging || debugProgress >= 0.0F; }

    public static float chargeHeldTicks(Minecraft client, float partialTick) {
        if (debugProgress >= 0.0F) {
            return client.player == null ? 0.0F : debugProgress * CombatChargeState.chargeTicksFor(client.player.getMainHandItem());
        }
        if (!charging || client.level == null) return -1.0F;
        return (client.level.getGameTime() - chargeStartTick) + partialTick;
    }

    public static float chargeProgress(Minecraft client, float partialTick) {
        if (debugProgress >= 0.0F) return debugProgress;
        if (!charging || client.level == null || client.player == null) return 0.0F;
        float held = (client.level.getGameTime() - chargeStartTick) + partialTick;
        return Math.min(1.0F, held / CombatChargeState.chargeTicksFor(client.player.getMainHandItem()));
    }
}
