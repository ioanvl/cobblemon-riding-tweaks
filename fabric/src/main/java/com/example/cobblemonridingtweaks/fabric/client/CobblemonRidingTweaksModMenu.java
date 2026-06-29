package com.example.cobblemonridingtweaks.fabric.client;

import com.example.cobblemonridingtweaks.client.RidingTweaksConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public final class CobblemonRidingTweaksModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return RidingTweaksConfigScreen::new;
    }
}
