package com.example.cobblemonridingtweaks.fabric.client;

import com.example.cobblemonridingtweaks.CobblemonRidingTweaks;
import com.example.cobblemonridingtweaks.config.RidingTweaksConfig;
import com.example.cobblemonridingtweaks.config.RidingTweaksConfigManager;
import com.example.cobblemonridingtweaks.fabric.net.ConfigUpdatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class RidingTweaksConfigScreen extends Screen {
    private final Screen parent;
    private Tab selectedTab = Tab.LOCAL;

    public RidingTweaksConfigScreen(Screen parent) {
        super(Component.literal(CobblemonRidingTweaks.MOD_NAME));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (selectedTab == Tab.SERVER && !manager().isServerConfigActive()) {
            selectedTab = Tab.LOCAL;
        }

        int centerX = this.width / 2;
        int y = this.height / 2 - 52;

        if (manager().isServerConfigActive()) {
            addRenderableWidget(Button.builder(tabText(Tab.LOCAL), button -> {
                selectedTab = Tab.LOCAL;
                rebuildConfigButtons();
            }).bounds(centerX - 100, y - 34, 98, 20).build());

            addRenderableWidget(Button.builder(tabText(Tab.SERVER), button -> {
                selectedTab = Tab.SERVER;
                rebuildConfigButtons();
            }).bounds(centerX + 2, y - 34, 98, 20).build());
        }

        Button enabledButton = Button.builder(enabledText(), button -> {
            viewingConfig().enabled = !viewingConfig().enabled;
            button.setMessage(enabledText());
            saveLocalChangesIfNeeded();
        }).bounds(centerX - 100, y, 200, 20).build();
        enabledButton.active = selectedTabIsEditable();
        addRenderableWidget(enabledButton);

        Button debugButton = Button.builder(debugText(), button -> {
            viewingConfig().debugLogging = !viewingConfig().debugLogging;
            button.setMessage(debugText());
            saveLocalChangesIfNeeded();
        }).bounds(centerX - 100, y + 24, 200, 20).build();
        debugButton.active = selectedTabIsEditable();
        addRenderableWidget(debugButton);

        Button reloadButton = Button.builder(Component.literal("Reload From File"), button -> {
            manager().reload();
            rebuildConfigButtons();
        }).bounds(centerX - 100, y + 56, 98, 20).build();
        reloadButton.active = selectedTab == Tab.LOCAL;
        addRenderableWidget(reloadButton);

        Button saveButton = Button.builder(Component.literal("Save"), button -> saveCurrentConfig())
                .bounds(centerX + 2, y + 56, 98, 20)
                .build();
        saveButton.active = selectedTabIsEditable();
        addRenderableWidget(saveButton);

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(centerX - 100, y + 88, 200, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 24, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.literal(configSummary()), this.width / 2, 48, 0xA0A0A0);
        graphics.drawCenteredString(this.font, Component.literal(statusText()), this.width / 2, this.height - 46, 0xA0A0A0);
        graphics.drawCenteredString(this.font, Component.literal(sourceText()), this.width / 2, this.height - 32, 0x808080);
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
        return Component.literal("Enabled: " + onOff(viewingConfig().enabled));
    }

    private Component debugText() {
        return Component.literal("Debug Logging: " + onOff(viewingConfig().debugLogging));
    }

    private String configSummary() {
        RidingTweaksConfig config = viewingConfig();
        return "Stamina level " + config.stamina.levelScaling.minLevel + "-" + config.stamina.levelScaling.maxLevel
                + " => x" + config.stamina.levelScaling.minMultiplier + "-x" + config.stamina.levelScaling.maxMultiplier
                + ", max x" + config.stamina.maxFinalMultiplier;
    }

    private Component tabText(Tab tab) {
        return Component.literal((selectedTab == tab ? "> " : "") + tab.displayName);
    }

    private String statusText() {
        if (selectedTab == Tab.SERVER) {
            if (manager().canEditServerConfig()) {
                return "Server config is active. Your permissions allow editing and saving it.";
            }
            return "Server config is active for this multiplayer session. This view is read-only.";
        }
        if (manager().isServerConfigActive()) {
            return "Local config is saved for singleplayer or servers without active sync.";
        }
        return "Local config is active.";
    }

    private String sourceText() {
        if (selectedTab == Tab.SERVER) {
            return "Server synced config";
        }
        return manager().path().toString();
    }

    private static String onOff(boolean value) {
        return value ? "On" : "Off";
    }

    private RidingTweaksConfig viewingConfig() {
        return selectedTab == Tab.SERVER ? manager().config() : manager().localConfig();
    }

    private boolean selectedTabIsEditable() {
        return selectedTab == Tab.LOCAL || manager().canEditServerConfig();
    }

    private void saveCurrentConfig() {
        if (selectedTab == Tab.SERVER) {
            ClientPlayNetworking.send(new ConfigUpdatePayload(manager().activeConfigJson()));
            return;
        }
        manager().save();
    }

    private void saveLocalChangesIfNeeded() {
        if (selectedTab == Tab.LOCAL) {
            manager().save();
        }
    }

    private static RidingTweaksConfigManager manager() {
        return CobblemonRidingTweaks.configManager();
    }

    private enum Tab {
        LOCAL("Local"),
        SERVER("Server");

        private final String displayName;

        Tab(String displayName) {
            this.displayName = displayName;
        }
    }
}
