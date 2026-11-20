package com.craisinlord.fightclub.combat;

import com.craisinlord.fightclub.CombatConfig;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Server to client, on join and after /reload: the server's weapon specs (material/weapon key to
 * source JSON) and its gameplay config, so client tooltips, input and timing match the server.
 */
public record WeaponSpecSyncPayload(Map<String, String> specs, Map<String, String> config) implements CustomPacketPayload {
    public static final Type<WeaponSpecSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("fight_club", "weapon_specs"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WeaponSpecSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8), WeaponSpecSyncPayload::specs,
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8), WeaponSpecSyncPayload::config,
            WeaponSpecSyncPayload::new);

    public static WeaponSpecSyncPayload current() {
        return new WeaponSpecSyncPayload(WeaponSpecs.rawJson(), CombatConfig.syncedValues());
    }

    /** Client side. */
    public void apply() {
        WeaponSpecs.applySynced(specs);
        CombatConfig.applySynced(config);
    }

    @Override
    public Type<WeaponSpecSyncPayload> type() {
        return TYPE;
    }
}
