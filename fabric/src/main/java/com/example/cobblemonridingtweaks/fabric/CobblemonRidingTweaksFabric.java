package com.example.cobblemonridingtweaks.fabric;

import com.example.cobblemonridingtweaks.CobblemonRidingTweaks;
import com.example.cobblemonridingtweaks.fabric.net.CobblemonRidingTweaksFabricNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class CobblemonRidingTweaksFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        CobblemonRidingTweaks.init(FabricLoader.getInstance().getConfigDir());
        CobblemonRidingTweaksFabricNetworking.registerServerNetworking();
    }
}
