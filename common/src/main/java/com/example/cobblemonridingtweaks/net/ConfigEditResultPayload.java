package com.example.cobblemonridingtweaks.net;

import com.example.cobblemonridingtweaks.CobblemonRidingTweaks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ConfigEditResultPayload(String message, boolean success) implements CustomPacketPayload {
    private static final int MAX_MESSAGE_LENGTH = 512;
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            CobblemonRidingTweaks.MOD_ID,
            "config_edit_result"
    );
    public static final Type<ConfigEditResultPayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigEditResultPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeUtf(payload.message(), MAX_MESSAGE_LENGTH);
                buffer.writeBoolean(payload.success());
            },
            buffer -> new ConfigEditResultPayload(buffer.readUtf(MAX_MESSAGE_LENGTH), buffer.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
