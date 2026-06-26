package com.example.cobblemonridingtweaks.neoforge;

import com.example.cobblemonridingtweaks.CobblemonRidingTweaks;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;

@Mod(CobblemonRidingTweaks.MOD_ID)
public final class CobblemonRidingTweaksNeoForge {
    public CobblemonRidingTweaksNeoForge() {
        CobblemonRidingTweaks.init(FMLPaths.CONFIGDIR.get());
    }
}
