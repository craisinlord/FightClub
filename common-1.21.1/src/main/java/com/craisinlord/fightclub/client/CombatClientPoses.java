package com.craisinlord.fightclub.client;

import com.craisinlord.fightclub.combat.CombatPosePayload;
import com.craisinlord.fightclub.combat.CombatSounds;
import com.craisinlord.fightclub.compat.BetterCombatCompat;
import com.craisinlord.fightclub.combat.CombatVanillaRules;
import com.craisinlord.fightclub.combat.WeaponKind;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Client-side animation state for every visible player: the held pose and its blend, plus a few
 * one-shot effects (sword block recoil, shield bash, longsword spin). Everything advances
 * once per tick and is interpolated by partial tick when rendering.
 */
public final class CombatClientPoses {
    private static final Map<Integer, Track> TRACKS = new HashMap<>();
    private static final Set<Integer> REMOTE_CHARGING = new HashSet<>();
    /** Game time each remote player's charge started (for the spear charge animation's timing). */
    private static final Map<Integer, Long> REMOTE_CHARGE_START = new HashMap<>();
    /** Reused every tick (client thread only) instead of allocating a new set. */
    private static final Set<Integer> TICK_IDS = new HashSet<>();

    private CombatClientPoses() {}

    /** Called from the loaders' pose packet handlers on the client thread. */
    public static void handlePacket(CombatPosePayload payload) {
        switch (payload.state()) {
            case CombatPosePayload.CHARGE_START -> {
                REMOTE_CHARGING.add(payload.entityId());
                Minecraft client = Minecraft.getInstance();
                if (client.level != null) REMOTE_CHARGE_START.put(payload.entityId(), client.level.getGameTime());
            }
            case CombatPosePayload.CHARGE_END -> {
                REMOTE_CHARGING.remove(payload.entityId());
                REMOTE_CHARGE_START.remove(payload.entityId());
                // Sent to ourselves when a running charge struck: stop our own charge too.
                Minecraft client = Minecraft.getInstance();
                if (client.player != null && client.player.getId() == payload.entityId()) CombatInputClient.serverEndedCharge();
            }
            case CombatPosePayload.BASH -> TRACKS.computeIfAbsent(payload.entityId(), id -> new Track()).bashTicks = 0.001F;
            case CombatPosePayload.SPIN -> TRACKS.computeIfAbsent(payload.entityId(), id -> new Track()).spinTicks = 0.001F;
            case CombatPosePayload.BASH_HIT -> {
                Minecraft client = Minecraft.getInstance();
                if (client.player != null && client.player.getId() == payload.entityId()) CombatInputClient.bashLanded();
            }
            default -> { }
        }
    }

    /** Starts the local shield-bash shove (the server decides what it actually hits). */
    public static void playBash(Player player) {
        TRACKS.computeIfAbsent(player.getId(), id -> new Track()).bashTicks = 0.001F;
    }

    public static void clear() {
        SWING_SIDES.clear();
        TRACKS.clear();
        REMOTE_CHARGING.clear();
        REMOTE_CHARGE_START.clear();
    }

    static void tick(Minecraft client) {
        if (client.level == null || client.player == null) {
            clear();
            return;
        }
        // Every loaded player, so vanilla-synced states (blocking, mace wind-up) show for others too.
        Set<Integer> ids = TICK_IDS;
        ids.clear();
        ids.addAll(TRACKS.keySet());
        ids.addAll(REMOTE_CHARGING);
        for (Player player : client.level.players()) ids.add(player.getId());
        for (int id : ids) {
            Entity entity = client.level.getEntity(id);
            if (!(entity instanceof Player player) || entity.isRemoved()) {
                TRACKS.remove(id);
                SWING_SIDES.remove(id);
                REMOTE_CHARGING.remove(id);
                REMOTE_CHARGE_START.remove(id);
                continue;
            }
            boolean charging = player == client.player ? CombatInputClient.isCharging() : REMOTE_CHARGING.contains(id);
            tickSwingSide(player, client.level.getGameTime());
            Track track = TRACKS.computeIfAbsent(id, key -> new Track());
            track.tick(targetPose(player, charging), player);
            track.readyCue(player, chargeHeld(client, player));
            track.sounds(player);
        }
        for (Iterator<Map.Entry<Integer, Track>> it = TRACKS.entrySet().iterator(); it.hasNext(); ) {
            if (it.next().getValue().idle()) it.remove();
        }
    }

