package com.example.cobblemonridingtweaks.net;

import com.example.cobblemonridingtweaks.CobblemonRidingTweaks;

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
        CobblemonRidingTweaks.configManager().applyServerConfig(payload.configJson(), payload.canEditServerConfig());
    }

    public static void showEditResult(ConfigEditResultPayload payload) {
        editResultHandler.accept(payload.message(), payload.success());
    }
}
