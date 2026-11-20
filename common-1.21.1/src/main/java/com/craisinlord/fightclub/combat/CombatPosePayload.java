package com.craisinlord.fightclub.combat;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: animation state of another player that vanilla doesn't sync (hold-left-click
 * charges and shield bashes), for third-person poses.
 */
public record CombatPosePayload(int entityId, byte state) implements CustomPacketPayload {
    public static final byte CHARGE_END = 0;
    public static final byte CHARGE_START = 1;
    public static final byte BASH = 2;
    /** A charged longsword spin was released (also sent to the spinning player). */
    public static final byte SPIN = 20;
    /** Sent only to the basher: the bash hit something, so its bash is locked for bash_hit_cooldown_ticks. */
    public static final byte BASH_HIT = 3;

    public static final Type<CombatPosePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("fight_club", "combat_pose"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CombatPosePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CombatPosePayload::entityId,
            ByteBufCodecs.BYTE, CombatPosePayload::state,
            CombatPosePayload::new);

    @Override
    public Type<CombatPosePayload> type() {
        return TYPE;
    }
}