    /** Dev screenshot harness only ({@link DevPoseGallery}): forces the local player's pose. */
    static CombatPose debugPose;
    /** Dev screenshot harness only: freezes the local player's swing / spin at this progress (negative = off). */
    static float debugSwing = -1.0F, debugSpin = -1.0F;
    /** Dev screenshot harness only: shows the local player's longsword swing as the return cut. */
    static boolean debugBackhand;

    /** The frozen swing progress for this player from {@link DevPoseGallery}, or -1. */
    public static float debugSwing(Player player) {
        return debugSwing >= 0.0F && player == Minecraft.getInstance().player ? debugSwing : -1.0F;
    }

    /** Ticks after a longsword swing starts within which the next swing is the return cut. */
    private static final long BACKHAND_WINDOW_TICKS = 30;
    private static final Map<Integer, SwingSide> SWING_SIDES = new HashMap<>();

    private static final class SwingSide {
        boolean wasSwinging, backhand;
        long lastStart = Long.MIN_VALUE / 2;
    }

    /**
     * Longsword swings alternate: right to left, then the return cut left to right, and so on; a pause
     * of over 1.5 s starts again from the right. Every client works this out from the synced swing, so
     * all players see the same side.
     */
    private static void tickSwingSide(Player player, long now) {
        SwingSide side = SWING_SIDES.computeIfAbsent(player.getId(), id -> new SwingSide());
        boolean swinging = player.swinging && player.swingingArm == net.minecraft.world.InteractionHand.MAIN_HAND;
        if (swinging && !side.wasSwinging && WeaponKind.of(player.getMainHandItem()) == WeaponKind.LONGSWORD) {
            side.backhand = now - side.lastStart <= BACKHAND_WINDOW_TICKS && !side.backhand;
            side.lastStart = now;
        }
        side.wasSwinging = swinging;
    }

    /** True while this player's current longsword swing is the left-to-right return cut. */
    public static boolean backhand(Player player) {
        if (debugBackhand && player == Minecraft.getInstance().player) return true;
        SwingSide side = SWING_SIDES.get(player.getId());
        return side != null && side.backhand;
    }

    private static CombatPose targetPose(Player player, boolean charging) {
        if (debugPose != null && player == Minecraft.getInstance().player) return debugPose;
        ItemStack main = player.getMainHandItem();
        WeaponKind kind = WeaponKind.of(main);
        if (kind == WeaponKind.DAGGER && player == Minecraft.getInstance().player && PounceClient.active()) {
            return CombatPose.DAGGER_POUNCE;
        }
        if (charging) {
            if (kind == WeaponKind.DAGGER) return CombatPose.DAGGER_CHARGE;
            if (kind == WeaponKind.TRIDENT) return CombatPose.TRIDENT_LOWERED;
            if (kind.thrusts()) return CombatPose.SPEAR_LOWERED;
        }
        if (player.isUsingItem() && player.getUseItem() == main) {
            if (kind == WeaponKind.LONGSWORD) return CombatPose.LONGSWORD_SPIN_CHARGE;
            if (kind == WeaponKind.SWORD && CombatVanillaRules.isSword(main)) return CombatPose.SWORD_BLOCK;
            if (kind == WeaponKind.DAGGER) return CombatPose.DAGGER_THROW;
        }
        return CombatPose.NONE;
    }

    /**
     * Ticks this player has been holding a spear charge (with partial tick), or -1 when they
     * aren't. Drives the vanilla-style spear charge animation in first and third person.
     */
    public static float spearChargeTicks(Player player, float partialTick) {
        if (WeaponKind.of(player.getMainHandItem()) != WeaponKind.SPEAR) return -1.0F;
        Minecraft client = Minecraft.getInstance();
        if (debugPose != null && player == client.player) {
            return debugPose == CombatPose.SPEAR_LOWERED ? CombatInputClient.chargeHeldTicks(client, partialTick) : -1.0F;
        }
        if (player == client.player) return CombatInputClient.chargeHeldTicks(client, partialTick);
        Long start = REMOTE_CHARGE_START.get(player.getId());
        if (start == null || client.level == null || !REMOTE_CHARGING.contains(player.getId())) return -1.0F;
        return (client.level.getGameTime() - start) + partialTick;
    }

