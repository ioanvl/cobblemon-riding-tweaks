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
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class RidingTweaksConfigScreen extends Screen {
    private static final long FEEDBACK_VISIBLE_MILLIS = 2_500L;
    private static final int ROW_HEIGHT = 24;
    private static final int MIN_MARGIN = 8;
    private static final int MAX_CONTENT_WIDTH = 920;
    private static final int FIELD_GAP = 10;
    private static final int REMOVE_BUTTON_WIDTH = 22;
    private static final List<String> RIDE_STYLE_KEYS = List.of("land", "liquid", "air");
    private static final List<String> LAND_BEHAVIOUR_KEYS = List.of("horse", "minekart", "vehicle");
    private static final List<String> AIR_BEHAVIOUR_KEYS = List.of("bird", "glider", "helicopter", "hover", "jet", "rocket");
    private static final List<String> LIQUID_BEHAVIOUR_KEYS = List.of("boat", "burst", "dolphin", "submarine");
    private static final List<String> OTHER_BEHAVIOUR_KEYS = List.of("composite");

    private static String feedbackMessage = "";
    private static boolean feedbackSuccess = true;
    private static long feedbackUntilMillis;

    private final Screen parent;
    private final List<LabelLine> labelLines = new ArrayList<>();
    private Tab selectedTab = Tab.LOCAL;
    private Section selectedSection = Section.GENERAL;
    private int scrollRow;
    private boolean knownPickerOpen;
    private int knownPickerScroll;
    private RidingTweaksConfig localDraft;
    private RidingTweaksConfig serverDraft;

    public RidingTweaksConfigScreen(Screen parent) {
        super(Component.literal(CobblemonRidingTweaks.MOD_NAME));
        this.parent = parent;
    }

    @Override
    protected void init() {
        labelLines.clear();
        ensureDrafts();
        if (selectedTab == Tab.SERVER && !showServerTabs()) {
            selectedTab = Tab.LOCAL;
        }
        knownPickerScroll = Math.max(0, knownPickerScroll);
        scrollRow = Math.clamp(scrollRow, 0, maxScrollRows());

        int centerX = this.width / 2;
        int contentLeft = contentLeft();
        int contentWidth = contentWidth();
        int top = tabsY();
        if (showServerTabs()) {
            int tabGap = 8;
            int tabWidth = Math.max(72, Math.min(120, (contentWidth - tabGap) / 2));
            addRenderableWidget(Button.builder(tabText(Tab.LOCAL), button -> {
                selectedTab = Tab.LOCAL;
                knownPickerOpen = false;
                rebuild();
            }).bounds(centerX - tabWidth - tabGap / 2, top, tabWidth, 20).build());

            addRenderableWidget(Button.builder(tabText(Tab.SERVER), button -> {
                selectedTab = Tab.SERVER;
                knownPickerOpen = false;
                rebuild();
            }).bounds(centerX + tabGap / 2, top, tabWidth, 20).build());
        }

        int sectionY = sectionY();
        addRenderableWidget(Button.builder(Component.literal("<"), button -> changeSection(-1))
                .bounds(contentLeft, sectionY, 28, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal(selectedSection.title), button -> {
        }).bounds(contentLeft + 36, sectionY, Math.max(40, contentWidth - 72), 20).build()).active = false;
        addRenderableWidget(Button.builder(Component.literal(">"), button -> changeSection(1))
                .bounds(contentLeft + contentWidth - 28, sectionY, 28, 20)
                .build());

        switch (selectedSection) {
            case GENERAL -> addGeneralControls();
            case STAMINA_LEVEL -> addLevelControls(viewingConfig().stamina);
            case STAMINA_RIDE_STYLES -> addRideStyleAndBehaviourControls(viewingConfig().stamina);
            case STAMINA_LABELS -> addLabelControls(viewingConfig().stamina);
            case STAMINA_SPECIES -> addSpeciesControls(viewingConfig().stamina);
            case SPEED_LEVEL -> addLevelControls(viewingConfig().speed);
            case SPEED_RIDE_STYLES -> addRideStyleAndBehaviourControls(viewingConfig().speed);
            case SPEED_LABELS -> addLabelControls(viewingConfig().speed);
            case SPEED_SPECIES -> addSpeciesControls(viewingConfig().speed);
        }

        int bottomY = footerButtonsY();
        int reloadWidth = Math.max(64, Math.min(120, (contentWidth - FIELD_GAP) / 3));
        Button reloadButton = Button.builder(Component.literal("Reload"), button -> {
            if (isSingleplayerSession()) {
                manager().reloadActiveLocal();
            } else {
                manager().reload();
            }
            localDraft = manager().copyLocalConfig();
            showFeedback("Reloaded local config.", true);
            rebuild();
        }).bounds(contentLeft, bottomY, reloadWidth, 20).build();
        reloadButton.active = selectedTab == Tab.LOCAL;
        addRenderableWidget(reloadButton);

        Button saveButton = Button.builder(Component.literal("Save"), button -> saveCurrentConfig())
                .bounds(contentLeft + reloadWidth + FIELD_GAP, bottomY, contentWidth - reloadWidth - FIELD_GAP, 20)
                .build();
        saveButton.active = selectedTabIsEditable();
        addRenderableWidget(saveButton);

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(contentLeft, bottomY + 24, contentWidth, 20)
                .build());

        if (knownPickerOpen) {
            addKnownPickerControls();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        if (knownPickerOpen) {
            drawKnownPickerBacking(graphics);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        drawCenteredStringWithBacking(graphics, this.title.getString(), titleY(), 0xFFFFFF, 0x70000000);
        drawCenteredStringWithBacking(graphics, configSummary(), summaryY(), 0xD0D0D0, 0x70000000);
        if (!knownPickerOpen) {
            labelLines.forEach(line -> graphics.drawString(this.font, line.text, line.x, line.y, line.color));
            drawMapScrollBar(graphics);
        }
        String status = statusText();
        if (!status.isBlank()) {
            drawCenteredStringWithBacking(graphics, status, statusY(), 0xE0E0E0, 0x85000000);
        }

        String feedback = feedbackText();
        if (!feedback.isBlank()) {
            drawCenteredStringWithBacking(
                    graphics,
                    feedback,
                    feedbackY(),
                    feedbackSuccess ? 0x80FF80 : 0xFF8080,
                    0xA0000000
            );
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (knownPickerOpen) {
            List<String> missing = missingKnownKeys(currentMap(), RidingTweaksConfig.knownCobblemonLabels());
            int maxScroll = Math.max(0, missing.size() - knownPickerRows());
            knownPickerScroll = Math.clamp(knownPickerScroll - (int) Math.signum(verticalAmount), 0, maxScroll);
            rebuild();
            return true;
        }

        int maxScroll = maxScrollRows();
        if (maxScroll > 0) {
            scrollRow = Math.clamp(scrollRow - (int) Math.signum(verticalAmount), 0, maxScroll);
            rebuild();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (knownPickerOpen) {
            handleKnownPickerClick(mouseX, mouseY, button);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
        addToggle("Mod Enabled", config.enabled, value -> config.enabled = value, centerX, rowY(0), 0);
        addToggle("Debug Logging", config.debugLogging, value -> config.debugLogging = value, centerX, rowY(1), 0);

        addHeader("Stamina", 2);
        addToggle("Stamina Tweaks", config.stamina.enabled, value -> config.stamina.enabled = value, centerX, rowY(3), 0);
        addToggle("Level Scaling", config.stamina.levelScalingEnabled, value -> config.stamina.levelScalingEnabled = value, centerX, rowY(4), 18);
        addToggle("Ride Styles", config.stamina.ridingMultipliersEnabled, value -> config.stamina.ridingMultipliersEnabled = value, centerX, rowY(5), 18);
        addToggle("Labels", config.stamina.labelMultipliersEnabled, value -> config.stamina.labelMultipliersEnabled = value, centerX, rowY(6), 18);
        addToggle("Species", config.stamina.speciesOverridesEnabled, value -> config.stamina.speciesOverridesEnabled = value, centerX, rowY(7), 18);
        if (shouldShowRow(8)) {
            addDoubleField("Min Final Multiplier", () -> config.stamina.minFinalMultiplier, value -> config.stamina.minFinalMultiplier = value, centerX, rowY(8), 18);
        }
        if (shouldShowRow(9)) {
            addDoubleField("Max Final Multiplier", () -> config.stamina.maxFinalMultiplier, value -> config.stamina.maxFinalMultiplier = value, centerX, rowY(9), 18);
        }

        addHeader("Speed", 10);
        addToggle("Speed Tweaks", config.speed.enabled, value -> config.speed.enabled = value, centerX, rowY(11), 0);
        addToggle("Level Scaling", config.speed.levelScalingEnabled, value -> config.speed.levelScalingEnabled = value, centerX, rowY(12), 18);
        addToggle("Ride Styles", config.speed.ridingMultipliersEnabled, value -> config.speed.ridingMultipliersEnabled = value, centerX, rowY(13), 18);
        addToggle("Labels", config.speed.labelMultipliersEnabled, value -> config.speed.labelMultipliersEnabled = value, centerX, rowY(14), 18);
        addToggle("Species", config.speed.speciesOverridesEnabled, value -> config.speed.speciesOverridesEnabled = value, centerX, rowY(15), 18);
        if (shouldShowRow(16)) {
            addDoubleField("Min Final Multiplier", () -> config.speed.minFinalMultiplier, value -> config.speed.minFinalMultiplier = value, centerX, rowY(16), 18);
        }
        if (shouldShowRow(17)) {
            addDoubleField("Max Final Multiplier", () -> config.speed.maxFinalMultiplier, value -> config.speed.maxFinalMultiplier = value, centerX, rowY(17), 18);
        }

        addHeader("Version", 18);
        if (shouldShowRow(19)) {
            addLabel("Config Version", config.configVersion, centerX, rowY(19));
        }
    }

    private void addLevelControls(RidingTweaksConfig.FeatureTweaks feature) {
        RidingTweaksConfig.LevelScaling scaling = feature.levelScaling;
        int centerX = this.width / 2;
        addHeader("Level Scaling", 0);
        addToggle("Enabled", feature.levelScalingEnabled, value -> feature.levelScalingEnabled = value, centerX, rowY(1), 0);
        if (shouldShowRow(2)) {
            addIntField("Min Level", () -> scaling.minLevel, value -> scaling.minLevel = value, centerX, rowY(2));
        }
        if (shouldShowRow(3)) {
            addIntField("Max Level", () -> scaling.maxLevel, value -> scaling.maxLevel = value, centerX, rowY(3));
        }
        if (shouldShowRow(4)) {
            addDoubleField("Min Level Multiplier", () -> scaling.minMultiplier, value -> scaling.minMultiplier = value, centerX, rowY(4));
        }
        if (shouldShowRow(5)) {
            addDoubleField("Max Level Multiplier", () -> scaling.maxMultiplier, value -> scaling.maxMultiplier = value, centerX, rowY(5));
        }
    }

    private void addRideStyleAndBehaviourControls(RidingTweaksConfig.FeatureTweaks feature) {
        int centerX = this.width / 2;
        addToggle("Ride Styles & Behaviours", feature.ridingMultipliersEnabled, value -> feature.ridingMultipliersEnabled = value, centerX, rowY(0), 0);
        addHeader("Ride Styles", 1);
        addMapEntries(feature.rideStyleMultipliers, false, 2, RIDE_STYLE_KEYS);

        int behaviourHeaderRow = 2 + RIDE_STYLE_KEYS.size();
        addHeader("Behaviours", behaviourHeaderRow);
        int row = behaviourHeaderRow + 1;
        row = addBehaviourGroup(feature, "Land", LAND_BEHAVIOUR_KEYS, row);
        row = addBehaviourGroup(feature, "Air", AIR_BEHAVIOUR_KEYS, row);
        row = addBehaviourGroup(feature, "Liquid", LIQUID_BEHAVIOUR_KEYS, row);
        addBehaviourGroup(feature, "Other", OTHER_BEHAVIOUR_KEYS, row);
    }

    private int addBehaviourGroup(RidingTweaksConfig.FeatureTweaks feature, String title, List<String> keys, int startRow) {
        addSubHeader(title, startRow);
        addMapEntries(feature.behaviourMultipliers, false, startRow + 1, keys);
        return startRow + 1 + keys.size();
    }

    private void addLabelControls(RidingTweaksConfig.FeatureTweaks feature) {
        int centerX = this.width / 2;
        addToggle("Label Multipliers", feature.labelMultipliersEnabled, value -> feature.labelMultipliersEnabled = value, centerX, rowY(0), 0);
        if (shouldShowRow(1)) {
            addDoubleField("Default Multiplier", () -> feature.defaultLabelMultiplier, value -> feature.defaultLabelMultiplier = value, centerX, rowY(1));
        }
        addHeader("Labels", 2);
        addMapEntries(feature.labelMultipliers, true, 3, new ArrayList<>(feature.labelMultipliers.keySet()));
        addMapButtons(feature.labelMultipliers, RidingTweaksConfig.knownCobblemonLabels(), true);
    }

    private void addSpeciesControls(RidingTweaksConfig.FeatureTweaks feature) {
        int centerX = this.width / 2;
        addToggle("Species Overrides", feature.speciesOverridesEnabled, value -> feature.speciesOverridesEnabled = value, centerX, rowY(0), 0);
        addHeader("Overrides", 1);
        addMapEntries(feature.speciesOverrides, true, 2, new ArrayList<>(feature.speciesOverrides.keySet()));
        addMapButtons(feature.speciesOverrides, List.of(), true);
    }

    private void addMapEntries(Map<String, Double> multipliers, boolean editableKeys, int startRow, List<String> keys) {
        int centerX = this.width / 2;
        for (int index = 0; index < keys.size(); index++) {
            String key = keys.get(index);
            int row = startRow + index;
            if (!shouldShowRow(row)) {
                continue;
            }
            double value = multipliers.getOrDefault(key, 1.0D);
            if (editableKeys) {
                addEditableMapRow(multipliers, key, value, centerX, rowY(row));
            } else {
                addMapValueRow(multipliers, key, value, centerX, rowY(row));
            }
        }
    }

    private void addMapButtons(Map<String, Double> multipliers, List<String> knownKeys, boolean editableKeys) {
        int buttonY = rowsTop() + visibleRows() * ROW_HEIGHT + 4;
        if (selectedSection.labelSection && !knownKeys.isEmpty()) {
            Button addKnownButton = Button.builder(Component.literal("Add Known"), button -> {
                knownPickerOpen = true;
                knownPickerScroll = 0;
                rebuild();
            }).bounds(contentLeft(), buttonY, addButtonWidth(editableKeys), 20).build();
            addKnownButton.active = selectedTabIsEditable();
            addRenderableWidget(addKnownButton);
        }

        if (editableKeys) {
            Button addCustomButton = Button.builder(Component.literal(customButtonText()), button -> {
                addCustomKey(multipliers);
                rebuild();
            }).bounds(addCustomButtonX(), buttonY, addButtonWidth(selectedSection.labelSection), 20).build();
            addCustomButton.active = selectedTabIsEditable();
            addRenderableWidget(addCustomButton);
        }
    }

    private void addHeader(String label, int row) {
        if (shouldShowRow(row)) {
            labelLines.add(new LabelLine(fitText(label, contentWidth()), contentLeft(), rowY(row) + 6, 0xFFE080));
        }
    }

    private void addSubHeader(String label, int row) {
        if (shouldShowRow(row)) {
            labelLines.add(new LabelLine(fitText(label, labelWidth() - 12), labelX() + 12, rowY(row) + 6, 0xC8D8FF));
        }
    }

    private void addToggle(String label, boolean currentValue, Consumer<Boolean> setter, int centerX, int y) {
        addToggle(label, currentValue, setter, centerX, y, 0);
    }

    private void addToggle(String label, boolean currentValue, Consumer<Boolean> setter, int centerX, int y, int indent) {
        if (y < rowsTop() || y >= rowViewportBottom()) {
            return;
        }
        Button button = Button.builder(Component.literal(onOff(currentValue)), pressed -> {
            setter.accept(!currentValue);
            rebuild();
        }).bounds(valueX(), y, valueWidth(), 20).build();
        button.active = selectedTabIsEditable();
        addRenderableWidget(button);
        addRowLabel(label, labelX() + indent, y + 6, labelWidth() - indent);
    }

    private void addLabel(String label, String value, int centerX, int y) {
        addRowLabel(label, labelX(), y + 6);
        addRowLabel(value, valueX(), y + 6);
    }

    private void addIntField(String label, Supplier<Integer> getter, Consumer<Integer> setter, int centerX, int y) {
        EditBox box = textBox(valueX(), y, valueWidth(), String.valueOf(getter.get()));
        box.setResponder(value -> {
            try {
                setter.accept(Math.max(1, Integer.parseInt(value.trim())));
            } catch (NumberFormatException exception) {
                showFeedback(label + " must be a whole number.", false);
            }
        });
        addRenderableWidget(box);
        addRowLabel(label, labelX(), y + 6);
    }

    private void addDoubleField(String label, Supplier<Double> getter, Consumer<Double> setter, int centerX, int y) {
        addDoubleField(label, getter, setter, centerX, y, 0);
    }

    private void addDoubleField(String label, Supplier<Double> getter, Consumer<Double> setter, int centerX, int y, int indent) {
        EditBox box = textBox(valueX(), y, valueWidth(), formatDouble(getter.get()));
        box.setResponder(value -> parseMultiplier(label, value, setter));
        addRenderableWidget(box);
        addRowLabel(label, labelX() + indent, y + 6, labelWidth() - indent);
    }

    private void addMapValueRow(Map<String, Double> multipliers, String key, double value, int centerX, int y) {
        EditBox valueBox = textBox(valueX(), y, valueWidth(), formatDouble(value));
        valueBox.setResponder(text -> parseMultiplier(key, text, parsed -> multipliers.put(key, parsed)));
        addRenderableWidget(valueBox);
        addRowLabel(key, labelX(), y + 6);
    }

    private void addEditableMapRow(Map<String, Double> multipliers, String key, double value, int centerX, int y) {
        String[] currentKey = { key };
        double[] currentValue = { value };
        EditBox keyBox = textBox(editableKeyX(), y, editableKeyWidth(), key);
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

        EditBox valueBox = textBox(editableValueX(), y, editableValueWidth(), formatDouble(value));
        valueBox.setResponder(text -> parseMultiplier(currentKey[0], text, parsed -> {
            currentValue[0] = parsed;
            multipliers.put(currentKey[0], parsed);
        }));
        addRenderableWidget(valueBox);

        Button removeButton = Button.builder(Component.literal("X"), button -> {
            multipliers.remove(currentKey[0]);
            scrollRow = Math.max(0, scrollRow - 1);
            rebuild();
        }).bounds(removeButtonX(), y, REMOVE_BUTTON_WIDTH, 20).build();
        removeButton.active = selectedTabIsEditable();
        addRenderableWidget(removeButton);
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

    private void addCustomKey(Map<String, Double> multipliers) {
        String prefix = selectedSection.speciesSection ? "cobblemon:species" : "custom_label";
        String key = prefix;
        int suffix = 2;
        while (multipliers.containsKey(key)) {
            key = prefix + "_" + suffix;
            suffix++;
        }
        multipliers.put(key, 1.0D);
        scrollRow = Math.max(0, rowCountForSection() - visibleRows());
    }

    private String customButtonText() {
        return selectedSection.speciesSection ? "Add Species" : "Add Custom";
    }

    private Map<String, Double> currentMap() {
        return switch (selectedSection) {
            case STAMINA_LABELS -> viewingConfig().stamina.labelMultipliers;
            case STAMINA_SPECIES -> viewingConfig().stamina.speciesOverrides;
            case SPEED_LABELS -> viewingConfig().speed.labelMultipliers;
            case SPEED_SPECIES -> viewingConfig().speed.speciesOverrides;
            default -> Map.of();
        };
    }

    private int visibleRows() {
        int reservedForAddButtons = selectedSection.mapSection && (selectedSection.labelSection || selectedSection.speciesSection)
                ? 28
                : 0;
        return Math.max(1, (rowViewportBottom() - rowsTop() - reservedForAddButtons) / ROW_HEIGHT);
    }

    private int rowCountForSection() {
        return switch (selectedSection) {
            case GENERAL -> 20;
            case STAMINA_LEVEL, SPEED_LEVEL -> 6;
            case STAMINA_RIDE_STYLES, SPEED_RIDE_STYLES -> rideStyleAndBehaviourRowCount();
            case STAMINA_LABELS, SPEED_LABELS -> 3 + currentMap().size();
            case STAMINA_SPECIES, SPEED_SPECIES -> 2 + currentMap().size();
        };
    }

    private int rideStyleAndBehaviourRowCount() {
        return 1
                + 1
                + RIDE_STYLE_KEYS.size()
                + 1
                + 1 + LAND_BEHAVIOUR_KEYS.size()
                + 1 + AIR_BEHAVIOUR_KEYS.size()
                + 1 + LIQUID_BEHAVIOUR_KEYS.size()
                + 1 + OTHER_BEHAVIOUR_KEYS.size();
    }

    private int maxScrollRows() {
        return Math.max(0, rowCountForSection() - visibleRows());
    }

    private boolean shouldShowRow(int rowIndex) {
        return rowIndex >= scrollRow && rowIndex < scrollRow + visibleRows();
    }

    private int rowY(int rowIndex) {
        return rowsTop() + (rowIndex - scrollRow) * ROW_HEIGHT;
    }

    private int titleY() {
        return this.height < 260 ? 12 : 24;
    }

    private int summaryY() {
        return titleY() + 16;
    }

    private int tabsY() {
        return summaryY() + 18;
    }

    private int sectionY() {
        return showServerTabs() ? tabsY() + 24 : summaryY() + 24;
    }

    private int rowsTop() {
        return sectionY() + 34;
    }

    private int footerButtonsY() {
        return Math.max(0, this.height - 52);
    }

    private int statusY() {
        return Math.max(rowsTop() + 4, footerButtonsY() - 16);
    }

    private int feedbackY() {
        return Math.max(rowsTop() + 4, statusY() - 16);
    }

    private int rowViewportBottom() {
        return Math.max(rowsTop() + ROW_HEIGHT, statusY() - 4);
    }

    private int margin() {
        return Math.max(MIN_MARGIN, Math.min(24, this.width / 24));
    }

    private int contentWidth() {
        int availableWidth = Math.max(80, this.width - margin() * 2);
        return Math.min(MAX_CONTENT_WIDTH, availableWidth);
    }

    private int contentLeft() {
        return (this.width - contentWidth()) / 2;
    }

    private int contentRight() {
        return contentLeft() + contentWidth();
    }

    private int valueWidth() {
        return Math.max(70, Math.min(180, contentWidth() / 4));
    }

    private int labelX() {
        return contentLeft();
    }

    private int labelWidth() {
        return Math.max(40, valueX() - labelX() - FIELD_GAP);
    }

    private int valueX() {
        return contentRight() - valueWidth();
    }

    private int editableKeyX() {
        return contentLeft();
    }

    private int editableValueWidth() {
        return Math.max(64, Math.min(120, contentWidth() / 5));
    }

    private int editableValueX() {
        return contentRight() - REMOVE_BUTTON_WIDTH - FIELD_GAP - editableValueWidth();
    }

    private int editableKeyWidth() {
        return Math.max(60, editableValueX() - editableKeyX() - FIELD_GAP);
    }

    private int removeButtonX() {
        return contentRight() - REMOVE_BUTTON_WIDTH;
    }

    private int addButtonWidth(boolean splitButtons) {
        return splitButtons ? Math.max(64, (contentWidth() - FIELD_GAP) / 2) : contentWidth();
    }

    private int addCustomButtonX() {
        return selectedSection.labelSection ? contentLeft() + addButtonWidth(true) + FIELD_GAP : contentLeft();
    }

    private void changeSection(int direction) {
        Section[] sections = Section.values();
        int nextIndex = Math.floorMod(selectedSection.ordinal() + direction, sections.length);
        selectedSection = sections[nextIndex];
        scrollRow = 0;
        knownPickerOpen = false;
        knownPickerScroll = 0;
        rebuild();
    }

    private void addKnownPickerControls() {
        List<String> missing = missingKnownKeys(currentMap(), RidingTweaksConfig.knownCobblemonLabels());
        int pickerWidth = knownPickerWidth();
        int left = (this.width - pickerWidth) / 2;
        int top = knownPickerTop();
        int rows = Math.min(knownPickerRows(), missing.size());
        knownPickerScroll = Math.clamp(knownPickerScroll, 0, Math.max(0, missing.size() - rows));

        addRenderableWidget(Button.builder(Component.literal("Known Labels"), button -> {
        }).bounds(left, top, pickerWidth, 20).build()).active = false;

        if (missing.isEmpty()) {
            addRenderableWidget(Button.builder(Component.literal("All known labels are listed"), button -> {
            }).bounds(left, top + 24, pickerWidth, 20).build()).active = false;
        } else {
            for (int row = 0; row < rows; row++) {
                String key = missing.get(knownPickerScroll + row);
                addRenderableWidget(Button.builder(Component.literal(key), button -> {
                    currentMap().put(key, 1.0D);
                    knownPickerOpen = false;
                    scrollRow = Math.max(0, currentMap().size() - visibleRows());
                    rebuild();
                }).bounds(left, top + 24 + row * ROW_HEIGHT, pickerWidth, 20).build()).active = selectedTabIsEditable();
            }
        }

        int closeY = top + 28 + knownPickerRows() * ROW_HEIGHT;
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> {
            knownPickerOpen = false;
            rebuild();
        }).bounds(left, closeY, pickerWidth, 20).build());
    }

    private void handleKnownPickerClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return;
        }

        List<String> missing = missingKnownKeys(currentMap(), RidingTweaksConfig.knownCobblemonLabels());
        int pickerWidth = knownPickerWidth();
        int left = (this.width - pickerWidth) / 2;
        int top = knownPickerTop();
        int rows = Math.min(knownPickerRows(), missing.size());

        if (selectedTabIsEditable()) {
            for (int row = 0; row < rows; row++) {
                int rowY = top + 24 + row * ROW_HEIGHT;
                if (isWithin(mouseX, mouseY, left, rowY, pickerWidth, 20)) {
                    String key = missing.get(knownPickerScroll + row);
                    currentMap().put(key, 1.0D);
                    knownPickerOpen = false;
                    scrollRow = Math.max(0, currentMap().size() - visibleRows());
                    rebuild();
                    return;
                }
            }
        }

        int closeY = top + 28 + knownPickerRows() * ROW_HEIGHT;
        if (isWithin(mouseX, mouseY, left, closeY, pickerWidth, 20)) {
            knownPickerOpen = false;
            rebuild();
        }
    }

    private static boolean isWithin(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private List<String> missingKnownKeys(Map<String, Double> multipliers, List<String> knownKeys) {
        return knownKeys.stream()
                .map(RidingTweaksConfigScreen::normalizeKey)
                .filter(key -> !multipliers.containsKey(key))
                .toList();
    }

    private int knownPickerRows() {
        int availableRows = (footerButtonsY() - knownPickerTop() - 72) / ROW_HEIGHT;
        return Math.max(1, Math.min(10, availableRows));
    }

    private int knownPickerTop() {
        return Math.max(rowsTop(), Math.min(this.height / 2 - 138, footerButtonsY() - 40));
    }

    private int knownPickerWidth() {
        return Math.min(260, contentWidth());
    }

    private void drawKnownPickerBacking(GuiGraphics graphics) {
        int pickerWidth = knownPickerWidth();
        int left = (this.width - pickerWidth) / 2 - 8;
        int top = knownPickerTop() - 8;
        int right = left + pickerWidth + 16;
        int bottom = top + 68 + knownPickerRows() * ROW_HEIGHT;
        graphics.fill(left, top, right, bottom, 0xD0000000);
        graphics.fill(left + 2, top + 2, right - 2, bottom - 2, 0xE0202020);
    }

    private void drawMapScrollBar(GuiGraphics graphics) {
        if (knownPickerOpen) {
            return;
        }

        int totalRows = rowCountForSection();
        int visibleRows = visibleRows();
        if (totalRows <= visibleRows) {
            return;
        }

        int trackX = Math.min(this.width - 6, contentRight() + 4);
        int trackTop = rowsTop();
        int trackHeight = Math.max(1, visibleRows * ROW_HEIGHT - 4);
        int handleHeight = Math.max(18, trackHeight * visibleRows / totalRows);
        int maxScroll = totalRows - visibleRows;
        int handleY = trackTop + (trackHeight - handleHeight) * scrollRow / maxScroll;
        graphics.fill(trackX, trackTop, trackX + 4, trackTop + trackHeight, 0x90000000);
        graphics.fill(trackX, handleY, trackX + 4, handleY + handleHeight, 0xFFD0D0D0);
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
                + " | final x" + formatDouble(config.stamina.minFinalMultiplier)
                + "-x" + formatDouble(config.stamina.maxFinalMultiplier);
    }

    private String statusText() {
        if (selectedTab == Tab.SERVER) {
            if (manager().canEditServerConfig()) {
                return "Server config. Save writes to the server and syncs modded clients.";
            }
            return "Server config. Read-only for this player.";
        }
        if (isSingleplayerSession()) {
            return "";
        }
        if (manager().isServerConfigActive()) {
            return "Local config. Stored locally; server values are active.";
        }
        if (manager().isAwaitingServerConfig()) {
            return "No server sync. Neutral x1 multipliers are active on this server.";
        }
        return fitText(manager().path().toString(), this.width - 40);
    }

    private boolean showServerTabs() {
        return manager().isServerConfigActive() && !isSingleplayerSession();
    }

    private boolean isSingleplayerSession() {
        return this.minecraft != null && this.minecraft.hasSingleplayerServer();
    }

    private RidingTweaksConfig viewingConfig() {
        ensureDrafts();
        return selectedTab == Tab.SERVER ? serverDraft : localDraft;
    }

    private boolean selectedTabIsEditable() {
        return selectedTab == Tab.LOCAL || manager().canEditServerConfig();
    }

    private void saveCurrentConfig() {
        RidingTweaksConfig draft = viewingConfig().sanitize();
        if (selectedTab == Tab.SERVER) {
            showFeedback("Saving server config...", true);
            ClientPlayNetworking.send(new ConfigUpdatePayload(manager().toJson(draft)));
            return;
        }
        if (isSingleplayerSession()) {
            manager().replaceAndSaveActiveLocal(draft);
        } else {
            manager().replaceAndSave(draft);
        }
        localDraft = manager().copyLocalConfig();
        showFeedback("Saved and reloaded local config.", true);
        rebuild();
    }

    private void ensureDrafts() {
        if (localDraft == null) {
            localDraft = manager().copyLocalConfig();
        }
        if (serverDraft == null) {
            serverDraft = manager().copyActiveConfig();
        }
    }

    private String feedbackText() {
        if (feedbackMessage.isBlank() || System.currentTimeMillis() > feedbackUntilMillis) {
            return "";
        }
        return feedbackMessage;
    }

    private void addRowLabel(String text, int x, int y) {
        addRowLabel(text, x, y, labelWidth());
    }

    private void addRowLabel(String text, int x, int y, int maxWidth) {
        labelLines.add(new LabelLine(fitText(text, Math.max(20, maxWidth)), x, y, 0xD8D8D8));
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
        GENERAL("General", false, false, false),
        STAMINA_LEVEL("Stamina - Scaling", false, false, false),
        STAMINA_RIDE_STYLES("Stamina - Ride Styles", false, false, false),
        STAMINA_LABELS("Stamina - Labels", true, true, false),
        STAMINA_SPECIES("Stamina - Species", true, false, true),
        SPEED_LEVEL("Speed - Scaling", false, false, false),
        SPEED_RIDE_STYLES("Speed - Ride Styles", false, false, false),
        SPEED_LABELS("Speed - Labels", true, true, false),
        SPEED_SPECIES("Speed - Species", true, false, true);

        private final String title;
        private final boolean mapSection;
        private final boolean labelSection;
        private final boolean speciesSection;

        Section(String title, boolean mapSection, boolean labelSection, boolean speciesSection) {
            this.title = title;
            this.mapSection = mapSection;
            this.labelSection = labelSection;
            this.speciesSection = speciesSection;
        }
    }
}
