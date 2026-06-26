package com.example.cobblemonridingtweaks;

import com.example.cobblemonridingtweaks.config.RidingTweaksConfigManager;

import java.nio.file.Path;

public final class CobblemonRidingTweaks {
    public static final String MOD_ID = "cobblemon_riding_tweaks";
    public static final String MOD_NAME = "Cobblemon Riding Tweaks";

    private static RidingTweaksConfigManager configManager;

    private CobblemonRidingTweaks() {
    }

    public static void init(Path configDirectory) {
        configManager = RidingTweaksConfigManager.load(configDirectory);
    }

    public static RidingTweaksConfigManager configManager() {
        if (configManager == null) {
            throw new IllegalStateException(MOD_NAME + " has not been initialized");
        }
        return configManager;
    }
}
