package com.example.cobblemonridingtweaks.fabric.client;

import com.example.cobblemonridingtweaks.CobblemonRidingTweaks;
import com.example.cobblemonridingtweaks.config.RidingTweaksConfig;
import com.example.cobblemonridingtweaks.config.RidingTweaksConfigManager;
import com.example.cobblemonridingtweaks.fabric.net.ConfigUpdatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class RidingTweaksConfigScreen extends Screen {
    private static final long FEEDBACK_VISIBLE_MILLIS = 5_000L;
    private static final int ROW_HEIGHT = 24;
    private static final int FIRST_ROW_Y = 112;
    private static final List<String> RIDE_STYLE_KEYS = List.of("land", "liquid", "air");
    private static final List<String> BEHAVIOUR_KEYS = List.of(
            "bird",
            "boat",
            "burst",
            "composite",
            "dolphin",
            "glider",
            "helicopter",
            "horse",
            "hover",
            "jet",
            "minekart",
            "rocket",
            "submarine",
            "vehicle"
    );

    private static String feedbackMessage = "";
    private static boolean feedbackSuccess = true;
    private static long feedbackUntilMillis;

    private final Screen parent;
    private final List<LabelLine> labelLines = new ArrayList<>();
    private Tab selectedTab = Tab.LOCAL;
    private Section selectedSection = Section.GENERAL;
    private int page;

    public RidingTweaksConfigScreen(Screen parent) {
        super(Component.literal(CobblemonRidingTweaks.MOD_NAME));
        this.parent = parent;
    }

    @Override
    protected void init() {
        labelLines.clear();
        if (selectedTab == Tab.SERVER && !manager().isServerConfigActive()) {
            selectedTab = Tab.LOCAL;
        }
        page = Math.max(0, page);

        int centerX = this.width / 2;
        int top = 54;
        if (manager().isServerConfigActive()) {
            addRenderableWidget(Button.builder(tabText(Tab.LOCAL), button -> {
                selectedTab = Tab.LOCAL;
                rebuild();
            }).bounds(centerX - 104, top, 100, 20).build());

            addRenderableWidget(Button.builder(tabText(Tab.SERVER), button -> {
                selectedTab = Tab.SERVER;
                rebuild();
            }).bounds(centerX + 4, top, 100, 20).build());
        }

        int sectionY = manager().isServerConfigActive() ? 80 : 62;
        addRenderableWidget(Button.builder(Component.literal("<"), button -> changeSection(-1))
                .bounds(centerX - 152, sectionY, 28, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal(selectedSection.title), button -> {
        }).bounds(centerX - 120, sectionY, 240, 20).build()).active = false;
        addRenderableWidget(Button.builder(Component.literal(">"), button -> changeSection(1))
                .bounds(centerX + 124, sectionY, 28, 20)
                .build());

        switch (selectedSection) {
            case GENERAL -> addGeneralControls();
            case STAMINA_LEVEL -> addStaminaLevelControls();
            case STAMINA_RIDE_STYLES -> addMapControls(viewingConfig().stamina.rideStyleMultipliers, RIDE_STYLE_KEYS, false);
            case STAMINA_BEHAVIOURS -> addMapControls(viewingConfig().stamina.behaviourMultipliers, BEHAVIOUR_KEYS, false);
            case STAMINA_LABELS -> addMapControls(viewingConfig().stamina.labelMultipliers, RidingTweaksConfig.knownCobblemonLabels(), true);
            case STAMINA_SPECIES -> addMapControls(viewingConfig().stamina.speciesOverrides, List.of(), true);
            case SPEED_SETTINGS -> addSpeedSettingsControls();
            case SPEED_RIDE_STYLES -> addMapControls(viewingConfig().speed.rideStyleMultipliers, RIDE_STYLE_KEYS, false);
            case SPEED_BEHAVIOURS -> addMapControls(viewingConfig().speed.behaviourMultipliers, BEHAVIOUR_KEYS, false);
            case SPEED_LABELS -> addMapControls(viewingConfig().speed.labelMultipliers, RidingTweaksConfig.knownCobblemonLabels(), true);
            case SPEED_SPECIES -> addMapControls(viewingConfig().speed.speciesOverrides, List.of(), true);
        }

        int bottomY = this.height - 76;
        Button reloadButton = Button.builder(Component.literal("Reload"), button -> {
            manager().reload();
            showFeedback("Reloaded local config.", true);
            rebuild();
        }).bounds(centerX - 152, bottomY, 72, 20).build();
        reloadButton.active = selectedTab == Tab.LOCAL;
        addRenderableWidget(reloadButton);

        Button saveButton = Button.builder(Component.literal("Save"), button -> saveCurrentConfig())
                .bounds(centerX - 76, bottomY, 72, 20)
                .build();
        saveButton.active = selectedTabIsEditable();
        addRenderableWidget(saveButton);

        Button previousPageButton = Button.builder(Component.literal("Page <"), button -> {
            page = Math.max(0, page - 1);
            rebuild();
        }).bounds(centerX, bottomY, 72, 20).build();
        previousPageButton.active = page > 0;
        addRenderableWidget(previousPageButton);

        Button nextPageButton = Button.builder(Component.literal("Page >"), button -> {
            page++;
            rebuild();
        }).bounds(centerX + 76, bottomY, 72, 20).build();
        nextPageButton.active = canGoToNextPage();
        addRenderableWidget(nextPageButton);

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(centerX - 152, bottomY + 24, 304, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        drawCenteredStringWithBacking(graphics, this.title.getString(), 24, 0xFFFFFF, 0x70000000);
        drawCenteredStringWithBacking(graphics, configSummary(), 40, 0xD0D0D0, 0x70000000);
        labelLines.forEach(line -> graphics.drawString(this.font, line.text, line.x, line.y, line.color));
        drawCenteredStringWithBacking(graphics, statusText(), this.height - 30, 0xE0E0E0, 0x85000000);

        String feedback = feedbackText();
        if (!feedback.isBlank()) {
            drawCenteredStringWithBacking(
                    graphics,
                    feedback,
                    this.height - 48,
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

    private void addGeneralControls() {
        RidingTweaksConfig config = viewingConfig();
        int centerX = this.width / 2;
        int y = FIRST_ROW_Y;
        addToggle("Config Enabled", config.enabled, value -> config.enabled = value, centerX, y);
        addToggle("Debug Logging", config.debugLogging, value -> config.debugLogging = value, centerX, y + ROW_HEIGHT);
        addToggle("Stamina Tweaks", config.stamina.enabled, value -> config.stamina.enabled = value, centerX, y + ROW_HEIGHT * 2);
        addToggle("Speed Tweaks", config.speed.enabled, value -> config.speed.enabled = value, centerX, y + ROW_HEIGHT * 3);
        addToggle(
                "Stamina Level Scaling",
                config.stamina.levelScalingEnabled,
                value -> config.stamina.levelScalingEnabled = value,
                centerX,
                y + ROW_HEIGHT * 4
        );
        addLabel("Config Version", config.configVersion, centerX, y + ROW_HEIGHT * 5);
    }

    private void addStaminaLevelControls() {
        RidingTweaksConfig.LevelScaling scaling = viewingConfig().stamina.levelScaling;
        int centerX = this.width / 2;
        int y = FIRST_ROW_Y;
        addIntField("Min Level", () -> scaling.minLevel, value -> scaling.minLevel = value, centerX, y);
        addIntField("Max Level", () -> scaling.maxLevel, value -> scaling.maxLevel = value, centerX, y + ROW_HEIGHT);
        addDoubleField("Min Multiplier", () -> scaling.minMultiplier, value -> scaling.minMultiplier = value, centerX, y + ROW_HEIGHT * 2);
        addDoubleField("Max Multiplier", () -> scaling.maxMultiplier, value -> scaling.maxMultiplier = value, centerX, y + ROW_HEIGHT * 3);
        addDoubleField(
                "Default Label Multiplier",
                () -> viewingConfig().stamina.defaultLabelMultiplier,
                value -> viewingConfig().stamina.defaultLabelMultiplier = value,
                centerX,
                y + ROW_HEIGHT * 4
        );
        addDoubleField(
                "Max Final Multiplier",
                () -> viewingConfig().stamina.maxFinalMultiplier,
                value -> viewingConfig().stamina.maxFinalMultiplier = value,
                centerX,
                y + ROW_HEIGHT * 5
        );
    }

    private void addSpeedSettingsControls() {
        RidingTweaksConfig.SpeedTweaks speed = viewingConfig().speed;
        int centerX = this.width / 2;
        int y = FIRST_ROW_Y;
        addToggle("Speed Tweaks", speed.enabled, value -> speed.enabled = value, centerX, y);
        addDoubleField("Default Label Multiplier", () -> speed.defaultLabelMultiplier, value -> speed.defaultLabelMultiplier = value, centerX, y + ROW_HEIGHT);
        addDoubleField("Max Final Multiplier", () -> speed.maxFinalMultiplier, value -> speed.maxFinalMultiplier = value, centerX, y + ROW_HEIGHT * 2);
    }

    private void addMapControls(Map<String, Double> multipliers, List<String> knownKeys, boolean editableKeys) {
        int centerX = this.width / 2;
        int rowsPerPage = rowsPerPage();
        List<Map.Entry<String, Double>> entries = new ArrayList<>(multipliers.entrySet());
        int start = Math.min(page * rowsPerPage, entries.size());
        int end = Math.min(start + rowsPerPage, entries.size());
        int y = FIRST_ROW_Y;

        for (int index = start; index < end; index++) {
            Map.Entry<String, Double> entry = entries.get(index);
            if (editableKeys) {
                addEditableMapRow(multipliers, entry.getKey(), entry.getValue(), centerX, y);
            } else {
                addMapValueRow(multipliers, entry.getKey(), entry.getValue(), centerX, y);
            }
            y += ROW_HEIGHT;
        }

        int buttonY = FIRST_ROW_Y + rowsPerPage * ROW_HEIGHT + 4;
        if (!knownKeys.isEmpty()) {
            Button addKnownButton = Button.builder(Component.literal("Add Known"), button -> {
                if (addFirstMissingKnownKey(multipliers, knownKeys)) {
                    rebuild();
                }
            }).bounds(centerX - 152, buttonY, 98, 20).build();
            addKnownButton.active = selectedTabIsEditable();
            addRenderableWidget(addKnownButton);
        }

        if (editableKeys) {
            Button addCustomButton = Button.builder(Component.literal(customButtonText()), button -> {
                addCustomKey(multipliers);
                rebuild();
            }).bounds(centerX - 50, buttonY, 98, 20).build();
            addCustomButton.active = selectedTabIsEditable();
            addRenderableWidget(addCustomButton);
        }
    }

    private void addToggle(String label, boolean currentValue, Consumer<Boolean> setter, int centerX, int y) {
        Button button = Button.builder(Component.literal(label + ": " + onOff(currentValue)), pressed -> {
            setter.accept(!currentValue);
            saveLocalChangesIfNeeded();
            rebuild();
        }).bounds(centerX - 40, y, 192, 20).build();
        button.active = selectedTabIsEditable();
        addRenderableWidget(button);
        addRowLabel(label, centerX - 152, y + 6);
    }

    private void addLabel(String label, String value, int centerX, int y) {
        addRowLabel(label, centerX - 152, y + 6);
        addRowLabel(value, centerX - 40, y + 6);
    }

    private void addIntField(String label, Supplier<Integer> getter, Consumer<Integer> setter, int centerX, int y) {
        EditBox box = textBox(centerX - 40, y, 192, String.valueOf(getter.get()));
        box.setResponder(value -> {
            try {
                setter.accept(Math.max(1, Integer.parseInt(value.trim())));
            } catch (NumberFormatException exception) {
                showFeedback(label + " must be a whole number.", false);
            }
        });
        addRenderableWidget(box);
        addRowLabel(label, centerX - 152, y + 6);
    }

    private void addDoubleField(String label, Supplier<Double> getter, Consumer<Double> setter, int centerX, int y) {
        EditBox box = textBox(centerX - 40, y, 192, formatDouble(getter.get()));
        box.setResponder(value -> parseMultiplier(label, value, setter));
        addRenderableWidget(box);
        addRowLabel(label, centerX - 152, y + 6);
    }

    private void addMapValueRow(Map<String, Double> multipliers, String key, double value, int centerX, int y) {
        EditBox valueBox = textBox(centerX + 56, y, 96, formatDouble(value));
        valueBox.setResponder(text -> parseMultiplier(key, text, parsed -> multipliers.put(key, parsed)));
        addRenderableWidget(valueBox);
        addRowLabel(key, centerX - 152, y + 6);
    }

    private void addEditableMapRow(Map<String, Double> multipliers, String key, double value, int centerX, int y) {
        String[] currentKey = { key };
        double[] currentValue = { value };
        EditBox keyBox = textBox(centerX - 152, y, 180, key);
        keyBox.setMaxLength(128);
        keyBox.setResponder(text -> {
            String normalized = normalizeKey(text);
            if (normalized.isBlank() || normalized.equals(currentKey[0])) {
                return;
            }
            if (multipliers.containsKey(normalized)) {
                showFeedback("That key already exists.", false);
                return;
            }
            multipliers.remove(currentKey[0]);
            multipliers.put(normalized, currentValue[0]);
            currentKey[0] = normalized;
        });
        addRenderableWidget(keyBox);

        EditBox valueBox = textBox(centerX + 56, y, 96, formatDouble(value));
        valueBox.setResponder(text -> parseMultiplier(currentKey[0], text, parsed -> {
            currentValue[0] = parsed;
            multipliers.put(currentKey[0], parsed);
        }));
        addRenderableWidget(valueBox);
    }

    private EditBox textBox(int x, int y, int width, String value) {
        EditBox box = new EditBox(this.font, x, y, width, 20, Component.empty());
        box.setValue(value);
        box.setEditable(selectedTabIsEditable());
        box.setMaxLength(64);
        return box;
    }

    private void parseMultiplier(String label, String value, Consumer<Double> setter) {
        try {
            double parsed = Double.parseDouble(value.trim());
            if (!Double.isFinite(parsed) || parsed < 0.01D) {
                showFeedback(label + " must be at least 0.01.", false);
                return;
            }
            setter.accept(parsed);
        } catch (NumberFormatException exception) {
            showFeedback(label + " must be a number.", false);
        }
    }

    private boolean addFirstMissingKnownKey(Map<String, Double> multipliers, List<String> knownKeys) {
        for (String knownKey : knownKeys) {
            String normalized = normalizeKey(knownKey);
            if (!multipliers.containsKey(normalized)) {
                multipliers.put(normalized, 1.0D);
                page = Math.max(0, (multipliers.size() - 1) / rowsPerPage());
                return true;
            }
        }
        showFeedback("All known keys are already listed.", false);
        return false;
    }

    private void addCustomKey(Map<String, Double> multipliers) {
        String prefix = selectedSection.speciesSection ? "cobblemon:species" : "custom_label";
        String key = prefix;
        int suffix = 2;
        while (multipliers.containsKey(key)) {
            key = prefix + "_" + suffix;
            suffix++;
        }
        multipliers.put(key, 1.0D);
        page = Math.max(0, (multipliers.size() - 1) / rowsPerPage());
    }

    private String customButtonText() {
        return selectedSection.speciesSection ? "Add Species" : "Add Custom";
    }

    private boolean canGoToNextPage() {
        if (!selectedSection.mapSection) {
            return false;
        }
        return mapSizeForSection() > (page + 1) * rowsPerPage();
    }

    private int mapSizeForSection() {
        return switch (selectedSection) {
            case STAMINA_RIDE_STYLES -> viewingConfig().stamina.rideStyleMultipliers.size();
            case STAMINA_BEHAVIOURS -> viewingConfig().stamina.behaviourMultipliers.size();
            case STAMINA_LABELS -> viewingConfig().stamina.labelMultipliers.size();
            case STAMINA_SPECIES -> viewingConfig().stamina.speciesOverrides.size();
            case SPEED_RIDE_STYLES -> viewingConfig().speed.rideStyleMultipliers.size();
            case SPEED_BEHAVIOURS -> viewingConfig().speed.behaviourMultipliers.size();
            case SPEED_LABELS -> viewingConfig().speed.labelMultipliers.size();
            case SPEED_SPECIES -> viewingConfig().speed.speciesOverrides.size();
            default -> 0;
        };
    }

    private int rowsPerPage() {
        return Math.max(3, (this.height - FIRST_ROW_Y - 110) / ROW_HEIGHT);
    }

    private void changeSection(int direction) {
        Section[] sections = Section.values();
        int nextIndex = Math.floorMod(selectedSection.ordinal() + direction, sections.length);
        selectedSection = sections[nextIndex];
        page = 0;
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    private Component tabText(Tab tab) {
        return Component.literal((selectedTab == tab ? "> " : "") + tab.displayName);
    }

    private String configSummary() {
        RidingTweaksConfig config = viewingConfig();
        return "Version " + config.configVersion
                + " | stamina x" + formatDouble(config.stamina.levelScaling.minMultiplier)
                + "-x" + formatDouble(config.stamina.levelScaling.maxMultiplier)
                + " | max x" + formatDouble(config.stamina.maxFinalMultiplier);
    }

    private String statusText() {
        if (selectedTab == Tab.SERVER) {
            if (manager().canEditServerConfig()) {
                return "Server config. Save writes to the server and syncs modded clients.";
            }
            return "Server config. Read-only for this player.";
        }
        if (manager().isServerConfigActive()) {
            return "Local config. Used for singleplayer or servers without sync.";
        }
        return fitText(manager().path().toString(), this.width - 40);
    }

    private RidingTweaksConfig viewingConfig() {
        return selectedTab == Tab.SERVER ? manager().config() : manager().localConfig();
    }

    private boolean selectedTabIsEditable() {
        return selectedTab == Tab.LOCAL || manager().canEditServerConfig();
    }

    private void saveCurrentConfig() {
        viewingConfig().sanitize();
        if (selectedTab == Tab.SERVER) {
            showFeedback("Saving server config...", true);
            ClientPlayNetworking.send(new ConfigUpdatePayload(manager().activeConfigJson()));
            return;
        }
        manager().save();
        showFeedback("Saved local config.", true);
        rebuild();
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

    private void addRowLabel(String text, int x, int y) {
        labelLines.add(new LabelLine(fitText(text, 108), x, y, 0xD8D8D8));
    }

    private void drawCenteredStringWithBacking(GuiGraphics graphics, String rawText, int y, int color, int backgroundColor) {
        String text = fitText(rawText, this.width - 40);
        int centerX = this.width / 2;
        int textWidth = this.font.width(text);
        int left = centerX - textWidth / 2 - 5;
        int top = y - 2;
        int right = centerX + textWidth / 2 + 5;
        int bottom = y + this.font.lineHeight + 1;
        graphics.fill(left, top, right, bottom, backgroundColor);
        graphics.drawCenteredString(this.font, text, centerX, y, color);
    }

    private String fitText(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        String shortened = text;
        while (!shortened.isEmpty() && this.font.width(shortened + ellipsis) > maxWidth) {
            shortened = shortened.substring(1);
        }
        return ellipsis + shortened;
    }

    private static String onOff(boolean value) {
        return value ? "On" : "Off";
    }

    private static String formatDouble(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.ROOT, "%.4f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String normalizeKey(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static RidingTweaksConfigManager manager() {
        return CobblemonRidingTweaks.configManager();
    }

    private record LabelLine(String text, int x, int y, int color) {
    }

    private enum Tab {
        LOCAL("Local"),
        SERVER("Server");

        private final String displayName;

        Tab(String displayName) {
            this.displayName = displayName;
        }
    }

    private enum Section {
        GENERAL("General", false, false),
        STAMINA_LEVEL("Stamina Scaling", false, false),
        STAMINA_RIDE_STYLES("Stamina Ride Styles", true, false),
        STAMINA_BEHAVIOURS("Stamina Behaviours", true, false),
        STAMINA_LABELS("Stamina Labels", true, false),
        STAMINA_SPECIES("Stamina Species", true, true),
        SPEED_SETTINGS("Speed Settings", false, false),
        SPEED_RIDE_STYLES("Speed Ride Styles", true, false),
        SPEED_BEHAVIOURS("Speed Behaviours", true, false),
        SPEED_LABELS("Speed Labels", true, false),
        SPEED_SPECIES("Speed Species", true, true);

        private final String title;
        private final boolean mapSection;
        private final boolean speciesSection;

        Section(String title, boolean mapSection, boolean speciesSection) {
            this.title = title;
            this.mapSection = mapSection;
            this.speciesSection = speciesSection;
        }
    }
}
