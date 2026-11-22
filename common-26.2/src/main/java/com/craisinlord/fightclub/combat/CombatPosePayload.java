package com.craisinlord.fightclub.combat;

import com.craisinlord.fightclub.FightClub;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record CombatPosePayload(int entityId, byte state) implements CustomPacketPayload {
    public static final byte CHARGE_END = 0;
    public static final byte CHARGE_START = 1;
    public static final byte BASH = 2;
    public static final byte BASH_HIT = 3;
    public static final byte SPIN = 20;

    public static final Type<CombatPosePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(FightClub.MOD_ID, "combat_pose"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CombatPosePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CombatPosePayload::entityId,
            ByteBufCodecs.BYTE, CombatPosePayload::state,
            CombatPosePayload::new);

    @Override
    public Type<CombatPosePayload> type() {
        return TYPE;
    }
}