    /** Ticks a dagger shakes after its charge fills (the "ready" cue). */
    private static final float READY_FLASH_TICKS = 6.0F;

    /** Ticks this player's charge has been held, or -1 when not charging (remote: since CHARGE_START arrived). */
    private static float chargeHeld(Minecraft client, Player player) {
        if (player == client.player) return CombatInputClient.chargeHeldTicks(client, 0.0F);
        Long start = REMOTE_CHARGE_START.get(player.getId());
        if (start == null || client.level == null || !REMOTE_CHARGING.contains(player.getId())) return -1.0F;
        return client.level.getGameTime() - start;
    }

    /**
     * 1 the moment this player's dagger charge fills, fading to 0 over a few ticks: drives the ready
     * shake on the dagger in first and third person. Works for everyone, since every client times
     * remote charges from the synced CHARGE_START.
     */
    public static float readyFlash(Player player, float partialTick) {
        Track track = TRACKS.get(player.getId());
        if (track == null || track.readyTicks <= 0.0F) return 0.0F;
        return 1.0F - Mth.clamp((track.readyTicks + partialTick) / READY_FLASH_TICKS, 0.0F, 1.0F);
    }

    /** Animation state to draw for this player this frame, or null when nothing is active. */
    public static View view(Player player, float partialTick) {
        Track track = TRACKS.get(player.getId());
        if (track == null && debugSpin >= 0.0F && player == Minecraft.getInstance().player) {
            return new View(CombatPose.NONE, 0.0F, 0.0F, 0.0F, debugSpin);
        }
        if (track == null) return null;
        float held = track.shown == CombatPose.NONE ? 0.0F : smooth(Mth.lerp(partialTick, track.prevBlend, track.blend));
        float recoil = effect(track.recoilTicks, partialTick, CombatPose.BLOCK_RECOIL);
        float bash = effect(track.bashTicks, partialTick, CombatPose.SHIELD_BASH);
        float spin = track.spinTicks <= 0.0F ? 0.0F
                : Mth.clamp((track.spinTicks + partialTick) / CombatPoseConfig.get(CombatPose.LONGSWORD_SPIN).blendIn(), 0.0F, 1.0F);
        if (spin >= 1.0F) spin = 0.0F;
        if (debugSpin >= 0.0F && player == Minecraft.getInstance().player) spin = debugSpin;
        if (held <= 0.0F && recoil <= 0.0F && bash <= 0.0F && spin <= 0.0F) return null;
        return new View(track.shown, held, recoil, bash, spin);
    }

    /** One-shot curve: fast push out (first 30%), then a soft settle back. */
    static float punch(float t) {
        if (t <= 0.0F || t >= 1.0F) return 0.0F;
        if (t < 0.3F) {
            float u = t / 0.3F;
            return 1.0F - (1.0F - u) * (1.0F - u) * (1.0F - u);
        }
        return 1.0F - smooth((t - 0.3F) / 0.7F);
    }

    private static float effect(float ticks, float partialTick, CombatPose pose) {
        if (ticks <= 0.0F) return 0.0F;
        return punch((ticks + partialTick) / CombatPoseConfig.get(pose).blendIn());
    }

    /** Plays a sound at a player for this client only (each client plays its own copy). */
    static void play(Player player, net.minecraft.sounds.SoundEvent sound) {
        player.level().playLocalSound(player.getX(), player.getY(), player.getZ(), sound,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.95F + player.getRandom().nextFloat() * 0.1F, false);
    }

