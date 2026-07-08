package com.example.cobblemonridingtweaks.net;

import com.example.cobblemonridingtweaks.CobblemonRidingTweaks;
import com.example.cobblemonridingtweaks.config.RidingTweaksConfigManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.function.BiConsumer;

public final class ConfigClientHandlers {
    private static final BiConsumer<String, Boolean> NOOP_EDIT_RESULT_HANDLER = (message, success) -> {
    };

    private static BiConsumer<String, Boolean> editResultHandler = NOOP_EDIT_RESULT_HANDLER;

    private ConfigClientHandlers() {
    }

    public static void setEditResultHandler(BiConsumer<String, Boolean> handler) {
        editResultHandler = handler == null ? NOOP_EDIT_RESULT_HANDLER : handler;
    }

    public static void applyServerConfig(ConfigSyncPayload payload) {
        RidingTweaksConfigManager.ServerConfigApplyResult result = CobblemonRidingTweaks.configManager()
                .applyServerConfigWithResult(payload.configJson(), payload.canEditServerConfig());
        if (result.versionMismatch()) {
            showLocalChatMessage(
                    Component.literal(CobblemonRidingTweaks.MOD_NAME + " sync skipped: server config version ")
                            .append(Component.literal(result.serverConfigVersion()).withStyle(ChatFormatting.YELLOW))
                            .append(Component.literal(" is incompatible with this client ("))
                            .append(Component.literal(result.supportedConfigVersion()).withStyle(ChatFormatting.YELLOW))
                            .append(Component.literal("). Using neutral x1 riding tweaks."))
                            .withStyle(ChatFormatting.GOLD)
            );
        }
    }

    public static void showEditResult(ConfigEditResultPayload payload) {
        editResultHandler.accept(payload.message(), payload.success());
    }

    private static void showLocalChatMessage(Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(message, false);
        }
    }
}
