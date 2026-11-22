package com.craisinlord.fightclub.combat;

import com.craisinlord.fightclub.FightClub;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record CombatActionPayload(byte action, int target) implements CustomPacketPayload {
    public CombatActionPayload(byte action) { this(action, -1); }

    public static final Type<CombatActionPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(FightClub.MOD_ID, "combat_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CombatActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BYTE, CombatActionPayload::action,
            ByteBufCodecs.VAR_INT, CombatActionPayload::target, CombatActionPayload::new);

    @Override
    public Type<CombatActionPayload> type() {
        return TYPE;
    }
}
