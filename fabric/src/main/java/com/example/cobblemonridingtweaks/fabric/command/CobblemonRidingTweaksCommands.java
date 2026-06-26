package com.example.cobblemonridingtweaks.fabric.command;

import com.example.cobblemonridingtweaks.CobblemonRidingTweaks;
import com.example.cobblemonridingtweaks.fabric.net.CobblemonRidingTweaksFabricNetworking;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class CobblemonRidingTweaksCommands {
    private static final int RELOAD_PERMISSION_LEVEL = 3;

    private CobblemonRidingTweaksCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cobblemonridingtweaks")
                .requires(source -> source.hasPermission(RELOAD_PERMISSION_LEVEL))
                .then(Commands.literal("reload")
                        .executes(context -> reload(context.getSource()))));
    }

    private static int reload(CommandSourceStack source) {
        CobblemonRidingTweaks.configManager().reload();
        int syncedPlayers = CobblemonRidingTweaksFabricNetworking.syncConfigToAll(source.getServer());
        source.sendSuccess(
                () -> Component.literal("Reloaded " + CobblemonRidingTweaks.MOD_NAME + " config and synced "
                        + syncedPlayers + " client(s)."),
                true
        );
        return syncedPlayers;
    }
}
