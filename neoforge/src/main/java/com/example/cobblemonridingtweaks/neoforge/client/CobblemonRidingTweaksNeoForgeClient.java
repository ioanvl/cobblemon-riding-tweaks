package com.example.cobblemonridingtweaks.neoforge.client;

import com.example.cobblemonridingtweaks.CobblemonRidingTweaks;
import com.example.cobblemonridingtweaks.client.RidingTweaksConfigScreen;
import com.example.cobblemonridingtweaks.net.ConfigClientHandlers;
import com.example.cobblemonridingtweaks.neoforge.net.CobblemonRidingTweaksNeoForgeNetworking;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

public final class CobblemonRidingTweaksNeoForgeClient {
    private CobblemonRidingTweaksNeoForgeClient() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(CobblemonRidingTweaksNeoForgeClient::onClientSetup);
        NeoForge.EVENT_BUS.addListener(CobblemonRidingTweaksNeoForgeClient::onClientLoggingIn);
        NeoForge.EVENT_BUS.addListener(CobblemonRidingTweaksNeoForgeClient::onClientLoggingOut);
        RidingTweaksConfigScreen.setServerConfigUpdateSender(CobblemonRidingTweaksNeoForgeNetworking::sendConfigUpdateToServer);
        ConfigClientHandlers.setEditResultHandler(RidingTweaksConfigScreen::showFeedback);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ModList.get()
                .getModContainerById(CobblemonRidingTweaks.MOD_ID)
                .ifPresent(container -> container.registerExtensionPoint(
                        IConfigScreenFactory.class,
                        new IConfigScreenFactory() {
                            @Override
                            public Screen createScreen(ModContainer container, Screen modListScreen) {
                                return new RidingTweaksConfigScreen(modListScreen);
                            }
                        }
                )));
    }

    private static void onClientLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        CobblemonRidingTweaks.configManager().awaitServerConfig();
    }

    private static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        CobblemonRidingTweaks.configManager().clearServerConfig();
    }
}
