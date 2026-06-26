package com.example.cobblemonridingtweaks.fabric.client;

import com.example.cobblemonridingtweaks.CobblemonRidingTweaks;
import com.example.cobblemonridingtweaks.config.RidingTweaksConfig;
import com.example.cobblemonridingtweaks.config.RidingTweaksConfigManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class RidingTweaksConfigScreen extends Screen {
    private final Screen parent;

    public RidingTweaksConfigScreen(Screen parent) {
        super(Component.literal(CobblemonRidingTweaks.MOD_NAME));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = this.height / 2 - 52;
        addRenderableWidget(Button.builder(enabledText(), button -> {
            config().enabled = !config().enabled;
            button.setMessage(enabledText());
            manager().save();
        }).bounds(centerX - 100, y, 200, 20).build());

        addRenderableWidget(Button.builder(debugText(), button -> {
            config().debugLogging = !config().debugLogging;
            button.setMessage(debugText());
            manager().save();
        }).bounds(centerX - 100, y + 24, 200, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Reload From File"), button -> {
            manager().reload();
            rebuildConfigButtons();
        }).bounds(centerX - 100, y + 56, 98, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Save"), button -> manager().save())
                .bounds(centerX + 2, y + 56, 98, 20)
                .build());

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(centerX - 100, y + 88, 200, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 24, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.literal(configSummary()), this.width / 2, 48, 0xA0A0A0);
        graphics.drawCenteredString(this.font, Component.literal(manager().path().toString()), this.width / 2, this.height - 32, 0x808080);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    private void rebuildConfigButtons() {
        clearWidgets();
        init();
    }

    private Component enabledText() {
        return Component.literal("Enabled: " + onOff(config().enabled));
    }

    private Component debugText() {
        return Component.literal("Debug Logging: " + onOff(config().debugLogging));
    }

    private String configSummary() {
        RidingTweaksConfig config = config();
        return "Stamina level " + config.stamina.levelScaling.minLevel + "-" + config.stamina.levelScaling.maxLevel
                + " => x" + config.stamina.levelScaling.minMultiplier + "-x" + config.stamina.levelScaling.maxMultiplier
                + ", max x" + config.stamina.maxFinalMultiplier;
    }

    private static String onOff(boolean value) {
        return value ? "On" : "Off";
    }

    private static RidingTweaksConfig config() {
        return manager().localConfig();
    }

    private static RidingTweaksConfigManager manager() {
        return CobblemonRidingTweaks.configManager();
    }
}
