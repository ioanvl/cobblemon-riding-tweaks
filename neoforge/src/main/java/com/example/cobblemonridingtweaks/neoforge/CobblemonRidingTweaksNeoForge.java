package com.example.cobblemonridingtweaks.neoforge;

import com.example.cobblemonridingtweaks.CobblemonRidingTweaks;
import com.example.cobblemonridingtweaks.neoforge.client.CobblemonRidingTweaksNeoForgeClient;
import com.example.cobblemonridingtweaks.neoforge.command.CobblemonRidingTweaksNeoForgeCommands;
import com.example.cobblemonridingtweaks.neoforge.net.CobblemonRidingTweaksNeoForgeNetworking;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;

@Mod(CobblemonRidingTweaks.MOD_ID)
public final class CobblemonRidingTweaksNeoForge {
    public CobblemonRidingTweaksNeoForge(IEventBus modBus) {
        CobblemonRidingTweaks.init(FMLPaths.CONFIGDIR.get());
        modBus.addListener(CobblemonRidingTweaksNeoForgeNetworking::registerPayloads);
        NeoForge.EVENT_BUS.addListener(CobblemonRidingTweaksNeoForgeNetworking::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(CobblemonRidingTweaksNeoForgeCommands::register);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            CobblemonRidingTweaksNeoForgeClient.init(modBus);
        }
    }
}
