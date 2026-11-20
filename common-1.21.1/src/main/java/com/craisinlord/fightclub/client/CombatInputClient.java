package com.craisinlord.fightclub.client;

import com.craisinlord.fightclub.CombatConfig;
import com.craisinlord.fightclub.combat.CombatActionPayload;
import com.craisinlord.fightclub.combat.CombatChargeState;
import com.craisinlord.fightclub.combat.CombatSounds;
import com.craisinlord.fightclub.combat.CombatVanillaRules;
import com.craisinlord.fightclub.combat.WeaponSpec;
import com.craisinlord.fightclub.combat.WeaponSpecs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

/**
 * Hold-left-click charge input.
 *
 * <p>Spear and trident: the click is claimed before vanilla or Better Combat see it. Letting go
 * before {@link #HOLD_THRESHOLD_TICKS} is a tap and attacks normally on release; holding past it
 * starts the charge. Either way only one thing happens per press: no swing slips in before the
 * charge, and Better Combat's hold-to-attack is paused locally for the whole hold. Clicking on
 * a block is left alone so mining works as usual.
 *
 * <p>Dagger: a tap attacks immediately through vanilla; holding past the threshold starts the
 * pounce charge.
 *
 * <p>While charging, the Minecraft mixin stops vanilla from mining, re-attacking or starting a
 * right-click use.
 */
public final class CombatInputClient {
    /** About a quarter second; short enough to feel responsive, long enough that taps stay taps. */
    public static final int HOLD_THRESHOLD_TICKS = 5;

    private static Consumer<CustomPacketPayload> sender = payload -> {};
    private static int holdTicks;
    /** Spear/trident press claimed, not yet a tap (released) or a charge (held long enough). */
    private static boolean pendingTap;
    private static Item pendingItem;
    private static boolean charging;
    private static Item chargeItem;
    private static long chargeStartTick;
    private static boolean wasDown;
    private static boolean readyPlayed;
    /** While charging a dagger: the mob a release would pounce on right now, or null. */
    private static net.minecraft.world.entity.LivingEntity lockTarget;
    /** After the server ends a charge (running charge hit), wait for the key to be let go. */
    private static boolean waitForRelease;

    /** The server ended our charge on its own: drop it without re-sending a release. */
    static void serverEndedCharge() {
        if (!charging) return;
        charging = false;
        lockTarget = null;
        chargeItem = null;
        holdTicks = 0;
        waitForRelease = true;
        // Keep Better Combat paused until the key is let go, so the held key can't turn into swings.
    }

    /** Spear or trident with charged attacks on: these use the claimed tap-or-hold input. */
    private static boolean thrustCharge(ItemStack stack) {
        return CombatConfig.enabled(CombatConfig.CHARGED_ATTACKS) && CombatChargeState.isChargeWeapon(stack.getItem())
                && !CombatChargeState.isDagger(stack.getItem());
    }

