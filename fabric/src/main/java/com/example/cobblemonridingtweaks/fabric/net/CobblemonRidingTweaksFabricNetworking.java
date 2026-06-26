package com.example.cobblemonridingtweaks.fabric.net;

import com.example.cobblemonridingtweaks.CobblemonRidingTweaks;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CobblemonRidingTweaksFabricNetworking {
    private static final Logger LOGGER = LoggerFactory.getLogger(CobblemonRidingTweaks.MOD_NAME);

    private CobblemonRidingTweaksFabricNetworking() {
    }

    public static void registerServerNetworking() {
        PayloadTypeRegistry.playS2C().register(ConfigSyncPayload.TYPE, ConfigSyncPayload.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            syncConfigTo(handler.player);
        });
    }

    public static int syncConfigToAll(MinecraftServer server) {
        int syncedPlayers = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (syncConfigTo(player)) {
                syncedPlayers++;
            }
        }
        return syncedPlayers;
    }

    private static boolean syncConfigTo(ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, ConfigSyncPayload.TYPE)) {
            return false;
        }

        ServerPlayNetworking.send(
                player,
                new ConfigSyncPayload(CobblemonRidingTweaks.configManager().localConfigJson())
        );

        if (CobblemonRidingTweaks.configManager().config().debugLogging) {
            LOGGER.info("Synced {} config to {}", CobblemonRidingTweaks.MOD_NAME, player.getGameProfile().getName());
        }
        return true;
    }
}
