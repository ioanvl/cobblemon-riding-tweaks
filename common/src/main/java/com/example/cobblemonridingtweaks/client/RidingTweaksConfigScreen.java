package com.example.cobblemonridingtweaks.client;

import com.example.cobblemonridingtweaks.CobblemonRidingTweaks;
import com.example.cobblemonridingtweaks.config.RidingTweaksConfig;
import com.example.cobblemonridingtweaks.config.RidingTweaksConfigManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

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
    private static final int MAX_CONTENT_WIDTH = 420;
    private static final int FIELD_GAP = 10;
    private static final int REMOVE_BUTTON_WIDTH = 22;
    private static final int NORMAL_TEXT_COLOR = 0xD8D8D8;
    private static final int DIMMED_TEXT_COLOR = 0x8C8C8C;
    private static final int HEADER_TEXT_COLOR = 0xFFE080;
    private static final int SUBHEADER_TEXT_COLOR = 0xC8D8FF;
    private static final List<String> RIDE_STYLE_KEYS = List.of("land", "liquid", "air");
    private static final List<String> LAND_BEHAVIOUR_KEYS = List.of("horse", "minekart", "vehicle");
    private static final List<String> AIR_BEHAVIOUR_KEYS = List.of("bird", "glider", "helicopter", "hover", "jet", "rocket");
    private static final List<String> LIQUID_BEHAVIOUR_KEYS = List.of("boat", "burst", "dolphin", "submarine");

    private static String feedbackMessage = "";
    private static boolean feedbackSuccess = true;
    private static long feedbackUntilMillis;
    private static ServerConfigUpdateSender serverConfigUpdateSender = json ->
            showFeedback("Server config editing is not available on this loader.", false);

    private final Screen parent;
    private final List<LabelLine> labelLines = new ArrayList<>();
    private final List<TooltipArea> tooltipAreas = new ArrayList<>();
    private Tab selectedTab = Tab.LOCAL;
    private Section selectedSection = Section.GENERAL;
    private int scrollRow;
    private boolean sectionPickerOpen;
    private int sectionPickerScroll;
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
        tooltipAreas.clear();
        ensureDrafts();
        if (selectedTab == Tab.SERVER && !showServerTabs()) {
            selectedTab = Tab.LOCAL;
        }
        knownPickerScroll = Math.max(0, knownPickerScroll);
        sectionPickerScroll = Math.clamp(sectionPickerScroll, 0, maxSectionPickerScroll());
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
                sectionPickerOpen = false;
                rebuild();
            }).bounds(centerX - tabWidth - tabGap / 2, top, tabWidth, 20).build());

            addRenderableWidget(Button.builder(tabText(Tab.SERVER), button -> {
                selectedTab = Tab.SERVER;
                knownPickerOpen = false;
                sectionPickerOpen = false;
                rebuild();
            }).bounds(centerX + tabGap / 2, top, tabWidth, 20).build());
        }

        int sectionY = sectionY();
        addRenderableWidget(Button.builder(Component.literal("<"), button -> changeSection(-1))
                .bounds(contentLeft, sectionY, 28, 20)
                .build());
        addRenderableWidget(Button.builder(sectionText(), button -> {
            sectionPickerOpen = !sectionPickerOpen;
            knownPickerOpen = false;
            rebuild();
        }).bounds(contentLeft + 36, sectionY, Math.max(40, contentWidth - 72), 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> changeSection(1))
                .bounds(contentLeft + contentWidth - 28, sectionY, 28, 20)
                .build());

        if (!knownPickerOpen && !sectionPickerOpen) {
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
                if (!isActiveMultiplayerSession()) {
                    manager().reloadActiveLocal();
                    showFeedback(isSingleplayerSession() ? "Reloaded and applied config." : "Reloaded config.", true);
                } else {
                    manager().reload();
                    showFeedback("Reloaded local config.", true);
                }
                localDraft = manager().copyLocalConfig();
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
        }

        if (knownPickerOpen) {
            addKnownPickerControls();
        }
        if (sectionPickerOpen) {
            addSectionPickerControls();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        if (knownPickerOpen) {
            drawKnownPickerBacking(graphics);
        }
        if (sectionPickerOpen) {
            drawSectionPickerBacking(graphics);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (knownPickerOpen) {
            drawKnownPickerScrollBar(graphics);
        }
        if (sectionPickerOpen) {
            drawSectionPickerScrollBar(graphics);
        }
        drawCenteredStringWithBacking(graphics, this.title.getString(), titleY(), 0xFFFFFF, 0x70000000);
        drawCenteredStringWithBacking(graphics, configSummary(), summaryY(), 0xD0D0D0, 0x70000000);
        if (!knownPickerOpen && !sectionPickerOpen) {
            labelLines.forEach(line -> graphics.drawString(this.font, line.text, line.x, line.y, line.color));
            drawMapScrollBar(graphics);
        }
        String status = statusText();
        if (!status.isBlank()) {
            drawCenteredStringWithBacking(graphics, status, statusY(), 0xE0E0E0, 0x85000000);
        }

        String unsavedChanges = unsavedChangesText();
        if (!unsavedChanges.isBlank()) {
            drawCenteredStringWithBacking(
                    graphics,
                    unsavedChanges,
                    unsavedChangesY(!status.isBlank()),
                    0xFFE080,
                    0xA0000000
            );
        }

        String feedback = feedbackText();
        if (!feedback.isBlank()) {
            drawCenteredStringWithBacking(
                    graphics,
                    feedback,
                    feedbackY(!unsavedChanges.isBlank(), !status.isBlank()),
                    feedbackSuccess ? 0x80FF80 : 0xFF8080,
                    0xA0000000
            );
        }
        drawLabelTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (sectionPickerOpen) {
            sectionPickerScroll = Math.clamp(
                    sectionPickerScroll - (int) Math.signum(verticalAmount),
                    0,
                    maxSectionPickerScroll()
            );
            rebuild();
            return true;
        }

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
        if (sectionPickerOpen) {
            if (handleTabClick(mouseX, mouseY, button)) {
                return true;
            }
            handleSectionPickerClick(mouseX, mouseY, button);
            return true;
        }
        if (knownPickerOpen) {
            handleKnownPickerClick(mouseX, mouseY, button);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && (knownPickerOpen || sectionPickerOpen)) {
            knownPickerOpen = false;
            sectionPickerOpen = false;
            rebuild();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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

    public static void setServerConfigUpdateSender(ServerConfigUpdateSender sender) {
        serverConfigUpdateSender = sender == null
                ? json -> showFeedback("Server config editing is not available on this loader.", false)
                : sender;
    }

    private void addGeneralControls() {
        RidingTweaksConfig config = viewingConfig();
        int centerX = this.width / 2;
        addToggle("Mod Enabled", config.enabled, value -> config.enabled = value, centerX, rowY(0), 0,
                "Turns this config on or off. Off leaves stamina and speed at neutral x1.");

        addHeader("Stamina", 1);
        addToggle("Enabled", config.stamina.enabled, value -> config.stamina.enabled = value, centerX, rowY(2), 0,
                "Master switch for stamina multipliers. Off keeps Cobblemon's normal stamina drain.");
        addStackingModeToggle("Multiplier Mode", config.stamina, centerX, rowY(3), 0);
        if (shouldShowRow(4)) {
            addDoubleField("Global Multiplier", () -> config.stamina.globalMultiplier, value -> config.stamina.globalMultiplier = value, centerX, rowY(4), 18,
                    "Always-on stamina multiplier. It combines with the other enabled stamina factors.");
        }
        addToggle("Scaling", config.stamina.levelScalingEnabled, value -> config.stamina.levelScalingEnabled = value, centerX, rowY(5), 18,
                "Enables stamina scaling options for level and the selected stat's nature, IV, and EV.");
        addToggle("Ride Styles & Behaviours", config.stamina.ridingMultipliersEnabled, value -> config.stamina.ridingMultipliersEnabled = value, centerX, rowY(6), 18,
                "Applies stamina multipliers for the active ride style and behaviour, such as air/jet or land/horse.");
        addToggle("Labels", config.stamina.labelMultipliersEnabled, value -> config.stamina.labelMultipliersEnabled = value, centerX, rowY(7), 18,
                "Applies stamina multipliers from Cobblemon form labels. Label Behaviour controls highest or stacking.");
        addToggle("Species", config.stamina.speciesOverridesEnabled, value -> config.stamina.speciesOverridesEnabled = value, centerX, rowY(8), 18,
                "Applies per-species stamina overrides. Species Behaviour controls override or stacking.");
        if (shouldShowRow(9)) {
            addDoubleField("Min Final Multiplier", () -> config.stamina.minFinalMultiplier, value -> config.stamina.minFinalMultiplier = value, centerX, rowY(9), 18,
                    "Lowest allowed final stamina multiplier after all enabled stamina factors are combined.");
        }
        if (shouldShowRow(10)) {
            addDoubleField("Max Final Multiplier", () -> config.stamina.maxFinalMultiplier, value -> config.stamina.maxFinalMultiplier = value, centerX, rowY(10), 18,
                    "Highest allowed final stamina multiplier after all enabled stamina factors are combined.");
        }

        addHeader("Speed", 11);
        addToggle("Enabled", config.speed.enabled, value -> config.speed.enabled = value, centerX, rowY(12), 0,
                "Master switch for speed multipliers. Off keeps Cobblemon's normal riding speed.");
        addStackingModeToggle("Multiplier Mode", config.speed, centerX, rowY(13), 0);
        if (shouldShowRow(14)) {
            addDoubleField("Global Multiplier", () -> config.speed.globalMultiplier, value -> config.speed.globalMultiplier = value, centerX, rowY(14), 18,
                    "Always-on speed multiplier. It combines with the other enabled speed factors.");
        }
        addToggle("Scaling", config.speed.levelScalingEnabled, value -> config.speed.levelScalingEnabled = value, centerX, rowY(15), 18,
                "Enables speed scaling options for level, nature, IVs, and EVs.");
        addToggle("Ride Styles & Behaviours", config.speed.ridingMultipliersEnabled, value -> config.speed.ridingMultipliersEnabled = value, centerX, rowY(16), 18,
                "Applies speed multipliers for the active ride style and behaviour, such as air/jet or land/horse.");
        addToggle("Labels", config.speed.labelMultipliersEnabled, value -> config.speed.labelMultipliersEnabled = value, centerX, rowY(17), 18,
                "Applies speed multipliers from Cobblemon form labels. Label Behaviour controls highest or stacking.");
        addToggle("Species", config.speed.speciesOverridesEnabled, value -> config.speed.speciesOverridesEnabled = value, centerX, rowY(18), 18,
                "Applies per-species speed overrides. Species Behaviour controls override or stacking.");
        if (shouldShowRow(19)) {
            addDoubleField("Min Final Multiplier", () -> config.speed.minFinalMultiplier, value -> config.speed.minFinalMultiplier = value, centerX, rowY(19), 18,
                    "Lowest allowed final speed multiplier after all enabled speed factors are combined.");
        }
        if (shouldShowRow(20)) {
            addDoubleField("Max Final Multiplier", () -> config.speed.maxFinalMultiplier, value -> config.speed.maxFinalMultiplier = value, centerX, rowY(20), 18,
                    "Highest allowed final speed multiplier after all enabled speed factors are combined.");
        }

        addHeader("Configuration", 21);
        addToggle("Debug Logging", config.debugLogging, value -> config.debugLogging = value, centerX, rowY(22), 0,
                "Writes extra config and sync details to the log.");
        addPresetButtons(rowY(23));
        if (shouldShowRow(24)) {
            addLabel("Config Version", config.configVersion, centerX, rowY(24));
        }
    }

    private void addLevelControls(RidingTweaksConfig.FeatureTweaks feature) {
        RidingTweaksConfig.LevelScaling scaling = feature.levelScaling;
        int centerX = this.width / 2;
        addToggle("Enabled", feature.levelScalingEnabled, value -> feature.levelScalingEnabled = value, centerX, rowY(0), 0);
        addHeader("Level Scaling", 1);
        if (shouldShowRow(2)) {
            addDoubleField("Level 1 Multiplier", () -> scaling.level1Multiplier, value -> scaling.level1Multiplier = value, centerX, rowY(2));
        }
        if (shouldShowRow(3)) {
            addDoubleField("Level 100 Multiplier", () -> scaling.level100Multiplier, value -> scaling.level100Multiplier = value, centerX, rowY(3));
        }
        if (feature instanceof RidingTweaksConfig.StaminaTweaks staminaTweaks) {
            addStatScalingControls(
                    staminaTweaks.statScaling,
                    staminaTweaks.statScaling.stat,
                    true,
                    true,
                    centerX
            );
        } else if (feature instanceof RidingTweaksConfig.SpeedTweaks speedTweaks) {
            addStatScalingControls(
                    speedTweaks.statScaling,
                    RidingTweaksConfig.STAT_SPEED,
                    true,
                    false,
                    centerX
            );
        }
    }

    private void addStatScalingControls(
            RidingTweaksConfig.IvEvStatScaling scaling,
            String statKey,
            boolean includeNatureScaling,
            boolean includeStatSelector,
            int centerX
    ) {
        addHeader("Stat Scaling", 4);
        int row = 5;
        String sanitizedStatKey = RidingTweaksConfig.sanitizeStatKey(statKey);
        String statName = statLabelText(sanitizedStatKey);
        if (includeStatSelector && scaling instanceof RidingTweaksConfig.StaminaStatScaling staminaScaling) {
            addStatKeyToggle("Used Stat", staminaScaling, centerX, rowY(row++), 0);
            sanitizedStatKey = RidingTweaksConfig.sanitizeStatKey(staminaScaling.stat);
            statName = statLabelText(sanitizedStatKey);
        }

        RidingTweaksConfig.NatureIvEvStatScaling natureScaling =
                includeNatureScaling && scaling instanceof RidingTweaksConfig.NatureIvEvStatScaling typedScaling ? typedScaling : null;
        if (natureScaling != null) {
            boolean statHasNature = statHasNature(sanitizedStatKey);
            addNatureScalingToggle("Nature Scaling", natureScaling, statName, statHasNature, centerX, rowY(row++), 0);
            if (statHasNature && natureScaling.natureScalingEnabled) {
                addToggle("Allow Mints", natureScaling.allowMints, value -> natureScaling.allowMints = value, centerX, rowY(row++), 18,
                        "When on, minted natures count. When off, only the Pokemon's original nature counts.");
            }
        }
        addIvEvModeToggle(statName + " IV & EV Scaling", scaling, statName, centerX, rowY(row++), 0);
        if (RidingTweaksConfig.IV_EV_MODE_COMBINED.equals(scaling.ivEvScalingMode)) {
            addToggle("Allow Hyper Training", scaling.allowHyperTraining, value -> scaling.allowHyperTraining = value, centerX, rowY(row++), 18,
                    "When on, hyper-trained " + statName + " IV counts. When off, only the natural " + statName + " IV counts.");
            if (shouldShowRow(row)) {
                addDoubleField(
                        "0 IV + EV Multiplier",
                        () -> scaling.combinedZeroMultiplier,
                        value -> scaling.combinedZeroMultiplier = value,
                        centerX,
                        rowY(row),
                        18
                );
            }
            row++;
            if (shouldShowRow(row)) {
                addDoubleField(
                        "283 (Max) IV + EV Multiplier",
                        () -> scaling.combinedMaxMultiplier,
                        value -> scaling.combinedMaxMultiplier = value,
                        centerX,
                        rowY(row),
                        18
                );
            }
        } else if (RidingTweaksConfig.IV_EV_MODE_SEPARATE.equals(scaling.ivEvScalingMode)) {
            addToggle("Allow Hyper Training", scaling.allowHyperTraining, value -> scaling.allowHyperTraining = value, centerX, rowY(row++), 18,
                    "When on, hyper-trained " + statName + " IV counts. When off, only the natural " + statName + " IV counts.");
            if (shouldShowRow(row)) {
                addDoubleField("0 IV Multiplier", () -> scaling.ivZeroMultiplier, value -> scaling.ivZeroMultiplier = value, centerX, rowY(row), 18);
            }
            row++;
            if (shouldShowRow(row)) {
                addDoubleField("31 (Max) IV Multiplier", () -> scaling.ivMaxMultiplier, value -> scaling.ivMaxMultiplier = value, centerX, rowY(row), 18);
            }
            row++;
            if (shouldShowRow(row)) {
                addDoubleField("0 EV Multiplier", () -> scaling.evZeroMultiplier, value -> scaling.evZeroMultiplier = value, centerX, rowY(row), 18);
            }
            row++;
            if (shouldShowRow(row)) {
                addDoubleField("252 (Max) EV Multiplier", () -> scaling.evMaxMultiplier, value -> scaling.evMaxMultiplier = value, centerX, rowY(row), 18);
            }
        }
    }

    private void addRideStyleAndBehaviourControls(RidingTweaksConfig.FeatureTweaks feature) {
        int centerX = this.width / 2;
        addToggle("Enabled", feature.ridingMultipliersEnabled, value -> feature.ridingMultipliersEnabled = value, centerX, rowY(0), 0);
        addHeader("Ride Styles", 1);
        addMapEntries(feature.rideStyleMultipliers, false, 2, RIDE_STYLE_KEYS);

        int behaviourHeaderRow = 2 + RIDE_STYLE_KEYS.size();
        addHeader("Behaviours", behaviourHeaderRow);
        int row = behaviourHeaderRow + 1;
        row = addBehaviourGroup(feature, "Land", LAND_BEHAVIOUR_KEYS, row);
        row = addBehaviourGroup(feature, "Air", AIR_BEHAVIOUR_KEYS, row);
        addBehaviourGroup(feature, "Liquid", LIQUID_BEHAVIOUR_KEYS, row);
    }

    private int addBehaviourGroup(RidingTweaksConfig.FeatureTweaks feature, String title, List<String> keys, int startRow) {
        if (keys.isEmpty()) {
            return startRow;
        }
        addSubHeader(title, startRow);
        addMapEntries(feature.behaviourMultipliers, false, startRow + 1, keys, 24);
        return startRow + 1 + keys.size();
    }

    private void addLabelControls(RidingTweaksConfig.FeatureTweaks feature) {
        int centerX = this.width / 2;
        addToggle("Enabled", feature.labelMultipliersEnabled, value -> feature.labelMultipliersEnabled = value, centerX, rowY(0), 0);
        addLabelModeToggle("Label Behaviour", feature, centerX, rowY(1), 0);
        if (shouldShowRow(2)) {
            addDoubleField("Default Multiplier", () -> feature.defaultLabelMultiplier, value -> feature.defaultLabelMultiplier = value, centerX, rowY(2));
        }
        addHeader("Labels", 3);
        addMapEntries(feature.labelMultipliers, true, 4, new ArrayList<>(feature.labelMultipliers.keySet()));
        addMapButtons(feature.labelMultipliers, RidingTweaksConfig.knownCobblemonLabels(), true);
    }

    private void addSpeciesControls(RidingTweaksConfig.FeatureTweaks feature) {
        int centerX = this.width / 2;
        addToggle("Enabled", feature.speciesOverridesEnabled, value -> feature.speciesOverridesEnabled = value, centerX, rowY(0), 0);
        addSpeciesModeToggle("Species Behaviour", feature, centerX, rowY(1), 0);
        addHeader("Overrides", 2);
        addMapEntries(feature.speciesOverrides, true, 3, new ArrayList<>(feature.speciesOverrides.keySet()));
        addMapButtons(feature.speciesOverrides, List.of(), true);
    }

    private void addMapEntries(Map<String, Double> multipliers, boolean editableKeys, int startRow, List<String> keys) {
        addMapEntries(multipliers, editableKeys, startRow, keys, 0);
    }

    private void addMapEntries(Map<String, Double> multipliers, boolean editableKeys, int startRow, List<String> keys, int indent) {
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
                addMapValueRow(multipliers, key, value, centerX, rowY(row), indent);
            }
        }
    }

    private void addMapButtons(Map<String, Double> multipliers, List<String> knownKeys, boolean editableKeys) {
        int buttonY = rowsTop() + visibleRows() * ROW_HEIGHT + 4;
        if (selectedSection.labelSection && !knownKeys.isEmpty()) {
            Button addKnownButton = Button.builder(rowButtonText("Add Known", buttonY), button -> {
                knownPickerOpen = true;
                sectionPickerOpen = false;
                knownPickerScroll = 0;
                rebuild();
            }).bounds(contentLeft(), buttonY, addButtonWidth(editableKeys), 20).build();
            addKnownButton.active = selectedTabIsEditable();
            addRenderableWidget(addKnownButton);
        }

        if (editableKeys) {
            Button addCustomButton = Button.builder(rowButtonText(customButtonText(), buttonY), button -> {
                addCustomKey(multipliers);
                rebuild();
            }).bounds(addCustomButtonX(), buttonY, addButtonWidth(selectedSection.labelSection), 20).build();
            addCustomButton.active = selectedTabIsEditable();
            addRenderableWidget(addCustomButton);
        }
    }

    private void addHeader(String label, int row) {
        if (shouldShowRow(row)) {
            int y = rowY(row) + 6;
            labelLines.add(new LabelLine(fitText(label, contentWidth()), contentLeft(), y, rowTextColor(y, HEADER_TEXT_COLOR)));
        }
    }

    private void addSubHeader(String label, int row) {
        if (shouldShowRow(row)) {
            int y = rowY(row) + 6;
            labelLines.add(new LabelLine(fitText(label, labelWidth() - 12), labelX() + 12, y, rowTextColor(y, SUBHEADER_TEXT_COLOR)));
        }
    }

    private void addToggle(String label, boolean currentValue, Consumer<Boolean> setter, int centerX, int y) {
        addToggle(label, currentValue, setter, centerX, y, 0);
    }

    private void addToggle(String label, boolean currentValue, Consumer<Boolean> setter, int centerX, int y, int indent) {
        addToggle(label, currentValue, setter, centerX, y, indent, null);
    }

    private void addToggle(
            String label,
            boolean currentValue,
            Consumer<Boolean> setter,
            int centerX,
            int y,
            int indent,
            String tooltip
    ) {
        if (y < rowsTop() || y >= rowViewportBottom()) {
            return;
        }
        Button button = Button.builder(rowButtonText(onOff(currentValue), y), pressed -> {
            setter.accept(!currentValue);
            rebuild();
        }).bounds(valueX(), y, valueWidth(), 20).build();
        button.active = selectedTabIsEditable();
        setTooltip(button, tooltip);
        addRenderableWidget(button);
        addTooltipArea(labelX() + indent, y, labelWidth() - indent, 20, tooltip);
        addRowLabel(label, labelX() + indent, y + 6, labelWidth() - indent);
    }

    private void addPresetButtons(int y) {
        if (y < rowsTop() || y >= rowViewportBottom()) {
            return;
        }

        // The pair needs more room than a normal value field, especially at high GUI scales.
        int minButtonWidth = Math.max(this.font.width(Preset.DEFAULT.displayName), this.font.width(Preset.BALANCED.displayName)) + 16;
        int pairWidth = Math.min(contentWidth(), Math.max(valueWidth(), minButtonWidth * 2 + FIELD_GAP));
        int firstWidth = (pairWidth - FIELD_GAP) / 2;
        int x = contentRight() - pairWidth;
        addPresetButton(Preset.DEFAULT, x, y, firstWidth);
        addPresetButton(Preset.BALANCED, x + firstWidth + FIELD_GAP, y, pairWidth - firstWidth - FIELD_GAP);

        int availableLabelWidth = x - labelX() - FIELD_GAP;
        if (availableLabelWidth >= this.font.width("Presets")) {
            addRowLabel("Presets", labelX(), y + 6, availableLabelWidth);
        }
    }

    private void addPresetButton(Preset preset, int x, int y, int width) {
        Button button = Button.builder(Component.literal(preset.displayName), pressed -> showPresetConfirmation(preset))
                .bounds(x, y, width, 20)
                .build();
        button.active = selectedTabIsEditable();
        setTooltip(button, preset.description + " Replaces current riding settings and custom overrides. Nothing is written until you click Save.");
        addRenderableWidget(button);
    }

    private void addStackingModeToggle(String label, RidingTweaksConfig.FeatureTweaks feature, int centerX, int y, int indent) {
        if (y < rowsTop() || y >= rowViewportBottom()) {
            return;
        }
        Button button = Button.builder(rowButtonText(stackingModeText(feature.stackingMode), y), pressed -> {
            feature.stackingMode = nextStackingMode(feature.stackingMode);
            rebuild();
        }).bounds(valueX(), y, valueWidth(), 20).build();
        button.active = selectedTabIsEditable();
        List<Component> tooltip = stackingModeTooltip();
        setTooltip(button, tooltip);
        addRenderableWidget(button);
        addTooltipArea(labelX() + indent, y, labelWidth() - indent, 20, tooltip);
        addRowLabel(label, labelX() + indent, y + 6, labelWidth() - indent);
    }

    private void addLabelModeToggle(String label, RidingTweaksConfig.FeatureTweaks feature, int centerX, int y, int indent) {
        if (y < rowsTop() || y >= rowViewportBottom()) {
            return;
        }
        Button button = Button.builder(rowButtonText(labelModeText(feature.labelMode), y), pressed -> {
            feature.labelMode = nextLabelMode(feature.labelMode);
            rebuild();
        }).bounds(valueX(), y, valueWidth(), 20).build();
        button.active = selectedTabIsEditable();
        List<Component> tooltip = labelModeTooltip();
        setTooltip(button, tooltip);
        addRenderableWidget(button);
        addTooltipArea(labelX() + indent, y, labelWidth() - indent, 20, tooltip);
        addRowLabel(label, labelX() + indent, y + 6, labelWidth() - indent);
    }

    private void addSpeciesModeToggle(String label, RidingTweaksConfig.FeatureTweaks feature, int centerX, int y, int indent) {
        if (y < rowsTop() || y >= rowViewportBottom()) {
            return;
        }
        Button button = Button.builder(rowButtonText(speciesModeText(feature.speciesMode), y), pressed -> {
            feature.speciesMode = nextSpeciesMode(feature.speciesMode);
            rebuild();
        }).bounds(valueX(), y, valueWidth(), 20).build();
        button.active = selectedTabIsEditable();
        List<Component> tooltip = speciesModeTooltip();
        setTooltip(button, tooltip);
        addRenderableWidget(button);
        addTooltipArea(labelX() + indent, y, labelWidth() - indent, 20, tooltip);
        addRowLabel(label, labelX() + indent, y + 6, labelWidth() - indent);
    }

    private void addStatKeyToggle(
            String label,
            RidingTweaksConfig.StaminaStatScaling scaling,
            int centerX,
            int y,
            int indent
    ) {
        if (y < rowsTop() || y >= rowViewportBottom()) {
            return;
        }
        Button button = Button.builder(rowButtonText(statLabelText(scaling.stat), y), pressed -> {
            scaling.stat = nextStatKey(scaling.stat);
            rebuild();
        }).bounds(valueX(), y, valueWidth(), 20).build();
        button.active = selectedTabIsEditable();
        List<Component> tooltip = statKeyTooltip();
        setTooltip(button, tooltip);
        addRenderableWidget(button);
        addTooltipArea(labelX() + indent, y, labelWidth() - indent, 20, tooltip);
        addRowLabel(label, labelX() + indent, y + 6, labelWidth() - indent);
    }

    private void addNatureScalingToggle(
            String label,
            RidingTweaksConfig.NatureIvEvStatScaling scaling,
            String statName,
            boolean statHasNature,
            int centerX,
            int y,
            int indent
    ) {
        if (y < rowsTop() || y >= rowViewportBottom()) {
            return;
        }
        Button button = Button.builder(rowButtonText(statHasNature ? onOff(scaling.natureScalingEnabled) : "N/A", y), pressed -> {
            scaling.natureScalingEnabled = !scaling.natureScalingEnabled;
            rebuild();
        }).bounds(valueX(), y, valueWidth(), 20).build();
        button.active = selectedTabIsEditable() && statHasNature;
        String tooltip = statHasNature
                ? "Applies Pokemon nature's " + statName + " modifier as a riding factor: +10% for +" + statName + ", -10% for -" + statName + "."
                : "HP is not affected by any Pokemon nature.";
        setTooltip(button, tooltip);
        addRenderableWidget(button);
        addTooltipArea(labelX() + indent, y, labelWidth() - indent, 20, tooltip);
        addRowLabel(label, labelX() + indent, y + 6, labelWidth() - indent);
    }

    private void addIvEvModeToggle(
            String label,
            RidingTweaksConfig.IvEvStatScaling scaling,
            String statName,
            int centerX,
            int y,
            int indent
    ) {
        if (y < rowsTop() || y >= rowViewportBottom()) {
            return;
        }
        Button button = Button.builder(rowButtonText(ivEvModeText(scaling.ivEvScalingMode), y), pressed -> {
            scaling.ivEvScalingMode = nextIvEvMode(scaling.ivEvScalingMode);
            rebuild();
        }).bounds(valueX(), y, valueWidth(), 20).build();
        button.active = selectedTabIsEditable();
        List<Component> tooltip = ivEvModeTooltip(statName);
        setTooltip(button, tooltip);
        addRenderableWidget(button);
        addTooltipArea(labelX() + indent, y, labelWidth() - indent, 20, tooltip);
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
        addDoubleField(label, getter, setter, centerX, y, indent, null);
    }

    private void addDoubleField(
            String label,
            Supplier<Double> getter,
            Consumer<Double> setter,
            int centerX,
            int y,
            int indent,
            String tooltip
    ) {
        EditBox box = textBox(valueX(), y, valueWidth(), formatDouble(getter.get()));
        box.setResponder(value -> parseMultiplier(label, value, setter));
        setTooltip(box, tooltip);
        addRenderableWidget(box);
        addTooltipArea(labelX() + indent, y, labelWidth() - indent, 20, tooltip);
        addRowLabel(label, labelX() + indent, y + 6, labelWidth() - indent);
    }

    private void addTooltipArea(int x, int y, int width, int height, String tooltip) {
        if (tooltip != null && !tooltip.isBlank() && width > 0 && height > 0) {
            tooltipAreas.add(new TooltipArea(x, y, width, height, tooltip.lines().map(line -> (Component) Component.literal(line)).toList()));
        }
    }

    private void addTooltipArea(int x, int y, int width, int height, List<Component> tooltip) {
        if (tooltip != null && !tooltip.isEmpty() && width > 0 && height > 0) {
            tooltipAreas.add(new TooltipArea(x, y, width, height, tooltip));
        }
    }

    private void setTooltip(Button button, String tooltip) {
        if (tooltip != null && !tooltip.isBlank()) {
            button.setTooltip(Tooltip.create(Component.literal(tooltip)));
        }
    }

    private void setTooltip(Button button, List<Component> tooltip) {
        if (tooltip != null && !tooltip.isEmpty()) {
            button.setTooltip(Tooltip.create(componentLines(tooltip)));
        }
    }

    private void setTooltip(EditBox box, String tooltip) {
        if (tooltip != null && !tooltip.isBlank()) {
            box.setTooltip(Tooltip.create(Component.literal(tooltip)));
        }
    }

    private static Component componentLines(List<Component> lines) {
        Component result = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                result = result.copy().append(Component.literal("\n"));
            }
            result = result.copy().append(lines.get(i));
        }
        return result;
    }

    private void addMapValueRow(Map<String, Double> multipliers, String key, double value, int centerX, int y) {
        addMapValueRow(multipliers, key, value, centerX, y, 0);
    }

    private void addMapValueRow(Map<String, Double> multipliers, String key, double value, int centerX, int y, int indent) {
        EditBox valueBox = textBox(valueX(), y, valueWidth(), formatDouble(value));
        valueBox.setResponder(text -> parseMultiplier(key, text, parsed -> multipliers.put(key, parsed)));
        addRenderableWidget(valueBox);
        addRowLabel(displayKey(key), labelX() + indent, y + 6, labelWidth() - indent);
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

        Button removeButton = Button.builder(rowButtonText("X", y), button -> {
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
        if (isPageContentDimmedAt(y)) {
            box.setTextColor(DIMMED_TEXT_COLOR);
        }
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
            case GENERAL -> 25;
            case STAMINA_LEVEL -> statLevelRowCount(viewingConfig().stamina.statScaling, true);
            case SPEED_LEVEL -> statLevelRowCount(viewingConfig().speed.statScaling, false);
            case STAMINA_RIDE_STYLES, SPEED_RIDE_STYLES -> rideStyleAndBehaviourRowCount();
            case STAMINA_LABELS, SPEED_LABELS -> 3 + currentMap().size();
            case STAMINA_SPECIES, SPEED_SPECIES -> 3 + currentMap().size();
        };
    }

    private int statLevelRowCount(RidingTweaksConfig.IvEvStatScaling scaling, boolean includeStatSelector) {
        int rowCount = 5;
        String statKey = RidingTweaksConfig.STAT_SPEED;
        if (includeStatSelector && scaling instanceof RidingTweaksConfig.StaminaStatScaling staminaScaling) {
            rowCount++;
            statKey = staminaScaling.stat;
        }
        if (scaling instanceof RidingTweaksConfig.NatureIvEvStatScaling natureScaling) {
            rowCount++;
            if (statHasNature(statKey) && natureScaling.natureScalingEnabled) {
                rowCount++;
            }
        }
        rowCount++;
        if (scaling == null || RidingTweaksConfig.IV_EV_MODE_OFF.equals(normalizeKeyOrBlank(scaling.ivEvScalingMode))) {
            return rowCount;
        }
        if (RidingTweaksConfig.IV_EV_MODE_COMBINED.equals(normalizeKeyOrBlank(scaling.ivEvScalingMode))) {
            return rowCount + 3;
        }
        return rowCount + 5;
    }

    private int rideStyleAndBehaviourRowCount() {
        return 1
                + 1
                + RIDE_STYLE_KEYS.size()
                + 1
                + 1 + LAND_BEHAVIOUR_KEYS.size()
                + 1 + AIR_BEHAVIOUR_KEYS.size()
                + 1 + LIQUID_BEHAVIOUR_KEYS.size();
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

    private int unsavedChangesY(boolean statusVisible) {
        return statusVisible ? Math.max(rowsTop() + 4, statusY() - 14) : statusY();
    }

    private int feedbackY(boolean unsavedChangesVisible, boolean statusVisible) {
        int anchorY = unsavedChangesVisible ? unsavedChangesY(statusVisible) : statusY();
        return Math.max(rowsTop() + 4, anchorY - 16);
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
        return Math.max(70, Math.min(140, contentWidth() / 5));
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
        return Math.max(64, Math.min(110, contentWidth() / 6));
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
        sectionPickerOpen = false;
        sectionPickerScroll = 0;
        rebuild();
    }

    private void addSectionPickerControls() {
        Section[] sections = Section.values();
        int pickerWidth = sectionPickerWidth();
        int left = (this.width - pickerWidth) / 2;
        int top = sectionPickerTop();
        int rows = Math.min(sectionPickerRows(), sections.length);
        sectionPickerScroll = Math.clamp(sectionPickerScroll, 0, Math.max(0, sections.length - rows));

        for (int row = 0; row < rows; row++) {
            Section section = sections[sectionPickerScroll + row];
            addRenderableWidget(Button.builder(sectionButtonText(section), button -> {
                selectedSection = section;
                scrollRow = 0;
                knownPickerOpen = false;
                sectionPickerOpen = false;
                sectionPickerScroll = 0;
                rebuild();
            }).bounds(left, top + row * ROW_HEIGHT, pickerWidth, 20).build());
        }
    }

    private void handleSectionPickerClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return;
        }

        Section[] sections = Section.values();
        int pickerWidth = sectionPickerWidth();
        int left = (this.width - pickerWidth) / 2;
        int top = sectionPickerTop();
        int rows = Math.min(sectionPickerRows(), sections.length);

        for (int row = 0; row < rows; row++) {
            int rowY = top + row * ROW_HEIGHT;
            if (isWithin(mouseX, mouseY, left, rowY, pickerWidth, 20)) {
                selectedSection = sections[sectionPickerScroll + row];
                scrollRow = 0;
                knownPickerOpen = false;
                sectionPickerOpen = false;
                sectionPickerScroll = 0;
                rebuild();
                return;
            }
        }

        sectionPickerOpen = false;
        rebuild();
    }

    private boolean handleTabClick(double mouseX, double mouseY, int button) {
        if (button != 0 || !showServerTabs()) {
            return false;
        }

        int centerX = this.width / 2;
        int tabGap = 8;
        int tabWidth = Math.max(72, Math.min(120, (contentWidth() - tabGap) / 2));
        int top = tabsY();
        if (isWithin(mouseX, mouseY, centerX - tabWidth - tabGap / 2, top, tabWidth, 20)) {
            selectTab(Tab.LOCAL);
            return true;
        }
        if (isWithin(mouseX, mouseY, centerX + tabGap / 2, top, tabWidth, 20)) {
            selectTab(Tab.SERVER);
            return true;
        }
        return false;
    }

    private void selectTab(Tab tab) {
        selectedTab = tab;
        knownPickerOpen = false;
        sectionPickerOpen = false;
        sectionPickerScroll = 0;
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
            return;
        }

        knownPickerOpen = false;
        rebuild();
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

    private int sectionPickerRows() {
        int availableRows = (footerButtonsY() - sectionPickerTop() - 12) / ROW_HEIGHT;
        return Math.max(1, Math.min(Section.values().length, availableRows));
    }

    private int maxSectionPickerScroll() {
        return Math.max(0, Section.values().length - sectionPickerRows());
    }

    private int sectionPickerTop() {
        return sectionY() + 24;
    }

    private int sectionPickerWidth() {
        return Math.max(140, Math.min(320, contentWidth() - 72));
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

    private void drawSectionPickerBacking(GuiGraphics graphics) {
        int pickerWidth = sectionPickerWidth();
        int left = (this.width - pickerWidth) / 2 - 8;
        int top = sectionPickerTop() - 8;
        int right = left + pickerWidth + 16;
        int bottom = top + 20 + sectionPickerRows() * ROW_HEIGHT;
        graphics.fill(left, top, right, bottom, 0xD0000000);
        graphics.fill(left + 2, top + 2, right - 2, bottom - 2, 0xE0202020);
    }

    private void drawKnownPickerScrollBar(GuiGraphics graphics) {
        List<String> missing = missingKnownKeys(currentMap(), RidingTweaksConfig.knownCobblemonLabels());
        int totalRows = missing.size();
        int visibleRows = Math.min(knownPickerRows(), totalRows);
        if (totalRows <= visibleRows) {
            return;
        }

        int pickerWidth = knownPickerWidth();
        int left = (this.width - pickerWidth) / 2;
        int trackX = left + pickerWidth + 4;
        int trackTop = knownPickerTop() + 24;
        int trackHeight = Math.max(1, visibleRows * ROW_HEIGHT - 4);
        drawVerticalScrollBar(graphics, trackX, trackTop, trackHeight, totalRows, visibleRows, knownPickerScroll);
    }

    private void drawSectionPickerScrollBar(GuiGraphics graphics) {
        int totalRows = Section.values().length;
        int visibleRows = Math.min(sectionPickerRows(), totalRows);
        if (totalRows <= visibleRows) {
            return;
        }

        int pickerWidth = sectionPickerWidth();
        int left = (this.width - pickerWidth) / 2;
        int trackX = left + pickerWidth + 4;
        int trackTop = sectionPickerTop();
        int trackHeight = Math.max(1, visibleRows * ROW_HEIGHT - 4);
        drawVerticalScrollBar(graphics, trackX, trackTop, trackHeight, totalRows, visibleRows, sectionPickerScroll);
    }

    private void drawMapScrollBar(GuiGraphics graphics) {
        if (knownPickerOpen || sectionPickerOpen) {
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
        drawVerticalScrollBar(graphics, trackX, trackTop, trackHeight, totalRows, visibleRows, scrollRow);
    }

    private void drawVerticalScrollBar(
            GuiGraphics graphics,
            int trackX,
            int trackTop,
            int trackHeight,
            int totalRows,
            int visibleRows,
            int scroll
    ) {
        if (totalRows <= visibleRows) {
            return;
        }

        int handleHeight = Math.max(18, trackHeight * visibleRows / totalRows);
        int maxScroll = Math.max(1, totalRows - visibleRows);
        int handleY = trackTop + (trackHeight - handleHeight) * Math.clamp(scroll, 0, maxScroll) / maxScroll;
        graphics.fill(trackX, trackTop, trackX + 4, trackTop + trackHeight, 0x90000000);
        graphics.fill(trackX, handleY, trackX + 4, handleY + handleHeight, 0xFFD0D0D0);
    }

    private void drawLabelTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        for (TooltipArea area : tooltipAreas) {
            if (isWithin(mouseX, mouseY, area.x, area.y, area.width, area.height)) {
                graphics.renderComponentTooltip(this.font, area.lines, mouseX, mouseY);
                return;
            }
        }
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    private Component tabText(Tab tab) {
        if (selectedTab == tab) {
            return Component.literal(">> " + tab.displayName + " <<").withStyle(ChatFormatting.YELLOW);
        }
        return Component.literal(tab.displayName).withStyle(ChatFormatting.GRAY);
    }

    private Component sectionText() {
        return Component.literal((sectionPickerOpen ? "v " : "") + selectedSection.title);
    }

    private Component sectionButtonText(Section section) {
        boolean disabled = isSectionFeatureDisabled(section);
        String prefix = selectedSection == section ? ">>  " : "";
        String suffix = disabled ? " (Off)" : "";
        String text = prefix + section.title + suffix;
        return disabled ? Component.literal(text).withStyle(ChatFormatting.GRAY) : Component.literal(text);
    }

    private boolean isSectionFeatureDisabled(Section section) {
        RidingTweaksConfig config = viewingConfig();
        return switch (section) {
            case STAMINA_LEVEL -> !config.stamina.levelScalingEnabled;
            case STAMINA_RIDE_STYLES -> !config.stamina.ridingMultipliersEnabled;
            case STAMINA_LABELS -> !config.stamina.labelMultipliersEnabled;
            case STAMINA_SPECIES -> !config.stamina.speciesOverridesEnabled;
            case SPEED_LEVEL -> !config.speed.levelScalingEnabled;
            case SPEED_RIDE_STYLES -> !config.speed.ridingMultipliersEnabled;
            case SPEED_LABELS -> !config.speed.labelMultipliersEnabled;
            case SPEED_SPECIES -> !config.speed.speciesOverridesEnabled;
            case GENERAL -> false;
        };
    }

    private String configSummary() {
        RidingTweaksConfig config = viewingConfig();
        MultiplierRange staminaRange = summaryRange(config, config.stamina);
        MultiplierRange speedRange = summaryRange(config, config.speed);
        return "Version " + config.configVersion
                + " | stamina x" + formatMultiplierRange(staminaRange)
                + " | speed x" + formatMultiplierRange(speedRange);
    }

    private static MultiplierRange summaryRange(RidingTweaksConfig config, RidingTweaksConfig.FeatureTweaks feature) {
        if (config == null || !config.enabled || feature == null || !feature.enabled) {
            return new MultiplierRange(1.0D, 1.0D);
        }

        List<MultiplierRange> baseRanges = new ArrayList<>();
        baseRanges.add(fixedRange(feature.globalMultiplier));
        if (feature.levelScalingEnabled) {
            baseRanges.add(range(feature.levelScaling.level1Multiplier, feature.levelScaling.level100Multiplier));
            addStatSummaryRanges(baseRanges, feature);
        }
        if (feature.ridingMultipliersEnabled) {
            baseRanges.add(mapRange(feature.rideStyleMultipliers));
            baseRanges.add(mapRange(feature.behaviourMultipliers));
        }

        MultiplierRange labelRange = labelRange(feature);
        if (feature.speciesOverridesEnabled
                && RidingTweaksConfig.SPECIES_MODE_OVERRIDE.equals(normalizeKeyOrBlank(feature.speciesMode))) {
            MultiplierRange withoutSpecies = combineAndClamp(feature, baseRanges, labelRange);
            if (feature.speciesOverrides == null || feature.speciesOverrides.isEmpty()) {
                return withoutSpecies;
            }
            MultiplierRange withSpecies = combineAndClamp(feature, baseRanges, mapRange(feature.speciesOverrides));
            return new MultiplierRange(
                    Math.min(withoutSpecies.min(), withSpecies.min()),
                    Math.max(withoutSpecies.max(), withSpecies.max())
            );
        }

        List<MultiplierRange> ranges = new ArrayList<>(baseRanges);
        if (feature.speciesOverridesEnabled && feature.speciesOverrides != null && !feature.speciesOverrides.isEmpty()) {
            ranges.add(mapRangeIncludingOne(feature.speciesOverrides));
        }
        if (labelRange != null) {
            ranges.add(labelRange);
        }
        return combineAndClamp(feature, ranges);
    }

    private static void addStatSummaryRanges(List<MultiplierRange> ranges, RidingTweaksConfig.FeatureTweaks feature) {
        if (feature instanceof RidingTweaksConfig.StaminaTweaks staminaTweaks) {
            RidingTweaksConfig.StaminaStatScaling scaling = staminaTweaks.statScaling;
            if (scaling != null) {
                addNatureAndIvEvRanges(ranges, scaling, scaling.stat);
            }
        } else if (feature instanceof RidingTweaksConfig.SpeedTweaks speedTweaks) {
            addNatureAndIvEvRanges(ranges, speedTweaks.statScaling, RidingTweaksConfig.STAT_SPEED);
        }
    }

    private static void addNatureAndIvEvRanges(
            List<MultiplierRange> ranges,
            RidingTweaksConfig.NatureIvEvStatScaling scaling,
            String statKey
    ) {
        if (scaling == null) {
            return;
        }
        if (statHasNature(statKey) && scaling.natureScalingEnabled) {
            ranges.add(new MultiplierRange(0.9D, 1.1D));
        }
        if (RidingTweaksConfig.IV_EV_MODE_COMBINED.equals(normalizeKeyOrBlank(scaling.ivEvScalingMode))) {
            ranges.add(range(scaling.combinedZeroMultiplier, scaling.combinedMaxMultiplier));
        } else if (RidingTweaksConfig.IV_EV_MODE_SEPARATE.equals(normalizeKeyOrBlank(scaling.ivEvScalingMode))) {
            ranges.add(range(scaling.ivZeroMultiplier, scaling.ivMaxMultiplier));
            ranges.add(range(scaling.evZeroMultiplier, scaling.evMaxMultiplier));
        }
    }

    private static MultiplierRange labelRange(RidingTweaksConfig.FeatureTweaks feature) {
        if (!feature.labelMultipliersEnabled) {
            return null;
        }

        double defaultMultiplier = safeMultiplier(feature.defaultLabelMultiplier);
        List<Double> values = feature.labelMultipliers == null
                ? List.of()
                : feature.labelMultipliers.values().stream().map(RidingTweaksConfigScreen::safeMultiplier).toList();
        if (values.isEmpty()) {
            return fixedRange(defaultMultiplier);
        }

        if (RidingTweaksConfig.LABEL_MODE_HIGHEST.equals(normalizeKeyOrBlank(feature.labelMode))) {
            double min = defaultMultiplier;
            double max = defaultMultiplier;
            for (double value : values) {
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
            return new MultiplierRange(min, max);
        }

        if (RidingTweaksConfig.STACKING_MODE_MULTIPLICATIVE.equals(normalizeKeyOrBlank(feature.stackingMode))) {
            double min = 1.0D;
            double max = 1.0D;
            for (double value : values) {
                if (value < 1.0D) {
                    min *= value;
                } else if (value > 1.0D) {
                    max *= value;
                }
            }
            return new MultiplierRange(Math.min(defaultMultiplier, min), Math.max(defaultMultiplier, max));
        }

        double min = 1.0D;
        double max = 1.0D;
        for (double value : values) {
            if (value < 1.0D) {
                min += value - 1.0D;
            } else if (value > 1.0D) {
                max += value - 1.0D;
            }
        }
        return new MultiplierRange(Math.min(defaultMultiplier, min), Math.max(defaultMultiplier, max));
    }

    private static MultiplierRange combineAndClamp(
            RidingTweaksConfig.FeatureTweaks feature,
            List<MultiplierRange> ranges,
            MultiplierRange extraRange
    ) {
        List<MultiplierRange> combinedRanges = new ArrayList<>(ranges);
        if (extraRange != null) {
            combinedRanges.add(extraRange);
        }
        return combineAndClamp(feature, combinedRanges);
    }

    private static MultiplierRange combineAndClamp(
            RidingTweaksConfig.FeatureTweaks feature,
            List<MultiplierRange> ranges
    ) {
        MultiplierRange rawRange = combineRanges(feature, ranges);
        double minClamp = Math.max(0.01D, safeMultiplier(feature.minFinalMultiplier));
        double maxClamp = Math.max(minClamp, safeMultiplier(feature.maxFinalMultiplier));
        return new MultiplierRange(
                Math.clamp(rawRange.min(), minClamp, maxClamp),
                Math.clamp(rawRange.max(), minClamp, maxClamp)
        );
    }

    private static MultiplierRange combineRanges(
            RidingTweaksConfig.FeatureTweaks feature,
            List<MultiplierRange> ranges
    ) {
        if (RidingTweaksConfig.STACKING_MODE_MULTIPLICATIVE.equals(normalizeKeyOrBlank(feature.stackingMode))) {
            double min = 1.0D;
            double max = 1.0D;
            for (MultiplierRange range : ranges) {
                min *= range.min();
                max *= range.max();
            }
            return new MultiplierRange(min, max);
        }

        double min = 1.0D;
        double max = 1.0D;
        for (MultiplierRange range : ranges) {
            min += range.min() - 1.0D;
            max += range.max() - 1.0D;
        }
        return new MultiplierRange(min, max);
    }

    private static MultiplierRange fixedRange(double value) {
        double safeValue = safeMultiplier(value);
        return new MultiplierRange(safeValue, safeValue);
    }

    private static MultiplierRange range(double first, double second) {
        double safeFirst = safeMultiplier(first);
        double safeSecond = safeMultiplier(second);
        return new MultiplierRange(Math.min(safeFirst, safeSecond), Math.max(safeFirst, safeSecond));
    }

    private static MultiplierRange mapRange(Map<String, Double> values) {
        if (values == null || values.isEmpty()) {
            return new MultiplierRange(1.0D, 1.0D);
        }
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (double value : values.values()) {
            double safeValue = safeMultiplier(value);
            min = Math.min(min, safeValue);
            max = Math.max(max, safeValue);
        }
        return new MultiplierRange(min, max);
    }

    private static MultiplierRange mapRangeIncludingOne(Map<String, Double> values) {
        MultiplierRange configuredRange = mapRange(values);
        return new MultiplierRange(
                Math.min(1.0D, configuredRange.min()),
                Math.max(1.0D, configuredRange.max())
        );
    }

    private static double safeMultiplier(double value) {
        return Double.isFinite(value) ? Math.max(0.01D, value) : 1.0D;
    }

    private static String formatMultiplierRange(MultiplierRange range) {
        if (Math.abs(range.max() - range.min()) < 0.0001D) {
            return formatDouble(range.min());
        }
        return formatDouble(range.min()) + "-x" + formatDouble(range.max());
    }

    private String statusText() {
        if (selectedTab == Tab.SERVER) {
            if (manager().canEditServerConfig()) {
                return "Server config. Save writes to the server and syncs modded clients.";
            }
            return "Server config. Read-only.";
        }
        if (!isActiveMultiplayerSession()) {
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

    private String unsavedChangesText() {
        if (!hasUnsavedChanges()) {
            return "";
        }
        if (isSingleplayerSession()) {
            return "Unsaved changes. Click Save to store and apply them.";
        }
        if (!isActiveMultiplayerSession()) {
            return "Unsaved changes. Click Save to store them.";
        }
        String scope = selectedTab == Tab.SERVER ? "[Server]" : "[Local]";
        String action = selectedTab == Tab.SERVER && manager().canEditServerConfig() ? "store and apply" : "store";
        return "Unsaved changes to the " + scope + " config. Click Save to " + action + " them.";
    }

    private boolean hasUnsavedChanges() {
        String draftJson = manager().toJson(viewingConfig());
        String savedJson = selectedTab == Tab.SERVER ? manager().activeConfigJson() : manager().localConfigJson();
        return !draftJson.equals(savedJson);
    }

    private boolean showServerTabs() {
        return manager().isServerConfigActive() && isActiveMultiplayerSession();
    }

    private boolean isSingleplayerSession() {
        return this.minecraft != null && this.minecraft.hasSingleplayerServer();
    }

    private boolean isActiveMultiplayerSession() {
        return this.minecraft != null && this.minecraft.level != null && !isSingleplayerSession();
    }

    private RidingTweaksConfig viewingConfig() {
        ensureDrafts();
        return selectedTab == Tab.SERVER ? serverDraft : localDraft;
    }

    private boolean selectedTabIsEditable() {
        return selectedTab == Tab.LOCAL || manager().canEditServerConfig();
    }

    private boolean isPageContentDimmedAt(int y) {
        return isGeneralFeatureContentDimmedAt(y) || isPageContentDimmed() && y >= rowY(1);
    }

    private boolean isPageContentDimmed() {
        RidingTweaksConfig config = viewingConfig();
        return switch (selectedSection) {
            case STAMINA_LEVEL -> !config.stamina.levelScalingEnabled;
            case STAMINA_RIDE_STYLES -> !config.stamina.ridingMultipliersEnabled;
            case STAMINA_LABELS -> !config.stamina.labelMultipliersEnabled;
            case STAMINA_SPECIES -> !config.stamina.speciesOverridesEnabled;
            case SPEED_LEVEL -> !config.speed.levelScalingEnabled;
            case SPEED_RIDE_STYLES -> !config.speed.ridingMultipliersEnabled;
            case SPEED_LABELS -> !config.speed.labelMultipliersEnabled;
            case SPEED_SPECIES -> !config.speed.speciesOverridesEnabled;
            case GENERAL -> false;
        };
    }

    private boolean isGeneralFeatureContentDimmedAt(int y) {
        if (selectedSection != Section.GENERAL) {
            return false;
        }

        RidingTweaksConfig config = viewingConfig();
        int row = rowIndexForY(y);
        return !config.stamina.enabled && row > 2 && row < 11
                || !config.speed.enabled && row > 12 && row < 21;
    }

    private int rowIndexForY(int y) {
        return scrollRow + Math.floorDiv(y - rowsTop(), ROW_HEIGHT);
    }

    private int rowTextColor(int y, int normalColor) {
        return isPageContentDimmedAt(y) ? DIMMED_TEXT_COLOR : normalColor;
    }

    private Component rowButtonText(String text, int y) {
        Component component = Component.literal(text);
        return isPageContentDimmedAt(y) ? component.copy().withStyle(ChatFormatting.GRAY) : component;
    }

    private void saveCurrentConfig() {
        RidingTweaksConfig draft = viewingConfig().sanitize();
        if (selectedTab == Tab.SERVER) {
            showFeedback("Saving server config...", true);
            serverConfigUpdateSender.send(manager().toJson(draft));
            return;
        }
        if (!isActiveMultiplayerSession()) {
            manager().replaceAndSaveActiveLocal(draft);
            showFeedback(isSingleplayerSession() ? "Saved and applied config." : "Saved config.", true);
        } else {
            manager().replaceAndSave(draft);
            showFeedback("Saved local config.", true);
        }
        localDraft = manager().copyLocalConfig();
        rebuild();
    }

    private void showPresetConfirmation(Preset preset) {
        if (!selectedTabIsEditable() || this.minecraft == null) {
            return;
        }

        this.minecraft.setScreen(new ConfirmScreen(
                confirmed -> {
                    if (confirmed && selectedTabIsEditable()) {
                        replaceCurrentDraftWithPreset(preset);
                        showFeedback(presetFeedbackText(preset), true);
                    }
                    this.minecraft.setScreen(this);
                },
                Component.literal("Load " + preset.displayName + " preset?"),
                Component.literal(preset.description + " This replaces current riding settings and custom overrides. Nothing is written until you click Save."),
                Component.literal("Yes"),
                Component.literal("No")
        ));
    }

    private void replaceCurrentDraftWithPreset(Preset preset) {
        RidingTweaksConfig config = preset == Preset.BALANCED
                ? RidingTweaksConfig.balancedPreset()
                : new RidingTweaksConfig().sanitize();
        config.debugLogging = viewingConfig().debugLogging;
        if (selectedTab == Tab.SERVER) {
            serverDraft = config;
        } else {
            localDraft = config;
        }
        scrollRow = 0;
        knownPickerOpen = false;
        knownPickerScroll = 0;
        sectionPickerOpen = false;
        sectionPickerScroll = 0;
    }

    private String presetFeedbackText(Preset preset) {
        String prefix = preset.displayName + " preset loaded. Click Save to ";
        if (selectedTab == Tab.SERVER && manager().canEditServerConfig() || isSingleplayerSession()) {
            return prefix + "store and apply.";
        }
        return prefix + "store.";
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
        labelLines.add(new LabelLine(fitText(text, Math.max(20, maxWidth)), x, y, rowTextColor(y, NORMAL_TEXT_COLOR)));
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

    private static String statLabelText(String statKey) {
        return switch (RidingTweaksConfig.sanitizeStatKey(statKey)) {
            case RidingTweaksConfig.STAT_ATTACK -> "Attack";
            case RidingTweaksConfig.STAT_DEFENCE -> "Defence";
            case RidingTweaksConfig.STAT_SPECIAL_ATTACK -> "Sp. Attack";
            case RidingTweaksConfig.STAT_SPECIAL_DEFENCE -> "Sp. Defence";
            case RidingTweaksConfig.STAT_SPEED -> "Speed";
            default -> "HP";
        };
    }

    private static String nextStatKey(String statKey) {
        List<String> statKeys = RidingTweaksConfig.statKeys();
        int index = statKeys.indexOf(RidingTweaksConfig.sanitizeStatKey(statKey));
        return statKeys.get((index + 1) % statKeys.size());
    }

    private static boolean statHasNature(String statKey) {
        return !RidingTweaksConfig.STAT_HP.equals(RidingTweaksConfig.sanitizeStatKey(statKey));
    }

    private static String stackingModeText(String mode) {
        return RidingTweaksConfig.STACKING_MODE_MULTIPLICATIVE.equals(normalizeKeyOrBlank(mode)) ? "Multiplicative" : "Additive";
    }

    private static String nextStackingMode(String mode) {
        return RidingTweaksConfig.STACKING_MODE_MULTIPLICATIVE.equals(normalizeKeyOrBlank(mode))
                ? RidingTweaksConfig.STACKING_MODE_ADDITIVE
                : RidingTweaksConfig.STACKING_MODE_MULTIPLICATIVE;
    }

    private static String labelModeText(String mode) {
        return RidingTweaksConfig.LABEL_MODE_HIGHEST.equals(normalizeKeyOrBlank(mode)) ? "Highest" : "Stacking";
    }

    private static String nextLabelMode(String mode) {
        return RidingTweaksConfig.LABEL_MODE_HIGHEST.equals(normalizeKeyOrBlank(mode))
                ? RidingTweaksConfig.LABEL_MODE_STACKING
                : RidingTweaksConfig.LABEL_MODE_HIGHEST;
    }

    private static String speciesModeText(String mode) {
        return RidingTweaksConfig.SPECIES_MODE_STACKING.equals(normalizeKeyOrBlank(mode)) ? "Stacking" : "Override";
    }

    private static String nextSpeciesMode(String mode) {
        return RidingTweaksConfig.SPECIES_MODE_STACKING.equals(normalizeKeyOrBlank(mode))
                ? RidingTweaksConfig.SPECIES_MODE_OVERRIDE
                : RidingTweaksConfig.SPECIES_MODE_STACKING;
    }

    private static String ivEvModeText(String mode) {
        return switch (normalizeKeyOrBlank(mode)) {
            case RidingTweaksConfig.IV_EV_MODE_COMBINED -> "Combined";
            case RidingTweaksConfig.IV_EV_MODE_SEPARATE -> "Separate";
            default -> "Off";
        };
    }

    private static String nextIvEvMode(String mode) {
        return switch (normalizeKeyOrBlank(mode)) {
            case RidingTweaksConfig.IV_EV_MODE_OFF -> RidingTweaksConfig.IV_EV_MODE_COMBINED;
            case RidingTweaksConfig.IV_EV_MODE_COMBINED -> RidingTweaksConfig.IV_EV_MODE_SEPARATE;
            default -> RidingTweaksConfig.IV_EV_MODE_OFF;
        };
    }

    private static List<Component> stackingModeTooltip() {
        return List.of(
                Component.literal("Additive adds each change from x1."),
                Component.literal("Example: 1.6 and 1.6 => 1 + (0.6 + 0.6) = 2.2").withStyle(ChatFormatting.GRAY),
                Component.empty(),
                Component.literal("Multiplicative multiplies all active factors together."),
                Component.literal("Example: 1.6 and 1.6 => 1.6 x 1.6 = 2.56").withStyle(ChatFormatting.GRAY)
        );
    }

    private static List<Component> labelModeTooltip() {
        return List.of(
                Component.literal("Highest uses only the largest matching label multiplier."),
                Component.literal("Stacking uses every matching label multiplier."),
                Component.literal("Stacking follows the Additive/Multiplicative multiplier mode from the General tab.").withStyle(ChatFormatting.GRAY)
        );
    }

    private static List<Component> speciesModeTooltip() {
        return List.of(
                Component.literal("Override uses the species multiplier instead of label multipliers."),
                Component.literal("Stacking adds the species multiplier on top of label multipliers."),
                Component.literal("Stacking follows the Additive/Multiplicative multiplier mode from the General tab.").withStyle(ChatFormatting.GRAY)
        );
    }

    private static List<Component> statKeyTooltip() {
        return List.of(
                Component.literal("Chooses which Pokemon stat feeds this stamina stat-scaling section."),
                Component.literal("HP is the default endurance-like stat. Other stats can use nature scaling too.").withStyle(ChatFormatting.GRAY)
        );
    }

    private static List<Component> ivEvModeTooltip(String statName) {
        return List.of(
                Component.literal("Off ignores " + statName + " IV and " + statName + " EV."),
                Component.literal("Combined scales from 0 to 283 using " + statName + " IV + " + statName + " EV."),
                Component.literal("Separate scales " + statName + " IV and " + statName + " EV as two factors."),
                Component.literal("These factors follow the Additive/Multiplicative multiplier mode from the General tab.").withStyle(ChatFormatting.GRAY)
        );
    }

    private static String formatDouble(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.ROOT, "%.4f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String displayKey(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        String normalized = key.replace('_', ' ').trim();
        return normalized.substring(0, 1).toUpperCase(Locale.ROOT) + normalized.substring(1);
    }

    private static String normalizeKey(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeKeyOrBlank(String value) {
        return value == null ? "" : normalizeKey(value);
    }

    private static RidingTweaksConfigManager manager() {
        return CobblemonRidingTweaks.configManager();
    }

    private record LabelLine(String text, int x, int y, int color) {
    }

    private record TooltipArea(int x, int y, int width, int height, List<Component> lines) {
    }

    private record MultiplierRange(double min, double max) {
    }

    @FunctionalInterface
    public interface ServerConfigUpdateSender {
        void send(String configJson);
    }

    private enum Preset {
        DEFAULT("Default", "Restores neutral x1 riding multipliers."),
        BALANCED("Balanced", "Adds level, HP/Speed training and rarity bonuses, with mints and hypertraining enabled.");

        private final String displayName;
        private final String description;

        Preset(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
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
        STAMINA_RIDE_STYLES("Stamina - Ride Styles & Behaviours", false, false, false),
        STAMINA_LABELS("Stamina - Labels", true, true, false),
        STAMINA_SPECIES("Stamina - Species", true, false, true),
        SPEED_LEVEL("Speed - Scaling", false, false, false),
        SPEED_RIDE_STYLES("Speed - Ride Styles & Behaviours", false, false, false),
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
