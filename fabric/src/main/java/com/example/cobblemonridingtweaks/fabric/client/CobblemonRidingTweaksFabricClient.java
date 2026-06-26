package com.example.cobblemonridingtweaks.fabric.client;

import com.example.cobblemonridingtweaks.CobblemonRidingTweaks;
import com.example.cobblemonridingtweaks.fabric.net.ConfigSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class CobblemonRidingTweaksFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(ConfigSyncPayload.TYPE, (payload, context) ->
                context.client().execute(() -> CobblemonRidingTweaks.configManager()
                        .applyServerConfig(payload.configJson(), payload.canEditServerConfig()))
        );

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                CobblemonRidingTweaks.configManager().awaitServerConfig()
        );

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                CobblemonRidingTweaks.configManager().clearServerConfig()
        );
    }
}
