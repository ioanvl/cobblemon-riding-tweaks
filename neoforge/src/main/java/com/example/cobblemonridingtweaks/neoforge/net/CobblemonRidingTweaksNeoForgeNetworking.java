package com.example.cobblemonridingtweaks.neoforge.net;

import com.example.cobblemonridingtweaks.CobblemonRidingTweaks;
import com.example.cobblemonridingtweaks.net.ConfigClientHandlers;
import com.example.cobblemonridingtweaks.net.ConfigEditResultPayload;
import com.example.cobblemonridingtweaks.net.ConfigSyncPayload;
import com.example.cobblemonridingtweaks.net.ConfigUpdatePayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CobblemonRidingTweaksNeoForgeNetworking {
    private static final Logger LOGGER = LoggerFactory.getLogger(CobblemonRidingTweaks.MOD_NAME);
    private static final int SERVER_CONFIG_EDIT_PERMISSION_LEVEL = 3;
    private static final String NETWORK_VERSION = "1.0.0";

    private CobblemonRidingTweaksNeoForgeNetworking() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(CobblemonRidingTweaks.MOD_ID)
                .versioned(NETWORK_VERSION)
                .optional();

        registrar.playToClient(ConfigEditResultPayload.TYPE, ConfigEditResultPayload.CODEC, CobblemonRidingTweaksNeoForgeNetworking::handleEditResult);
        registrar.playToClient(ConfigSyncPayload.TYPE, ConfigSyncPayload.CODEC, CobblemonRidingTweaksNeoForgeNetworking::handleConfigSync);
        registrar.playToServer(ConfigUpdatePayload.TYPE, ConfigUpdatePayload.CODEC, CobblemonRidingTweaksNeoForgeNetworking::handleConfigUpdate);
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncConfigTo(player);
        }
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

    public static void sendConfigUpdateToServer(String configJson) {
        PacketDistributor.sendToServer(new ConfigUpdatePayload(configJson));
    }

    private static boolean syncConfigTo(ServerPlayer player) {
        if (!NetworkRegistry.hasChannel(player.connection, ConfigSyncPayload.ID)) {
            return false;
        }

        PacketDistributor.sendToPlayer(
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

    private static void handleConfigSync(ConfigSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ConfigClientHandlers.applyServerConfig(payload));
    }

    private static void handleEditResult(ConfigEditResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ConfigClientHandlers.showEditResult(payload));
    }

    private static void handleConfigUpdate(ConfigUpdatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

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
        });
    }

    private static boolean canEditServerConfig(ServerPlayer player) {
        return player.hasPermissions(SERVER_CONFIG_EDIT_PERMISSION_LEVEL);
    }

    private static void sendEditResult(ServerPlayer player, String message, boolean success) {
        if (NetworkRegistry.hasChannel(player.connection, ConfigEditResultPayload.ID)) {
            PacketDistributor.sendToPlayer(player, new ConfigEditResultPayload(message, success));
        }
    }
}
