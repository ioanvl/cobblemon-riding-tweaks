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
    private static final int SERVER_CONFIG_EDIT_PERMISSION_LEVEL = 3;

    private CobblemonRidingTweaksFabricNetworking() {
    }

    public static void registerServerNetworking() {
        PayloadTypeRegistry.playS2C().register(ConfigEditResultPayload.TYPE, ConfigEditResultPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ConfigSyncPayload.TYPE, ConfigSyncPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ConfigUpdatePayload.TYPE, ConfigUpdatePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ConfigUpdatePayload.TYPE, (payload, context) ->
                context.server().execute(() -> handleConfigUpdate(payload, context.player()))
        );
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
                new ConfigSyncPayload(
                        CobblemonRidingTweaks.configManager().localConfigJson(),
                        canEditServerConfig(player)
                )
        );

        if (CobblemonRidingTweaks.configManager().config().debugLogging) {
            LOGGER.info("Synced {} config to {}", CobblemonRidingTweaks.MOD_NAME, player.getGameProfile().getName());
        }
        return true;
    }

    private static void handleConfigUpdate(ConfigUpdatePayload payload, ServerPlayer player) {
        if (!canEditServerConfig(player)) {
            LOGGER.warn(
                    "{} tried to update {} config without permission",
                    player.getGameProfile().getName(),
                    CobblemonRidingTweaks.MOD_NAME
            );
            sendEditResult(player, "You do not have permission to edit server config.", false);
            syncConfigTo(player);
            return;
        }

        if (!CobblemonRidingTweaks.configManager().replaceFromRemoteJson(payload.configJson())) {
            sendEditResult(player, "Rejected server config update. Check the server log for details.", false);
            syncConfigTo(player);
            return;
        }

        int syncedPlayers = syncConfigToAll(player.server);
        sendEditResult(player, "Saved server config and synced " + syncedPlayers + " client(s).", true);
    }

    private static boolean canEditServerConfig(ServerPlayer player) {
        return player.hasPermissions(SERVER_CONFIG_EDIT_PERMISSION_LEVEL);
    }

    private static void sendEditResult(ServerPlayer player, String message, boolean success) {
        if (ServerPlayNetworking.canSend(player, ConfigEditResultPayload.TYPE)) {
            ServerPlayNetworking.send(player, new ConfigEditResultPayload(message, success));
        }
    }
}
