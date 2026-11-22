package com.craisinlord.fightclub.client;

import com.craisinlord.fightclub.CombatConfig;
import com.craisinlord.fightclub.combat.CombatPosePayload;
import com.craisinlord.fightclub.combat.CombatSounds;
import com.craisinlord.fightclub.combat.CombatVanillaRules;
import com.craisinlord.fightclub.combat.WeaponKind;
import com.craisinlord.fightclub.compat.BetterCombatCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public final class CombatClientPoses {
    private static final Map<Integer, Track> TRACKS = new HashMap<>();
    private static final Set<Integer> REMOTE_CHARGING = new HashSet<>();
    private static final Map<Integer, Long> REMOTE_CHARGE_START = new HashMap<>();
    private static final Set<Integer> TICK_IDS = new HashSet<>();

    private CombatClientPoses() {}

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

    public static void playBash(Player player) {
        TRACKS.computeIfAbsent(player.getId(), id -> new Track()).bashTicks = 0.001F;
    }

    public static boolean isCharging(Player player) {
        Minecraft client = Minecraft.getInstance();
        return player == client.player ? CombatInputClient.isCharging() : REMOTE_CHARGING.contains(player.getId());
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
                continue;
            }
            tickSwingSide(player, client.level.getGameTime());
            Track track = TRACKS.computeIfAbsent(id, key -> new Track());
            track.tick(targetPose(player, isCharging(player)), player);
            track.readyCue(player, chargeHeld(client, player));
            track.sounds(player);
        }
        for (Iterator<Map.Entry<Integer, Track>> it = TRACKS.entrySet().iterator(); it.hasNext(); ) {
            if (it.next().getValue().idle()) it.remove();
        }
    }

    private static final long BACKHAND_WINDOW_TICKS = 30;
    private static final Map<Integer, SwingSide> SWING_SIDES = new HashMap<>();

    private static final class SwingSide {
        boolean wasSwinging, backhand;
        long lastStart = Long.MIN_VALUE / 2;
        int duration = 1;
    }

    public static final float THIRD_PERSON_SWING_STRETCH = 1.5F;

    private static void tickSwingSide(Player player, long now) {
        SwingSide side = SWING_SIDES.computeIfAbsent(player.getId(), id -> new SwingSide());
        boolean swinging = player.swinging && player.swingingArm == net.minecraft.world.InteractionHand.MAIN_HAND;
        WeaponKind kind = WeaponKind.of(player.getMainHandItem());
        if (swinging && !side.wasSwinging && (kind == WeaponKind.LONGSWORD || kind == WeaponKind.MACE)) {
            side.backhand = kind == WeaponKind.LONGSWORD && now - side.lastStart <= BACKHAND_WINDOW_TICKS && !side.backhand;
            side.lastStart = now;
            side.duration = Math.max(1, ((com.craisinlord.fightclub.mixin.LivingEntityAccessor) player).fightClub$getCurrentSwingDuration());
            if (com.craisinlord.fightclub.combat.LongswordSpin.sweepsArc(player)) {
                LongswordSweepParticle.cut(player, side.duration, side.backhand);
            }
        }
        side.wasSwinging = swinging;
    }

    public static float thirdPersonSwing(Player player, float partialTick) {
        SwingSide side = SWING_SIDES.get(player.getId());
        Minecraft client = Minecraft.getInstance();
        if (side == null || client.level == null) return 0.0F;
        float t = (client.level.getGameTime() - side.lastStart + partialTick) / (side.duration * THIRD_PERSON_SWING_STRETCH);
        return t > 0.0F && t < 1.0F ? t : 0.0F;
    }

    public static boolean backhand(Player player) {
        SwingSide side = SWING_SIDES.get(player.getId());
        return side != null && side.backhand;
    }

    private static CombatPose targetPose(Player player, boolean charging) {
        ItemStack main = player.getMainHandItem();
        WeaponKind kind = WeaponKind.of(main);
        if (kind == WeaponKind.DAGGER && player == Minecraft.getInstance().player && PounceClient.active()) {
            return CombatPose.DAGGER_POUNCE;
        }
        if (charging) {
            if (kind == WeaponKind.DAGGER) return CombatPose.DAGGER_CHARGE;
            if (kind == WeaponKind.TRIDENT) return CombatPose.TRIDENT_LOWERED;
            if (kind == WeaponKind.SPEAR) return CombatPose.NONE;
        }
        if (player.isUsingItem() && player.getUseItem() == main) {
            if (kind == WeaponKind.LONGSWORD) return CombatPose.LONGSWORD_SPIN_CHARGE;
            if (kind == WeaponKind.SWORD && CombatVanillaRules.isSword(main)) return CombatPose.SWORD_BLOCK;
            if (kind == WeaponKind.DAGGER) return CombatPose.DAGGER_THROW;
        }
        return CombatPose.NONE;
    }

    private static final float READY_FLASH_TICKS = 6.0F;

    private static float chargeHeld(Minecraft client, Player player) {
        if (player == client.player) return CombatInputClient.chargeHeldTicks(client, 0.0F);
        Long start = REMOTE_CHARGE_START.get(player.getId());
        if (start == null || client.level == null || !REMOTE_CHARGING.contains(player.getId())) return -1.0F;
        return client.level.getGameTime() - start;
    }

    public static float readyFlash(Player player, float partialTick) {
        Track track = TRACKS.get(player.getId());
        if (track == null || track.readyTicks <= 0.0F) return 0.0F;
        return 1.0F - Mth.clamp((track.readyTicks + partialTick) / READY_FLASH_TICKS, 0.0F, 1.0F);
    }

    public static View view(Player player, float partialTick) {
        Track track = TRACKS.get(player.getId());
        if (track == null) return null;
        float held = track.shown == CombatPose.NONE ? 0.0F : smooth(Mth.lerp(partialTick, track.prevBlend, track.blend));
        float recoil = effect(track.recoilTicks, partialTick, CombatPose.BLOCK_RECOIL);
        float bash = effect(track.bashTicks, partialTick, CombatPose.SHIELD_BASH);
        float spin = track.spinTicks <= 0.0F ? 0.0F
                : Mth.clamp((track.spinTicks + partialTick) / CombatPoseConfig.get(CombatPose.LONGSWORD_SPIN).blendIn(), 0.0F, 1.0F);
        if (spin >= 1.0F) spin = 0.0F;
        if (held <= 0.0F && recoil <= 0.0F && bash <= 0.0F && spin <= 0.0F) return null;
        return new View(track.shown, held, recoil, bash, spin);
    }

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

    static void play(Player player, SoundEvent sound) {
        player.level().playLocalSound(player.getX(), player.getY(), player.getZ(), sound,
                SoundSource.PLAYERS, 1.0F, 0.95F + player.getRandom().nextFloat() * 0.1F, false);
    }

    static float smooth(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    public record View(CombatPose pose, float amount, float recoil, float bash, float spin) {}

    private static final class Track {
        CombatPose shown = CombatPose.NONE;
        float blend, prevBlend;
        float recoilTicks;
        float bashTicks;
        float spinTicks;
        int lastHurtTime;
        boolean wasSwinging;
        float readyTicks;
        boolean readyFired;

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

        void sounds(Player player) {
            boolean swinging = player.swinging && player.swingingArm == InteractionHand.MAIN_HAND;
            if (swinging && !wasSwinging && !BetterCombatCompat.isLoaded() && !mining(player)
                    && CombatConfig.enabled(CombatConfig.SWING_SOUNDS)) {
                ItemStack main = player.getMainHandItem();
                SoundEvent whoosh = switch (WeaponKind.of(main)) {
                    case DAGGER -> CombatSounds.DAGGER_SWING;
                    case SPEAR, TRIDENT -> CombatSounds.SPEAR_THRUST;
                    case LONGSWORD -> CombatSounds.LONGSWORD_SWING;
                    case SWORD -> CombatSounds.SWORD_SWING;
                    case MACE -> CombatSounds.MACE_SWING;
                    default -> main.is(net.minecraft.tags.ItemTags.AXES) ? CombatSounds.AXE_SWING : null;
                };
                if (whoosh != null) play(player, whoosh);
            }
            wasSwinging = swinging;
        }

        private static boolean mining(Player player) {
            Minecraft client = Minecraft.getInstance();
            return player == client.player && client.gameMode != null && client.gameMode.isDestroying();
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
                blend = Math.max(0.0F, blend - 1.0F / CombatPoseConfig.get(shown).blendOut());
                if (blend <= 0.0F) shown = CombatPose.NONE;
            }

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
