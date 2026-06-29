package com.example.cobblemonridingtweaks.net;

import com.example.cobblemonridingtweaks.CobblemonRidingTweaks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ConfigSyncPayload(String configJson, boolean canEditServerConfig) implements CustomPacketPayload {
    private static final int MAX_CONFIG_LENGTH = 262_144;
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            CobblemonRidingTweaks.MOD_ID,
            "config_sync"
    );
    public static final Type<ConfigSyncPayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigSyncPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeUtf(payload.configJson(), MAX_CONFIG_LENGTH);
                buffer.writeBoolean(payload.canEditServerConfig());
            },
            buffer -> new ConfigSyncPayload(buffer.readUtf(MAX_CONFIG_LENGTH), buffer.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
