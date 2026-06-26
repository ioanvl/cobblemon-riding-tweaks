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
    private static final long FEEDBACK_VISIBLE_MILLIS = 5_000L;

    private static String feedbackMessage = "";
    private static boolean feedbackSuccess = true;
    private static long feedbackUntilMillis;

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
        super.render(graphics, mouseX, mouseY, partialTick);
        drawCenteredStringWithBacking(graphics, this.title.getString(), 24, 0xFFFFFF, 0x70000000);
        drawCenteredStringWithBacking(graphics, configSummary(), 48, 0xD0D0D0, 0x70000000);
        drawCenteredStringWithBacking(graphics, statusText(), this.height - 50, 0xE0E0E0, 0x85000000);
        drawCenteredStringWithBacking(graphics, sourceText(), this.height - 34, 0xB8B8B8, 0x85000000);

        String feedback = feedbackText();
        if (!feedback.isBlank()) {
            drawCenteredStringWithBacking(
                    graphics,
                    feedback,
                    this.height - 68,
                    feedbackSuccess ? 0x80FF80 : 0xFF8080,
                    0xA0000000
            );
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    public static void showFeedback(String message, boolean success) {
        feedbackMessage = message == null ? "" : message;
        feedbackSuccess = success;
        feedbackUntilMillis = System.currentTimeMillis() + FEEDBACK_VISIBLE_MILLIS;
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
            showFeedback("Saving server config...", true);
            ClientPlayNetworking.send(new ConfigUpdatePayload(manager().activeConfigJson()));
            return;
        }
        manager().save();
        showFeedback("Saved local config.", true);
    }

    private void saveLocalChangesIfNeeded() {
        if (selectedTab == Tab.LOCAL) {
            manager().save();
            showFeedback("Saved local config.", true);
        }
    }

    private String feedbackText() {
        if (feedbackMessage.isBlank() || System.currentTimeMillis() > feedbackUntilMillis) {
            return "";
        }
        return feedbackMessage;
    }

    private void drawCenteredStringWithBacking(GuiGraphics graphics, String text, int y, int color, int backgroundColor) {
        int centerX = this.width / 2;
        int textWidth = this.font.width(text);
        int left = centerX - textWidth / 2 - 5;
        int top = y - 2;
        int right = centerX + textWidth / 2 + 5;
        int bottom = y + this.font.lineHeight + 1;
        graphics.fill(left, top, right, bottom, backgroundColor);
        graphics.drawCenteredString(this.font, text, centerX, y, color);
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
