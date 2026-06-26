package com.example.cobblemonridingtweaks.fabric.net;

import com.example.cobblemonridingtweaks.CobblemonRidingTweaks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ConfigUpdatePayload(String configJson) implements CustomPacketPayload {
    private static final int MAX_CONFIG_LENGTH = 262_144;
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            CobblemonRidingTweaks.MOD_ID,
            "config_update"
    );
    public static final Type<ConfigUpdatePayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigUpdatePayload> CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeUtf(payload.configJson(), MAX_CONFIG_LENGTH),
            buffer -> new ConfigUpdatePayload(buffer.readUtf(MAX_CONFIG_LENGTH))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
