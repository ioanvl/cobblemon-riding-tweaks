package com.example.cobblemonridingtweaks.client;

import com.example.cobblemonridingtweaks.CobblemonRidingTweaks;
import com.example.cobblemonridingtweaks.config.RidingTweaksConfig;
import com.example.cobblemonridingtweaks.config.RidingTweaksConfigManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
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

    private static Supplier<List<PickerOption>> speciesProvider = List::of;
    private static Supplier<Collection<String>> labelsProvider = List::of;
    private List<PickerOption> speciesChoices = List.of();
    private static Function<String, List<PickerOption>> speciesFormsProvider = species -> List.of();
    private SearchablePicker picker;
    private Button pickerAnchor;
    private Button sectionSelector;

    private final Screen parent;
    private final List<LabelLine> labelLines = new ArrayList<>();
    private final List<TooltipArea> tooltipAreas = new ArrayList<>();
    private Tab selectedTab = Tab.LOCAL;
    private Section selectedSection = Section.GENERAL;
    private int scrollRow;
    private int sidebarScroll;
    private int sidebarViewportRows;
    private PanelLayout layout = PanelLayout.forScreen(320, 240);
    private RidingTweaksConfig localDraft;
    private RidingTweaksConfig serverDraft;

    public RidingTweaksConfigScreen(Screen parent) {
        super(Component.literal(CobblemonRidingTweaks.MOD_NAME));
        this.parent = parent;
    }

    @Override
    protected void init() {
        setFocused(null);
        picker = null;
        pickerAnchor = null;
        sectionSelector = null;
        labelLines.clear();
        tooltipAreas.clear();
        ensureDrafts();
        if (selectedSection.speciesSection) {
            speciesChoices = speciesProvider.get();
        }
        if (selectedTab == Tab.SERVER && !showServerTabs()) {
            selectedTab = Tab.LOCAL;
        }
        boolean wasSidebar = layout.sidebar();
        layout = PanelLayout.forScreen(width, height);
        sidebarScroll = Math.clamp(sidebarScroll, 0, maxSidebarScroll());
        if (layout.sidebar() && (!wasSidebar || sidebarViewportRows != sidebarRows())) {
            revealSelectedSection();
        }
        sidebarViewportRows = sidebarRows();
        scrollRow = Math.clamp(scrollRow, 0, maxScrollRows());

        int centerX = contentLeft() + contentWidth() / 2;
        if (showServerTabs()) {
            int tabWidth = Math.min(120, (contentWidth() - 8) / 2);
            addRenderableWidget(Button.builder(tabText(Tab.LOCAL), button -> selectTab(Tab.LOCAL))
                    .bounds(centerX - tabWidth - 4, tabsY(), tabWidth, 20).build());
            addRenderableWidget(Button.builder(tabText(Tab.SERVER), button -> selectTab(Tab.SERVER))
                    .bounds(centerX + 4, tabsY(), tabWidth, 20).build());
        }
        if (layout.sidebar()) {
            addSidebarControls();
        } else {
            addRenderableWidget(Button.builder(Component.literal("<"), button -> changeSection(-1))
                    .bounds(contentLeft(), sectionY(), 28, 20).build());
            sectionSelector = Button.builder(sectionText(), this::openSectionPicker)
                    .bounds(contentLeft() + 36, sectionY(), contentWidth() - 72, 20).build();
            setTooltip(sectionSelector, sectionDescription(selectedSection));
            addRenderableWidget(sectionSelector);
            addRenderableWidget(Button.builder(Component.literal(">"), button -> changeSection(1))
                    .bounds(contentRight() - 28, sectionY(), 28, 20).build());
        }

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

        int buttonWidth = layout.footerButtonWidth();
        Button reload = Button.builder(Component.literal("Reload"), button -> {
            if (!isActiveMultiplayerSession()) {
                manager().reloadActiveLocal();
                showFeedback(isSingleplayerSession() ? "Reloaded and applied config." : "Reloaded config.", true);
            } else {
                manager().reload();
                showFeedback("Reloaded local config.", true);
            }
            localDraft = manager().copyLocalConfig();
            rebuild();
        }).bounds(layout.footerButtonX(0), footerButtonsY(), buttonWidth, 20).build();
        reload.active = selectedTab == Tab.LOCAL;
        addRenderableWidget(reload);
        Button save = Button.builder(Component.literal("Save"), button -> saveCurrentConfig())
                .bounds(layout.footerButtonX(1), footerButtonsY(), buttonWidth, 20).build();
        save.active = selectedTabIsEditable();
        addRenderableWidget(save);
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(layout.footerButtonX(2), footerButtonsY(), buttonWidth, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        if (layout.sidebar()) {
            graphics.fill(layout.left() - 4, 8, layout.left() + layout.sidebarWidth() + 4, height - 8, 0x60000000);
            graphics.fill(contentLeft() - 7, 8, contentLeft() - 6, height - 8, 0x80606060);
        }
        super.render(graphics, picker == null ? mouseX : -1, picker == null ? mouseY : -1, partialTick);
        if (layout.sidebar()) {
            drawSidebarHeader(graphics, picker == null ? mouseX : -1, picker == null ? mouseY : -1);
            drawCenteredStringWithBacking(graphics, sectionDescription(selectedSection), titleY(),
                    isSectionFeatureDisabled(selectedSection) ? DIMMED_TEXT_COLOR : 0xFFFFFF, 0x70000000);
            drawVerticalScrollBar(graphics, layout.left() + layout.sidebarWidth() - 2,
                    sidebarTop(), sidebarRows() * 22 - 2, 11, sidebarRows(), sidebarScroll);
        } else {
            drawCenteredStringWithBacking(graphics, this.title.getString(), titleY(), 0xFFFFFF, 0x70000000);
            drawCenteredStringWithBacking(graphics, configSummary(), summaryY(), 0xD0D0D0, 0x70000000);
        }
        labelLines.forEach(line -> graphics.drawString(this.font, line.text, line.x, line.y, line.color));
        drawMapScrollBar(graphics);
        String status = statusText();
        if (!status.isBlank()) {
            drawCenteredStringWithBacking(graphics, status, statusY(), 0xE0E0E0, 0x85000000);
        }

        String feedback = feedbackText();
        String notice = feedback.isBlank() ? unsavedChangesText() : feedback;
        if (!notice.isBlank()) {
            drawCenteredStringWithBacking(
                    graphics,
                    notice,
                    noticeY(!status.isBlank()),
                    feedback.isBlank() ? 0xFFE080 : feedbackSuccess ? 0x80FF80 : 0xFF8080,
                    0xA0000000
            );
        }
        if (picker == null) {
            drawLabelTooltip(graphics, mouseX, mouseY);
        } else {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 400);
            picker.render(graphics, mouseX, mouseY, partialTick);
            graphics.pose().popPose();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (picker != null) {
            return picker.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        if (layout.sidebar() && mouseX < contentLeft() - 6) {
            if (mouseY >= sidebarTop()) {
                sidebarScroll = Math.clamp(sidebarScroll - (int) Math.signum(verticalAmount), 0, maxSidebarScroll());
                rebuild();
            }
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
        if (picker != null) {
            if (picker.isMouseOver(mouseX, mouseY)) {
                picker.mouseClicked(mouseX, mouseY, button);
            } else {
                closeSearchablePicker();
            }
            return true;
        }
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        // Screen focuses the clicked button after its callback opens the popover.
        if (picker != null) {
            setFocused(picker);
        } else if (getFocused() instanceof NavigationButton && !children().contains(getFocused())) {
            focusSelectedSection();
        }
        return handled;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (picker != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeSearchablePicker();
                return true;
            }
            return picker.keyPressed(keyCode, scanCode, modifiers);
        }
        if (getFocused() instanceof NavigationButton &&
                (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN)) {
            changeSection(keyCode == GLFW.GLFW_KEY_DOWN ? 1 : -1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        return picker == null ? super.charTyped(character, modifiers) : picker.charTyped(character, modifiers);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dragX, double dragY) {
        return picker == null ? super.mouseDragged(x, y, button, dragX, dragY)
                : picker.mouseDragged(x, y, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (picker != null) {
            setDragging(false);
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    public static void setCatalogProviders(Supplier<List<PickerOption>> species,
                                           Function<String, List<PickerOption>> forms,
                                           Supplier<Collection<String>> labels) {
        speciesProvider = species;
        speciesFormsProvider = forms;
        labelsProvider = labels;
    }

    private void openPicker(Button anchor, List<PickerOption> options, String current,
                            String title, String searchHint, String emptyMessage,
                            Function<String, PickerOption> customOption, Consumer<String> select) {
        openPicker(anchor, options, current, title, searchHint, emptyMessage, customOption, select, true);
    }

    private void openPicker(Button anchor, List<PickerOption> options, String current,
                            String title, String searchHint, String emptyMessage,
                            Function<String, PickerOption> customOption, Consumer<String> select, boolean editsConfig) {
        if (editsConfig && !selectedTabIsEditable()) {
            return;
        }
        pickerAnchor = anchor;
        picker = new SearchablePicker(font, width, height, anchor, options, current,
                title, searchHint, emptyMessage, customOption, selected -> {
                    closeSearchablePicker();
                    if (!editsConfig || selectedTabIsEditable()) {
                        select.accept(selected);
                    }
                }, editsConfig ? "✓ " : ">> ");
        addWidget(picker);
        setFocused(picker);
    }

    private void closeSearchablePicker() {
        if (picker != null) {
            removeWidget(picker);
            picker = null;
            setDragging(false);
            setFocused(pickerAnchor);
            pickerAnchor = null;
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

    public static void setServerConfigUpdateSender(ServerConfigUpdateSender sender) {
        serverConfigUpdateSender = sender == null
                ? json -> showFeedback("Server config editing is not available on this loader.", false)
                : sender;
    }

    private void addGeneralControls() {
        RidingTweaksConfig config = viewingConfig();
        int centerX = this.width / 2;
        addMasterToggle("Mod Enabled", config.enabled, value -> config.enabled = value, centerX, rowY(0),
                "Turns this config on or off. Off leaves stamina and speed at neutral x1.");

        addHeader("Stamina", 1);
        addMasterToggle("Enabled", config.stamina.enabled, value -> config.stamina.enabled = value, centerX, rowY(2),
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
        addMasterToggle("Enabled", config.speed.enabled, value -> config.speed.enabled = value, centerX, rowY(12),
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
        addMapEntries(feature.rideStyleMultipliers, 2, RIDE_STYLE_KEYS);

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
        addMapEntries(feature.behaviourMultipliers, startRow + 1, keys, 24);
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
        List<String> keys = new ArrayList<>(feature.labelMultipliers.keySet());
        for (int index = 0; index < keys.size(); index++) {
            if (shouldShowRow(4 + index)) {
                addLabelRow(feature.labelMultipliers, keys.get(index), rowY(4 + index));
            }
        }
        int y = rowsTop() + visibleRows() * ROW_HEIGHT + 4;
        Button add = Button.builder(rowButtonText("Add Label", y), button -> openLabelPicker(button, feature.labelMultipliers, null))
                .bounds(contentLeft(), y, contentWidth(), 20).build();
        add.active = selectedTabIsEditable();
        addRenderableWidget(add);
    }

    private void addLabelRow(Map<String, Double> labels, String key, int y) {
        Button label = Button.builder(rowButtonText(fitText(displayKey(key), editableKeyWidth() - 18) + SearchablePicker.INDICATOR, y),
                        button -> openLabelPicker(button, labels, key))
                .bounds(editableKeyX(), y, editableKeyWidth(), 20).build();
        label.active = selectedTabIsEditable();
        label.setTooltip(Tooltip.create(Component.literal(key)));
        addRenderableWidget(label);
        EditBox value = textBox(editableValueX(), y, editableValueWidth(), formatDouble(labels.get(key)));
        value.setResponder(text -> parseMultiplier(key, text, parsed -> labels.put(key, parsed)));
        addRenderableWidget(value);
        Button remove = Button.builder(rowButtonText("X", y), button -> {
            labels.remove(key);
            scrollRow = Math.max(0, scrollRow - 1);
            rebuild();
        }).bounds(removeButtonX(), y, REMOVE_BUTTON_WIDTH, 20).build();
        remove.active = selectedTabIsEditable();
        addRenderableWidget(remove);
    }

    private void openLabelPicker(Button anchor, Map<String, Double> labels, String current) {
        var known = new LinkedHashSet<>(RidingTweaksConfig.knownCobblemonLabels());
        known.addAll(labelsProvider.get());
        known.addAll(labels.keySet());
        List<PickerOption> options = known.stream().map(RidingTweaksConfigScreen::normalizeKey).distinct()
                .filter(key -> key.equals(current) || !labels.containsKey(key))
                .map(key -> new PickerOption(key, displayKey(key)))
                .sorted(java.util.Comparator.comparing(PickerOption::label, String.CASE_INSENSITIVE_ORDER)).toList();
        openPicker(anchor, options, current, "Choose label", "Search labels or enter custom ID", "No matching labels",
                query -> !query.isBlank() && query.length() <= 128 && !labels.containsKey(query)
                        ? new PickerOption(query, "Use custom label: " + query) : null,
                selected -> {
                    if (!selected.equals(current) && labels.containsKey(selected)) {
                        return;
                    }
                    if (current == null) {
                        labels.put(selected, 1.0D);
                        scrollRow = Math.max(0, rowCountForSection() - visibleRows());
                    } else {
                        Map<String, Double> renamed = new LinkedHashMap<>();
                        labels.forEach((key, value) -> renamed.put(key.equals(current) ? selected : key, value));
                        labels.clear();
                        labels.putAll(renamed);
                    }
                    rebuild();
                });
    }

    private void addSpeciesControls(RidingTweaksConfig.FeatureTweaks feature) {
        int centerX = this.width / 2;
        addToggle("Enabled", feature.speciesOverridesEnabled, value -> feature.speciesOverridesEnabled = value, centerX, rowY(0), 0);
        addSpeciesModeToggle("Species Behaviour", feature, centerX, rowY(1), 0);
        if (shouldShowRow(2)) {
            int y = rowY(2) + 6;
            labelLines.add(new LabelLine("Species", contentLeft(), y, HEADER_TEXT_COLOR));
            labelLines.add(new LabelLine("Form", speciesFormX(), y, HEADER_TEXT_COLOR));
            labelLines.add(new LabelLine("Multiplier", editableValueX(), y, HEADER_TEXT_COLOR));
        }
        for (int index = 0; index < feature.speciesOverrides.size(); index++) {
            if (shouldShowRow(3 + index)) {
                addSpeciesRow(feature, feature.speciesOverrides.get(index), rowY(3 + index));
            }
        }
        int buttonY = rowsTop() + visibleRows() * ROW_HEIGHT + 4;
        Button add = Button.builder(rowButtonText("Add Species", buttonY), button -> {
            feature.speciesOverrides.add(new RidingTweaksConfig.SpeciesOverride("", RidingTweaksConfig.ALL_FORMS, 1.0D));
            scrollRow = Math.max(0, rowCountForSection() - visibleRows());
            rebuild();
        }).bounds(contentLeft(), buttonY, contentWidth(), 20).build();
        add.active = selectedTabIsEditable();
        addRenderableWidget(add);
    }

    private void addSpeciesRow(RidingTweaksConfig.FeatureTweaks feature, RidingTweaksConfig.SpeciesOverride entry, int y) {
        Button form = Button.builder(Component.empty(), button -> {
            List<PickerOption> options = new ArrayList<>();
            options.add(new PickerOption(RidingTweaksConfig.ALL_FORMS, "All forms"));
            options.addAll(speciesFormsProvider.apply(entry.species));
            if (options.stream().noneMatch(option -> option.id().equals(entry.form))) {
                options.add(new PickerOption(entry.form, entry.form + " (unavailable)"));
            }
            openPicker(button, options, entry.form, "Choose form", "Search forms", "No matching forms", null, selected -> {
                entry.form = selected;
                updateFormButton(button, entry, y);
            });
        }).bounds(speciesFormX(), y, speciesFormWidth(), 20).build();
        form.active = selectedTabIsEditable();
        updateFormButton(form, entry, y);

        Button species = Button.builder(Component.empty(), button -> {
            speciesChoices = speciesProvider.get();
            String current = speciesChoiceId(entry.species);
            List<PickerOption> options = new ArrayList<>(speciesChoices);
            if (!current.isBlank() && options.stream().noneMatch(option -> option.id().equals(current))) {
                options.addFirst(new PickerOption(current, entry.species + " (unavailable)"));
            }
            openPicker(button, options, current, "Choose species", "Search Pokémon", "No matching Pokémon", null, selected -> {
                if (!selected.equals(current)) {
                    entry.form = RidingTweaksConfig.ALL_FORMS;
                }
                entry.species = selected;
                updateSpeciesButton(button, entry, y);
                updateFormButton(form, entry, y);
            });
        }).bounds(contentLeft(), y, speciesFormX() - contentLeft() - 6, 20).build();
        species.active = selectedTabIsEditable();
        updateSpeciesButton(species, entry, y);
        addRenderableWidget(species);
        addRenderableWidget(form);
        EditBox multiplier = textBox(editableValueX(), y, editableValueWidth(), formatDouble(entry.multiplier));
        multiplier.setResponder(value -> parseMultiplier("Multiplier", value, parsed -> entry.multiplier = parsed));
        addRenderableWidget(multiplier);
        Button remove = Button.builder(rowButtonText("X", y), button -> {
            feature.speciesOverrides.remove(entry);
            scrollRow = Math.max(0, scrollRow - 1);
            rebuild();
        }).bounds(removeButtonX(), y, REMOVE_BUTTON_WIDTH, 20).build();
        remove.active = selectedTabIsEditable();
        addRenderableWidget(remove);
    }

    private static String speciesChoiceId(String species) {
        return species.isBlank() || species.contains(":") ? species : "cobblemon:" + species;
    }

    private void updateSpeciesButton(Button button, RidingTweaksConfig.SpeciesOverride entry, int y) {
        String id = speciesChoiceId(entry.species);
        String label = entry.species.isBlank() ? "(Choose Pokémon)" : speciesChoices.stream()
                .filter(option -> option.id().equals(id)).map(PickerOption::label).findFirst()
                .orElse(entry.species + " (unavailable)");
        button.setMessage(rowButtonText(fitText(label, button.getWidth() - 18) + SearchablePicker.INDICATOR, y));
        button.setTooltip(Tooltip.create(Component.literal(label + (entry.species.isBlank() ? "" : "\n" + entry.species))));
    }

    private void updateFormButton(Button button, RidingTweaksConfig.SpeciesOverride entry, int y) {
        button.active = selectedTabIsEditable() && !entry.species.isBlank();
        String label = RidingTweaksConfig.ALL_FORMS.equals(entry.form) ? "All forms"
                : speciesFormsProvider.apply(entry.species).stream().filter(option -> option.id().equals(entry.form))
                .map(PickerOption::label).findFirst().orElse(entry.form + " (unavailable)");
        button.setMessage(rowButtonText(fitText(label, button.getWidth() - 18) + SearchablePicker.INDICATOR, y));
        button.setTooltip(Tooltip.create(Component.literal(label + "\nA specific form takes precedence over All forms.")));
    }

    private int speciesFormWidth() {
        return Math.max(60, Math.min(130, (editableValueX() - contentLeft()) * 2 / 5));
    }

    private int speciesFormX() {
        return editableValueX() - 6 - speciesFormWidth();
    }

    private List<RidingTweaksConfig.SpeciesOverride> currentSpeciesOverrides() {
        return selectedSection == Section.STAMINA_SPECIES ? viewingConfig().stamina.speciesOverrides
                : viewingConfig().speed.speciesOverrides;
    }

    private void addMapEntries(Map<String, Double> multipliers, int startRow, List<String> keys) {
        addMapEntries(multipliers, startRow, keys, 0);
    }

    private void addMapEntries(Map<String, Double> multipliers, int startRow, List<String> keys, int indent) {
        int centerX = this.width / 2;
        for (int index = 0; index < keys.size(); index++) {
            String key = keys.get(index);
            int row = startRow + index;
            if (!shouldShowRow(row)) {
                continue;
            }
            double value = multipliers.getOrDefault(key, 1.0D);
            addMapValueRow(multipliers, key, value, centerX, rowY(row), indent);
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

    private void addMasterToggle(String label, boolean currentValue, Consumer<Boolean> setter, int centerX, int y, String tooltip) {
        Button button = addToggle(label, currentValue, setter, centerX, y, 0, tooltip);
        if (button != null && button.active && !isPageContentDimmedAt(y)) {
            button.setMessage(Component.literal(onOff(currentValue)).withStyle(currentValue ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
    }

    private Button addToggle(
            String label,
            boolean currentValue,
            Consumer<Boolean> setter,
            int centerX,
            int y,
            int indent,
            String tooltip
    ) {
        if (!isRowVisibleAt(y)) {
            return null;
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
        return button;
    }

    private void addPresetButtons(int y) {
        if (!isRowVisibleAt(y)) {
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
        if (!isRowVisibleAt(y)) {
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
        if (!isRowVisibleAt(y)) {
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
        if (!isRowVisibleAt(y)) {
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
        if (!isRowVisibleAt(y)) {
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
        if (!isRowVisibleAt(y)) {
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
        if (!isRowVisibleAt(y)) {
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

    private Map<String, Double> currentMap() {
        return switch (selectedSection) {
            case STAMINA_LABELS -> viewingConfig().stamina.labelMultipliers;
            case SPEED_LABELS -> viewingConfig().speed.labelMultipliers;
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
            case STAMINA_LABELS, SPEED_LABELS -> 4 + currentMap().size();
            case STAMINA_SPECIES, SPEED_SPECIES -> 3 + currentSpeciesOverrides().size();
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

    private boolean isRowVisibleAt(int y) {
        return y >= rowsTop() && y < rowsTop() + visibleRows() * ROW_HEIGHT;
    }

    private int rowY(int rowIndex) {
        return rowsTop() + (rowIndex - scrollRow) * ROW_HEIGHT;
    }

    private int titleY() {
        return layout.sidebar() || this.height < 260 ? 12 : 24;
    }

    private int summaryY() {
        return titleY() + 16;
    }

    private int tabsY() {
        return layout.sidebar() ? 30 : summaryY() + 18;
    }

    private int sectionY() {
        return showServerTabs() ? tabsY() + 24 : summaryY() + 24;
    }

    private int rowsTop() {
        return layout.sidebar() ? (showServerTabs() ? 60 : 36) : sectionY() + 34;
    }

    private int footerButtonsY() {
        return Math.max(0, this.height - 28);
    }

    private int statusY() {
        return Math.max(rowsTop() + 4, footerButtonsY() - 16);
    }

    private int noticeY(boolean statusVisible) {
        return statusVisible ? Math.max(rowsTop() + 4, statusY() - 14) : statusY();
    }

    private int rowViewportBottom() {
        // Feedback temporarily replaces the unsaved notice; neither changes the viewport while editing.
        return Math.max(rowsTop() + ROW_HEIGHT, noticeY(!statusText().isBlank()) - 4);
    }

    private int contentWidth() {
        return layout.contentWidth();
    }

    private int contentLeft() {
        return layout.contentLeft();
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

    private void changeSection(int direction) {
        Section[] sections = Section.values();
        selectSection(sections[Math.floorMod(selectedSection.ordinal() + direction, sections.length)]);
    }

    private void selectSection(Section section) {
        selectedSection = section;
        scrollRow = 0;
        revealSelectedSection();
        rebuild();
        focusSelectedSection();
    }

    private void focusSelectedSection() {
        if (sectionSelector != null) {
            setFocused(sectionSelector);
            return;
        }
        children().stream().filter(child -> child instanceof NavigationButton navigation && navigation.section == selectedSection)
                .findFirst().ifPresent(this::setFocused);
    }

    private void openSectionPicker(Button anchor) {
        List<PickerOption> options = java.util.Arrays.stream(Section.values())
                .map(section -> new PickerOption(section.name(), section.title
                        + (isSectionFeatureDisabled(section) ? " (Off)" : ""), isSectionFeatureDisabled(section))).toList();
        openPicker(anchor, options, selectedSection.name(), "Choose section", "Search sections", "No matching sections",
                null, selected -> selectSection(Section.valueOf(selected)), false);
    }

    private void selectTab(Tab tab) {
        selectedTab = tab;
        rebuild();
    }

    private int sidebarTop() {
        return 90;
    }

    private int sidebarRows() {
        return Math.max(1, Math.min(11, (height - 10 - sidebarTop()) / 22));
    }

    private int maxSidebarScroll() {
        return 11 - sidebarRows();
    }

    private int sidebarIndex(Section section) {
        return section == Section.GENERAL ? 0 : section.ordinal() + (section.ordinal() < 5 ? 1 : 2);
    }

    private void revealSelectedSection() {
        int index = sidebarIndex(selectedSection);
        sidebarScroll = Math.clamp(sidebarScroll, Math.max(0, index - sidebarRows() + 1), index);
        sidebarScroll = Math.min(sidebarScroll, maxSidebarScroll());
    }

    private void addSidebarControls() {
        addSidebarHeading("Stamina", 1, viewingConfig().stamina);
        addSidebarHeading("Speed", 6, viewingConfig().speed);
        for (Section section : Section.values()) {
            int row = sidebarIndex(section) - sidebarScroll;
            if (row < 0 || row >= sidebarRows()) {
                continue;
            }
            int indent = section == Section.GENERAL ? 0 : 8;
            String label = (section == selectedSection ? ">> " : "") + section.shortTitle();
            Component text = Component.literal(label).withStyle(isSectionFeatureDisabled(section) ? ChatFormatting.GRAY
                    : section == selectedSection ? ChatFormatting.YELLOW : ChatFormatting.WHITE);
            NavigationButton button = new NavigationButton(section, layout.left() + indent,
                    sidebarTop() + row * 22, layout.sidebarWidth() - indent - 8, text);
            setTooltip(button, sectionDescription(section));
            addRenderableWidget(button);
        }
    }

    private void addSidebarHeading(String title, int index, RidingTweaksConfig.FeatureTweaks feature) {
        int row = index - sidebarScroll;
        if (row >= 0 && row < sidebarRows()) {
            labelLines.add(new LabelLine(title, layout.left() + 4, sidebarTop() + row * 22 + 6,
                    !viewingConfig().enabled || !feature.enabled ? DIMMED_TEXT_COLOR : HEADER_TEXT_COLOR));
        }
    }

    private void drawSidebarHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        drawSidebarLine(graphics, "Cobblemon", 14, 0xFFFFFF, mouseX, mouseY);
        drawSidebarLine(graphics, "Riding Tweaks", 26, 0xFFFFFF, mouseX, mouseY);
        drawSidebarLine(graphics, "Config " + viewingConfig().configVersion, 42, 0xAAAAAA, mouseX, mouseY);
        drawSidebarLine(graphics, "Stamina: " + featureSummary(viewingConfig(), viewingConfig().stamina), 60,
                viewingConfig().enabled && viewingConfig().stamina.enabled ? NORMAL_TEXT_COLOR : DIMMED_TEXT_COLOR, mouseX, mouseY);
        drawSidebarLine(graphics, "Speed: " + featureSummary(viewingConfig(), viewingConfig().speed), 72,
                viewingConfig().enabled && viewingConfig().speed.enabled ? NORMAL_TEXT_COLOR : DIMMED_TEXT_COLOR, mouseX, mouseY);
    }

    private void drawSidebarLine(GuiGraphics graphics, String text, int y, int color, int mouseX, int mouseY) {
        graphics.drawString(font, fitText(text, layout.sidebarWidth() - 8), layout.left() + 4, y, color);
        if (isWithin(mouseX, mouseY, layout.left(), y - 2, layout.sidebarWidth(), 12)) {
            graphics.renderTooltip(font, Component.literal(text), mouseX, mouseY);
        }
    }

    private static boolean isWithin(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private void drawMapScrollBar(GuiGraphics graphics) {
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
            return Component.literal(">> " + tab.displayName).withStyle(ChatFormatting.YELLOW);
        }
        return Component.literal(tab.displayName).withStyle(ChatFormatting.GRAY);
    }

    private Component sectionText() {
        return Component.literal(fitText(selectedSection.title, contentWidth() - 94) + SearchablePicker.INDICATOR)
                .withStyle(isSectionFeatureDisabled(selectedSection) ? ChatFormatting.GRAY : ChatFormatting.WHITE);
    }

    private boolean isSectionFeatureDisabled(Section section) {
        return !disabledReason(viewingConfig(), section).isBlank();
    }

    private String sectionDescription(Section section) {
        String reason = disabledReason(viewingConfig(), section);
        return section.title + (reason.isBlank() ? "" : " — " + reason);
    }

    static String disabledReason(RidingTweaksConfig config, Section section) {
        if (section == Section.GENERAL) {
            return "";
        }
        if (!config.enabled) {
            return "Mod disabled";
        }
        RidingTweaksConfig.FeatureTweaks feature = section.ordinal() < 5 ? config.stamina : config.speed;
        if (!feature.enabled) {
            return section.ordinal() < 5 ? "Stamina disabled" : "Speed disabled";
        }
        boolean enabled = switch (section) {
            case STAMINA_LEVEL, SPEED_LEVEL -> feature.levelScalingEnabled;
            case STAMINA_RIDE_STYLES, SPEED_RIDE_STYLES -> feature.ridingMultipliersEnabled;
            case STAMINA_LABELS, SPEED_LABELS -> feature.labelMultipliersEnabled;
            case STAMINA_SPECIES, SPEED_SPECIES -> feature.speciesOverridesEnabled;
            case GENERAL -> true;
        };
        return enabled ? "" : section.shortTitle() + " disabled";
    }

    private String configSummary() {
        RidingTweaksConfig config = viewingConfig();
        return "Config " + config.configVersion + " | stamina " + featureSummary(config, config.stamina)
                + " | speed " + featureSummary(config, config.speed);
    }

    static String featureSummary(RidingTweaksConfig config, RidingTweaksConfig.FeatureTweaks feature) {
        return config.enabled && feature.enabled ? "x" + formatMultiplierRange(summaryRange(config, feature)) : "Off";
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
            MultiplierRange withSpecies = combineAndClamp(feature, baseRanges, speciesRange(feature.speciesOverrides, false));
            return new MultiplierRange(
                    Math.min(withoutSpecies.min(), withSpecies.min()),
                    Math.max(withoutSpecies.max(), withSpecies.max())
            );
        }

        List<MultiplierRange> ranges = new ArrayList<>(baseRanges);
        if (feature.speciesOverridesEnabled && feature.speciesOverrides != null && !feature.speciesOverrides.isEmpty()) {
            ranges.add(speciesRange(feature.speciesOverrides, true));
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

    private static MultiplierRange speciesRange(List<RidingTweaksConfig.SpeciesOverride> entries, boolean includeOne) {
        double min = includeOne ? 1.0D : Double.POSITIVE_INFINITY;
        double max = includeOne ? 1.0D : Double.NEGATIVE_INFINITY;
        for (RidingTweaksConfig.SpeciesOverride entry : entries) {
            double value = safeMultiplier(entry.multiplier);
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        return entries.isEmpty() ? new MultiplierRange(1.0D, 1.0D) : new MultiplierRange(min, max);
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
        if (isGeneralFeatureContentDimmedAt(y)) {
            return true;
        }
        boolean parentDisabled = !viewingConfig().enabled || selectedSection != Section.GENERAL
                && !(selectedSection.ordinal() < 5 ? viewingConfig().stamina.enabled : viewingConfig().speed.enabled);
        return isPageContentDimmed() && (parentDisabled || y >= rowY(1));
    }

    private boolean isPageContentDimmed() {
        return isSectionFeatureDisabled(selectedSection);
    }

    private boolean isGeneralFeatureContentDimmedAt(int y) {
        if (selectedSection != Section.GENERAL) {
            return false;
        }

        RidingTweaksConfig config = viewingConfig();
        int row = rowIndexForY(y);
        return !config.enabled && row > 0 && row < 21
                || !config.stamina.enabled && row > 2 && row < 11
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
        if (!selectedTabIsEditable()) {
            return;
        }
        for (RidingTweaksConfig.FeatureTweaks feature : List.of(viewingConfig().stamina, viewingConfig().speed)) {
            var targets = new java.util.HashSet<List<String>>();
            for (RidingTweaksConfig.SpeciesOverride entry : feature.speciesOverrides) {
                String species = normalizeKey(entry.species);
                if (species.isBlank() || net.minecraft.resources.ResourceLocation.tryParse(species) == null) {
                    showFeedback("Enter a valid species ID or remove the empty row before saving.", false);
                    return;
                }
                if (!targets.add(List.of(species, entry.form))) {
                    showFeedback("Duplicate species/form entry: " + species + ". Choose a different form or remove the row.", false);
                    return;
                }
            }
        }
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
        sidebarScroll = 0;
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
        String text = fitText(rawText, contentWidth() - 10);
        int centerX = contentLeft() + contentWidth() / 2;
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

    record PanelLayout(boolean sidebar, int left, int sidebarWidth, int contentLeft, int contentWidth) {
        static PanelLayout forScreen(int width, int height) {
            int margin = Math.max(MIN_MARGIN, Math.min(24, width / 24));
            int available = Math.max(80, width - margin * 2);
            boolean sidebar = available >= 392 && height >= 220;
            int navigation = sidebar ? 100 : 0;
            int total = Math.min(MAX_CONTENT_WIDTH + (sidebar ? 112 : 0), available);
            int left = (width - total) / 2;
            return new PanelLayout(sidebar, left, navigation, left + (sidebar ? 112 : 0),
                    total - (sidebar ? 112 : 0));
        }

        int footerButtonWidth() {
            return (contentWidth - FIELD_GAP * 2) / 3;
        }

        int footerButtonX(int index) {
            int spare = contentWidth - footerButtonWidth() * 3 - FIELD_GAP * 2;
            return contentLeft + spare / 2 + index * (footerButtonWidth() + FIELD_GAP);
        }
    }

    private final class NavigationButton extends Button {
        private final Section section;

        NavigationButton(Section section, int x, int y, int width, Component text) {
            super(x, y, width, 20, text, button -> selectSection(section), DEFAULT_NARRATION);
            this.section = section;
        }

        @Override
        public void renderString(GuiGraphics graphics, Font font, int color) {
            graphics.drawString(font, getMessage(), getX() + 4, getY() + 6, color);
        }

        @Override
        protected net.minecraft.network.chat.MutableComponent createNarrationMessage() {
            return Component.literal(sectionDescription(section));
        }
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

    enum Section {
        GENERAL("General", false, false, false),
        STAMINA_LEVEL("Stamina - Scaling", false, false, false),
        STAMINA_RIDE_STYLES("Stamina - Behaviour", false, false, false),
        STAMINA_LABELS("Stamina - Labels", true, true, false),
        STAMINA_SPECIES("Stamina - Species", true, false, true),
        SPEED_LEVEL("Speed - Scaling", false, false, false),
        SPEED_RIDE_STYLES("Speed - Behaviour", false, false, false),
        SPEED_LABELS("Speed - Labels", true, true, false),
        SPEED_SPECIES("Speed - Species", true, false, true);

        String shortTitle() {
            int separator = title.indexOf(" - ");
            return separator < 0 ? title : title.substring(separator + 3);
        }

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
