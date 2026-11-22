package com.craisinlord.fightclub.combat;

import com.craisinlord.fightclub.FightClub;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

public record WeaponItemsPayload(Map<String, String> items) implements CustomPacketPayload {
    public static final Type<WeaponItemsPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(FightClub.MOD_ID, "weapon_items"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WeaponItemsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8), WeaponItemsPayload::items,
            WeaponItemsPayload::new);

    public static WeaponItemsPayload current() {
        return new WeaponItemsPayload(ExternalWeapons.rawJson());
    }

    public void apply() {
        ExternalWeapons.apply(items);
    }

    @Override
    public Type<WeaponItemsPayload> type() {
        return TYPE;
    }
}
