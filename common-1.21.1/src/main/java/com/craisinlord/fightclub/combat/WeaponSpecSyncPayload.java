package com.craisinlord.fightclub.combat;

import com.craisinlord.fightclub.CombatConfig;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

public record WeaponSpecSyncPayload(Map<String, String> specs, Map<String, String> config, Map<String, String> items) implements CustomPacketPayload {
    public static final Type<WeaponSpecSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("fight_club", "weapon_specs"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WeaponSpecSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8), WeaponSpecSyncPayload::specs,
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8), WeaponSpecSyncPayload::config,
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8), WeaponSpecSyncPayload::items,
            WeaponSpecSyncPayload::new);

    public static WeaponSpecSyncPayload current() {
        return new WeaponSpecSyncPayload(WeaponSpecs.rawJson(), CombatConfig.syncedValues(), ExternalWeapons.rawJson());
    }

    public void apply() {
        WeaponSpecs.applySynced(specs);
        CombatConfig.applySynced(config);
        ExternalWeapons.apply(items);
    }

    @Override
    public Type<WeaponSpecSyncPayload> type() {
        return TYPE;
    }
}