    /**
     * Head of {@code Minecraft.handleKeybinds}, before vanilla's startAttack/continueAttack and
     * Better Combat's hooks on them run. Claims spear/trident attack clicks for the tap-or-hold input.
     */
    public static void beforeKeybinds(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || !thrustCharge(player.getMainHandItem())) return;
        var attackKey = client.options.keyAttack;
        if (charging || pendingTap || waitForRelease) {
            // Mid-hold: extra clicks mean nothing, and must not reach vanilla or Better Combat.
            while (attackKey.consumeClick()) {}
            return;
        }
        if (player.isUsingItem() || player.isSpectator() || PounceClient.active()) return;
        // Mining with a spear in hand stays vanilla.
        if (client.hitResult != null && client.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) return;
        boolean clicked = false;
        while (attackKey.consumeClick()) clicked = true;
        if (!clicked) return;
        // A fresh press replaces a tap still waiting in the buffer: it makes its own tap or charge.
        clearTapBuffer();
        pendingTap = true;
        pendingItem = player.getMainHandItem().getItem();
        holdTicks = 0;
        // Better Combat is not paused here: that would cancel a swing still in flight from the last
        // tap. Its hold-to-attack is kept out by MinecraftInputMixin skipping continueAttack instead.
    }

    private static void endPending() {
        pendingTap = false;
        pendingItem = null;
        holdTicks = 0;
    }

    /**
     * How long a tap that comes in before the weapon is ready waits to go through. Vanilla and
     * Better Combat both drop an early click without a sound or swing, which reads as a missed
     * input; within this window it lands the moment the weapon is ready instead.
     */
    public static final int TAP_BUFFER_TICKS = 6;
    private static int tapBufferTicks;
    private static Item tapItem;
    private static boolean attackWentThrough;

    /** MinecraftInputMixin: vanilla's startAttack got as far as swinging. */
    public static void markAttackWentThrough() { attackWentThrough = true; }

    private static void clearTapBuffer() {
        tapBufferTicks = 0;
        tapItem = null;
    }

    /** A spear/trident tap was released: attack now, or as soon as the weapon allows. */
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

    /**
     * Fires the tap through vanilla's startAttack (and Better Combat's hook on it). True when it
     * went through: vanilla swung, or Better Combat started a swing. Either drops it silently
     * when the weapon isn't ready, with no side effects, so retrying next tick is safe.
     */
    private static boolean tryTap(Minecraft client) {
        attackWentThrough = false;
        int bcStarts = com.craisinlord.fightclub.compat.BetterCombatCompat.clientAttackStarts();
        ((com.craisinlord.fightclub.mixin.MinecraftAccessor) client).fightClub$startAttack();
        return attackWentThrough || com.craisinlord.fightclub.compat.BetterCombatCompat.clientAttackStarts() != bcStarts;
    }
    private static long bashReadyTick;
    /** Length of the current bash lock (whiff delay, or the hit cooldown once the server reports a hit). */
    private static int bashLockTicks;

    private CombatInputClient() {}

    private static int bashCooldownTicks() {
        WeaponSpec spec = WeaponSpecs.getOrLoadBuiltin("base", "shield");
        return spec == null || spec.recoveryTicks() <= 0 ? 25 : spec.recoveryTicks();
    }

    private static int bashHitCooldownTicks() {
        WeaponSpec spec = WeaponSpecs.getOrLoadBuiltin("base", "shield");
        return spec == null ? WeaponSpec.DEFAULT_BASH_HIT_COOLDOWN : spec.bashHitCooldownTicks();
    }

    /** The server reports our last bash hit something: bashing stays locked for the longer hit cooldown. */
    public static void bashLanded() {
        int extra = bashHitCooldownTicks() - bashLockTicks;
        if (extra > 0) {
            bashReadyTick += extra;
            bashLockTicks += extra;
        }
    }

    /** How far the bash lock has run (0..1) while a shield is raised; 1 when a bash is ready or no shield is up. */
    public static float bashReadiness(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null || bashLockTicks <= 0 || !player.isUsingItem()
                || !CombatVanillaRules.isShield(player.getUseItem())) return 1.0F;
        long left = bashReadyTick - client.level.getGameTime();
        return left <= 0 ? 1.0F : 1.0F - (float) left / bashLockTicks;
    }

    /** A swing that hit nothing (see MinecraftInputMixin): longsword air sweep / mace streak miss. */
    public static void onMissSwing(LocalPlayer player) {
        LongswordSpinClient.onMissSwing(sender, player);
    }

    /** Each loader supplies its client-to-server send function at client init. */
    public static void setSender(Consumer<CustomPacketPayload> packetSender) {
        sender = packetSender;
    }

    /** Called once per client tick (end of tick) by each loader. */
    public static void tick(Minecraft client) {
        DevPoseGallery.tick(client);
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

        // Shield bash: vanilla swallows attack clicks while an item is in use, so watch for the press here.
        if (pressed && CombatConfig.enabled(CombatConfig.SHIELD_BASH) && player.isUsingItem() && CombatVanillaRules.isShield(player.getUseItem())
                && client.level.getGameTime() >= bashReadyTick) {
            // Assume a whiff; the server's BASH_HIT reply extends this to the hit cooldown.
            bashLockTicks = bashCooldownTicks();
            bashReadyTick = client.level.getGameTime() + bashLockTicks;
            sender.accept(new CombatActionPayload(CombatChargeState.BASH));
            CombatClientPoses.playBash(player);
        }

        // A spear/trident tap released before the weapon was ready: retry until it goes through.
        if (!charging && !pendingTap) tickTapBuffer(client, player);

        if (charging) {
            boolean full = chargeProgress(client, 0.0F) >= 1.0F;
            boolean dagger = CombatChargeState.isDagger(stack.getItem());
            lockTarget = dagger && stack.getItem() == chargeItem ? pounceTarget(player, stack) : null;
            // Daggers only sound "ready" while something is locked: the cue means a release will pounce.
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
                    // Dagger pounce: dash at the target nearest the crosshair. No target, no pounce.
                    net.minecraft.world.entity.LivingEntity target = lockTarget;
                    stopCharging(target == null ? -1 : target.getId());
                    if (target != null) PounceClient.start(player, target);
                } else {
                    stopCharging(-1);
                    // A full charge released on purpose strikes: show the jab locally (and to others via the swing packet).
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
            // Spear/trident press claimed in beforeKeybinds: decide tap or charge.
            if (client.screen != null || stack.getItem() != pendingItem || player.isUsingItem() || player.isSpectator()) {
                endPending();
            } else if (!down) {
                // A tap: attack now (or as soon as the weapon is ready), through vanilla and Better Combat.
                endPending();
                queueTap(client, player);
            } else if (++holdTicks >= HOLD_THRESHOLD_TICKS && !player.getCooldowns().isOnCooldown(stack.getItem())) {
                // Held: charge. (During recovery the hold just waits, and charges once recovery ends.)
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
                || !(stack.getItem() instanceof com.craisinlord.fightclub.combat.CombatWeaponItem weapon)) return null;
        WeaponSpec spec = WeaponSpecs.getOrLoadBuiltin(weapon.combatMaterial(), "dagger");
        return spec == null ? null : com.craisinlord.fightclub.combat.PounceTargeting.find(player, spec);
    }

    /** The mob to outline: the dagger's current lock while charging, or the pounce target mid-dash. */
    public static net.minecraft.world.entity.Entity outlinedTarget() {
        if (PounceClient.active()) return PounceClient.target();
        return charging ? lockTarget : null;
    }

    /** A full dagger charge with a mob locked: releasing now pounces. */
    public static boolean pounceReady(Minecraft client) {
        return charging && lockTarget != null && chargeProgress(client, 0.0F) >= 1.0F;
    }

    /** Charging, deciding tap-or-hold, or mid-pounce: a click must not start a separate attack or mine. */
    public static boolean blocksAttack() { return isCharging() || pendingTap || waitForRelease || PounceClient.active(); }

    private static void startCharging(Minecraft client, LocalPlayer player, ItemStack stack) {
        charging = true;
        readyPlayed = false;
        holdTicks = 0;
        chargeItem = stack.getItem();
        chargeStartTick = client.level.getGameTime();
        clearTapBuffer();
        // Spear/trident: pause Better Combat locally now; the server sets the same flag when START
        // arrives. (Daggers leave it to the server, which keeps it paused through a pounce dash.)
        if (!CombatChargeState.isDagger(chargeItem)) com.craisinlord.fightclub.compat.BetterCombatCompat.setLocalAttacksPaused(player, true);
        sender.accept(new CombatActionPayload(CombatChargeState.START));
        CombatClientPoses.play(player, CombatSounds.CHARGE_START);
    }

    private static void stopCharging(int pounceTarget) {
        // Spear/trident paused Better Combat locally (beforeKeybinds); daggers leave it to the
        // server, which keeps it paused through a pounce dash.
        boolean thrust = chargeItem != null && !CombatChargeState.isDagger(chargeItem);
        charging = false;
        lockTarget = null;
        chargeItem = null;
        holdTicks = 0;
        Minecraft client = Minecraft.getInstance();
        if (thrust && client.player != null) {
            boolean stillHeld = client.screen == null && client.options.keyAttack.isDown();
            // Ended with the key still down (weapon switched, item used): wait for a fresh press.
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

    /** Dev screenshot harness only ({@link DevPoseGallery}): fakes charge progress when >= 0. */
    static float debugProgress = -1.0F;

    public static boolean isCharging() { return charging || debugProgress >= 0.0F; }

    /** Ticks the current charge has been held (with partial tick), or -1 when not charging. */
    public static float chargeHeldTicks(Minecraft client, float partialTick) {
        if (debugProgress >= 0.0F) {
            return client.player == null ? 0.0F : debugProgress * CombatChargeState.chargeTicksFor(client.player.getMainHandItem());
        }
        if (!charging || client.level == null) return -1.0F;
        return (client.level.getGameTime() - chargeStartTick) + partialTick;
    }

    /** 0..1 progress toward a full charge, including the partial tick for smooth rendering. */
    public static float chargeProgress(Minecraft client, float partialTick) {
        if (debugProgress >= 0.0F) return debugProgress;
        if (!charging || client.level == null || client.player == null) return 0.0F;
        float held = (client.level.getGameTime() - chargeStartTick) + partialTick;
        return Math.min(1.0F, held / CombatChargeState.chargeTicksFor(client.player.getMainHandItem()));
    }
}
