package com.example.cobblemonridingtweaks.fabric.client;

import com.example.cobblemonridingtweaks.CobblemonRidingTweaks;
import com.example.cobblemonridingtweaks.client.RidingTweaksConfigScreen;
import com.example.cobblemonridingtweaks.net.ConfigClientHandlers;
import com.example.cobblemonridingtweaks.net.ConfigEditResultPayload;
import com.example.cobblemonridingtweaks.net.ConfigSyncPayload;
import com.example.cobblemonridingtweaks.net.ConfigUpdatePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class CobblemonRidingTweaksFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        RidingTweaksConfigScreen.setServerConfigUpdateSender(configJson ->
                ClientPlayNetworking.send(new ConfigUpdatePayload(configJson))
        );
        ConfigClientHandlers.setEditResultHandler(RidingTweaksConfigScreen::showFeedback);

        ClientPlayNetworking.registerGlobalReceiver(ConfigSyncPayload.TYPE, (payload, context) ->
                context.client().execute(() -> ConfigClientHandlers.applyServerConfig(payload))
        );
        ClientPlayNetworking.registerGlobalReceiver(ConfigEditResultPayload.TYPE, (payload, context) ->
                context.client().execute(() -> ConfigClientHandlers.showEditResult(payload))
        );

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (client.hasSingleplayerServer()) {
                CobblemonRidingTweaks.configManager().clearServerConfig();
            } else {
                CobblemonRidingTweaks.configManager().awaitServerConfig();
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                CobblemonRidingTweaks.configManager().clearServerConfig()
        );
    }
}
