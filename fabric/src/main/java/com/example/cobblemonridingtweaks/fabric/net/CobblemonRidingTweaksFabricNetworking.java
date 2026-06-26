package com.example.cobblemonridingtweaks.fabric.net;

import com.example.cobblemonridingtweaks.CobblemonRidingTweaks;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CobblemonRidingTweaksFabricNetworking {
    private static final Logger LOGGER = LoggerFactory.getLogger(CobblemonRidingTweaks.MOD_NAME);

    private CobblemonRidingTweaksFabricNetworking() {
    }

    public static void registerServerNetworking() {
        PayloadTypeRegistry.playS2C().register(ConfigSyncPayload.TYPE, ConfigSyncPayload.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (!ServerPlayNetworking.canSend(handler.player, ConfigSyncPayload.TYPE)) {
                return;
            }

            ServerPlayNetworking.send(
                    handler.player,
                    new ConfigSyncPayload(CobblemonRidingTweaks.configManager().localConfigJson())
            );

            if (CobblemonRidingTweaks.configManager().config().debugLogging) {
                LOGGER.info("Synced {} config to {}", CobblemonRidingTweaks.MOD_NAME, handler.player.getGameProfile().getName());
            }
        });
    }
}