    /** Smoothstep: soft start and soft landing, so poses settle instead of snapping. */
    static float smooth(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    /**
     * @param pose   held pose being shown (may be NONE while only effects play)
     * @param amount held pose blend, 0..1, eased
     * @param recoil block recoil curve value, 0..1
     * @param bash   shield bash curve value, 0..1
     * @param spin   longsword spin progress, 0..1 (linear; 0 when no spin plays)
     */
    public record View(CombatPose pose, float amount, float recoil, float bash, float spin) {}

    private static final class Track {
        CombatPose shown = CombatPose.NONE;
        float blend, prevBlend;
        float recoilTicks;
        float bashTicks;
        float spinTicks;
        int lastHurtTime;
        boolean wasSwinging;
        /** Ticks since this player's dagger charge filled (0 = no cue running). */
        float readyTicks;
        boolean readyFired;

        /** The moment a dagger charge fills: a ting (each client plays its own copy) and the ready shake. */
        void readyCue(Player player, float held) {
            if (readyTicks > 0.0F) readyTicks = readyTicks + 1.0F >= READY_FLASH_TICKS ? 0.0F : readyTicks + 1.0F;
            ItemStack main = player.getMainHandItem();
            if (held < 0.0F || WeaponKind.of(main) != WeaponKind.DAGGER) {
                readyFired = false;
                return;
            }
            if (!readyFired && held >= com.craisinlord.fightclub.combat.CombatChargeState.chargeTicksFor(main)) {
                readyFired = true;
                readyTicks = 0.001F;
                play(player, CombatSounds.DAGGER_READY);
            }
        }

        /** Swing whooshes (Better Combat plays its own), heard by everyone nearby. */
        void sounds(Player player) {
            boolean swinging = player.swinging && player.swingingArm == net.minecraft.world.InteractionHand.MAIN_HAND;
            if (swinging && !wasSwinging && !BetterCombatCompat.isLoaded()
                    && com.craisinlord.fightclub.CombatConfig.enabled(com.craisinlord.fightclub.CombatConfig.SWING_SOUNDS)) {
                net.minecraft.sounds.SoundEvent whoosh = switch (WeaponKind.of(player.getMainHandItem())) {
                    case DAGGER -> CombatSounds.DAGGER_SWING;
                    case SPEAR, TRIDENT -> CombatSounds.SPEAR_THRUST;
                    case LONGSWORD -> CombatSounds.LONGSWORD_SWING;
                    default -> null;
                };
                if (whoosh != null) play(player, whoosh);
            }
            wasSwinging = swinging;
        }

        void tick(CombatPose target, Player player) {
            prevBlend = blend;
            if (target != CombatPose.NONE && (shown == target || shown == CombatPose.NONE || blend <= 0.0F)) {
                if (shown != target) {
                    shown = target;
                    prevBlend = 0.0F;
                    blend = 0.0F;
                }
                blend = Math.min(1.0F, blend + 1.0F / CombatPoseConfig.get(shown).blendIn());
            } else if (shown != CombatPose.NONE) {
                // Blend out (also when switching poses: fully leave the old one first).
                blend = Math.max(0.0F, blend - 1.0F / CombatPoseConfig.get(shown).blendOut());
                if (blend <= 0.0F) shown = CombatPose.NONE;
            }

            // Recoil when a hit lands during a sword block (hurtTime jumps back up on each hit).
            if (shown == CombatPose.SWORD_BLOCK && player.hurtTime > lastHurtTime && player.hurtTime > 0) {
                recoilTicks = 0.001F;
            }
            lastHurtTime = player.hurtTime;
            recoilTicks = advance(recoilTicks, CombatPose.BLOCK_RECOIL);
            bashTicks = advance(bashTicks, CombatPose.SHIELD_BASH);
            spinTicks = advance(spinTicks, CombatPose.LONGSWORD_SPIN);
        }

        private static float advance(float ticks, CombatPose pose) {
            if (ticks <= 0.0F) return 0.0F;
            float next = ticks + 1.0F;
            return next >= CombatPoseConfig.get(pose).blendIn() ? 0.0F : next;
        }

        boolean idle() {
            return shown == CombatPose.NONE && blend <= 0.0F && prevBlend <= 0.0F
                    && recoilTicks <= 0.0F && bashTicks <= 0.0F && spinTicks <= 0.0F && !wasSwinging
                    && readyTicks <= 0.0F && !readyFired;
        }
    }
}
