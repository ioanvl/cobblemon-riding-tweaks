package com.example.cobblemonridingtweaks.neoforge.command;

import com.example.cobblemonridingtweaks.CobblemonRidingTweaks;
import com.example.cobblemonridingtweaks.neoforge.net.CobblemonRidingTweaksNeoForgeNetworking;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class CobblemonRidingTweaksNeoForgeCommands {
    private static final int RELOAD_PERMISSION_LEVEL = 3;

    private CobblemonRidingTweaksNeoForgeCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cobblemonridingtweaks")
                .requires(source -> source.hasPermission(RELOAD_PERMISSION_LEVEL))
                .then(Commands.literal("reload")
                        .executes(context -> reload(context.getSource()))));
    }

    private static int reload(CommandSourceStack source) {
        CobblemonRidingTweaks.configManager().reload();
        int syncedPlayers = CobblemonRidingTweaksNeoForgeNetworking.syncConfigToAll(source.getServer());
        source.sendSuccess(
                () -> Component.literal("Reloaded " + CobblemonRidingTweaks.MOD_NAME + " config and synced "
                        + syncedPlayers + " client(s)."),
                true
        );
        return syncedPlayers;
    }
}
