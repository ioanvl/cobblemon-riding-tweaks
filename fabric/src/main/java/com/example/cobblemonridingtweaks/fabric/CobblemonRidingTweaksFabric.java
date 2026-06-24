package com.example.cobblemonridingtweaks.fabric;

import com.example.cobblemonridingtweaks.CobblemonRidingTweaks;
import net.fabricmc.api.ModInitializer;

public final class CobblemonRidingTweaksFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        CobblemonRidingTweaks.init();
    }
}
