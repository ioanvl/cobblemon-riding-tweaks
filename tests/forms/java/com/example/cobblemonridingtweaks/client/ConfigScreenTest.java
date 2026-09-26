package com.example.cobblemonridingtweaks.client;

import com.example.cobblemonridingtweaks.CobblemonRidingTweaks;
import com.example.cobblemonridingtweaks.config.RidingTweaksConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.FormattedText;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/** Builds real screen controls with conservative text metrics, without a renderer or running game. */
public final class ConfigScreenTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("riding-screen-");
        try {
            CobblemonRidingTweaks.init(directory);
            geometryAndPages();
            navigationAndDrafts();
            disabledStates();
            System.out.println("Config screen passed: both layouts, all pages, equal footer, resize/draft retention, "
                    + "independent scrolling, keyboard navigation, compact picker dismissal, read-only navigation and parent status.");
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static void geometryAndPages() throws Exception {
        for (int[] size : List.of(new int[]{320, 240}, new int[]{400, 240}, new int[]{426, 520},
                new int[]{480, 300}, new int[]{640, 360}, new int[]{960, 540}, new int[]{480, 220})) {
            var screen = screen(size[0], size[1]);
            var layout = layout(screen);
            check(layout.sidebar() == (size[0] >= 426), "Sidebar/compact selection at representative GUI sizes");
            if (layout.sidebar()) {
                check(layout.sidebarWidth() <= size[0] / 4, "Sidebar stays below one quarter of screen width");
                check(layout.contentWidth() >= 280, "Content retains usable width");
                check(layout.contentLeft() >= layout.left() + layout.sidebarWidth() + 8, "Panels remain separated");
            }
            for (var section : RidingTweaksConfigScreen.Section.values()) {
                field(screen, "selectedSection", section);
                call(screen, "rebuild");
                int rowsTop = (int) call(screen, "rowsTop");
                int viewportBottom = (int) call(screen, "rowViewportBottom");
                int footerY = (int) call(screen, "footerButtonsY");
                for (var child : screen.children()) {
                    if (child instanceof AbstractWidget widget) {
                        check(widget.getX() >= 0 && widget.getRight() <= size[0], "Widget fits width: " + section + " / " + widget.getMessage().getString());
                        check(widget.getY() >= 0 && widget.getBottom() <= size[1], "Widget fits height: " + section);
                        if (widget.getX() >= layout.contentLeft()) {
                            check(widget.getRight() <= layout.contentLeft() + layout.contentWidth(), "Controls stay in content panel");
                            if (widget.getY() >= rowsTop && widget.getY() < footerY) {
                                check(widget.getBottom() <= viewportBottom, "Settings and Add controls leave the notice line clear: " + section);
                            }
                        }
                    }
                }
                Button reload = button(screen, "Reload"), save = button(screen, "Save"), done = button(screen, "Done");
                check(reload.getY() == save.getY() && save.getY() == done.getY(), "Footer shares one row");
                check(reload.getWidth() == save.getWidth() && save.getWidth() == done.getWidth(), "Footer uses equal widths");
                check(reload.getRight() < save.getX() && save.getRight() < done.getX(), "Footer order and spacing");
            }
        }
    }

    private static void navigationAndDrafts() throws Exception {
        var screen = screen(480, 300);
        var draft = (RidingTweaksConfig) field(screen, "localDraft");
        int visibleRows = (int) call(screen, "visibleRows");
        check(visibleRows == 9, "General uses the reclaimed footer space at 480x300");
        screen.children().stream().filter(child -> child instanceof EditBox).map(child -> (EditBox) child)
                .findFirst().orElseThrow().setValue("2.25");
        check(draft.stamina.globalMultiplier == 2.25, "Field input updates the draft immediately");
        check((int) call(screen, "visibleRows") == visibleRows, "Unsaved notice does not move fields while editing");
        screen.mouseScrolled(30, 180, 0, -1);
        check((int) field(screen, "sidebarScroll") == 1 && (int) field(screen, "scrollRow") == 0, "Sidebar wheel does not scroll settings");
        screen.mouseScrolled(300, 150, 0, -1);
        check((int) field(screen, "scrollRow") == 1 && (int) field(screen, "sidebarScroll") == 1, "Settings wheel does not scroll navigation");
        button(screen, "Species").onPress(); // Stamina species is visible after scrolling once.
        check(field(screen, "selectedSection") == RidingTweaksConfigScreen.Section.STAMINA_SPECIES, "Sidebar selects its page");
        screen.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0);
        check(field(screen, "selectedSection") == RidingTweaksConfigScreen.Section.SPEED_LEVEL, "Down reaches the next group");
        for (int i = 0; i < 3; i++) screen.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0);
        check(field(screen, "selectedSection") == RidingTweaksConfigScreen.Section.SPEED_SPECIES, "Keyboard reaches offscreen navigation");

        screen.width = 320;
        screen.height = 240;
        call(screen, "rebuild");
        check(!layout(screen).sidebar() && field(screen, "localDraft") == draft, "Narrow resize retains draft");
        check(draft.stamina.globalMultiplier == 2.25, "Resize preserves unsaved values");
        sectionButton(screen).onPress();
        check(field(screen, "picker") != null, "Compact section uses the shared picker");
        screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
        check(field(screen, "picker") == null, "Escape dismisses section picker");
        sectionButton(screen).onPress();
        for (char c : "behaviour".toCharArray()) screen.charTyped(c, 0);
        screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        check(field(screen, "selectedSection") == RidingTweaksConfigScreen.Section.STAMINA_RIDE_STYLES, "Search/Enter selects Behaviour");
        check(field(screen, "picker") == null, "Selecting closes picker");
        check(screen.getFocused() == sectionButton(screen), "Selection returns focus to the rebuilt section button");
        sectionButton(screen).onPress();
        Button save = button(screen, "Save");
        screen.mouseClicked(save.getX() + 2, save.getY() + 2, 0);
        check(field(screen, "picker") == null, "Outside click closes picker");
        check(CobblemonRidingTweaks.configManager().copyLocalConfig().stamina.globalMultiplier != 2.25, "Dismissal does not click through to Save");

        // Navigation may open while editing is prohibited. No world is needed to exercise that guard.
        Class<?> tab = Class.forName(RidingTweaksConfigScreen.class.getName() + "$Tab");
        @SuppressWarnings({"rawtypes", "unchecked"}) Object server = Enum.valueOf((Class) tab, "SERVER");
        field(screen, "selectedTab", server);
        sectionButton(screen).onPress();
        check(field(screen, "picker") != null, "Read-only server tab can open navigation");
        screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
        field(screen, "selectedSection", RidingTweaksConfigScreen.Section.SPEED_SPECIES);
        screen.width = 480;
        screen.height = 220;
        call(screen, "rebuild");
        check(layout(screen).sidebar(), "Wider resize returns to sidebar");
        check(button(screen, ">> Species") != null, "Selected page is revealed after resize");
        check(field(screen, "localDraft") == draft, "Mode changes keep draft identity");
        screen.height = 520;
        call(screen, "rebuild");
        screen.height = 220;
        call(screen, "rebuild");
        check(button(screen, ">> Species") != null, "Shorter sidebar viewport reveals the selected page");
    }

    private static void disabledStates() throws Exception {
        var config = new RidingTweaksConfig().sanitize();
        config.stamina.levelScaling.level100Multiplier = 10;
        config.enabled = false;
        check(RidingTweaksConfigScreen.featureSummary(config, config.stamina).equals("Off"), "Master switch uses the compact Off summary");
        check(RidingTweaksConfigScreen.disabledReason(config, RidingTweaksConfigScreen.Section.STAMINA_LEVEL).equals("Mod disabled"), "Parent state dims child navigation");
        check(RidingTweaksConfigScreen.disabledReason(config, RidingTweaksConfigScreen.Section.GENERAL).isBlank(), "General remains available");
        config.enabled = true;
        config.stamina.enabled = false;
        check(RidingTweaksConfigScreen.featureSummary(config, config.stamina).equals("Off"), "Feature switch is explicit");
        config.stamina.enabled = true;
        check(RidingTweaksConfigScreen.featureSummary(config, config.stamina).contains("10"), "Enabled summary still computes ranges");
        var selected = new java.util.concurrent.atomic.AtomicReference<String>();
        var picker = new SearchablePicker(null, 320, 240, Button.builder(net.minecraft.network.chat.Component.empty(), b -> {}).bounds(30, 50, 200, 20).build(),
                List.of(new PickerOption("STAMINA_LEVEL", "Stamina - Scaling (Off)", true)), "STAMINA_LEVEL", "Choose section", "Search sections", "No matching sections", null, selected::set, ">> ");
        picker.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        check(selected.get().equals("STAMINA_LEVEL"), "Dimmed navigation remains selectable");
    }

    private static RidingTweaksConfigScreen screen(int width, int height) throws Exception {
        var screen = new RidingTweaksConfigScreen(null);
        Field font = Screen.class.getDeclaredField("font");
        font.setAccessible(true);
        font.set(screen, new MetricsFont());
        screen.width = width;
        screen.height = height;
        screen.init();
        return screen;
    }

    private static RidingTweaksConfigScreen.PanelLayout layout(RidingTweaksConfigScreen screen) throws Exception {
        return (RidingTweaksConfigScreen.PanelLayout) field(screen, "layout");
    }

    private static Button button(RidingTweaksConfigScreen screen, String label) {
        return screen.children().stream().filter(c -> c instanceof Button b && b.getMessage().getString().equals(label))
                .map(c -> (Button) c).findFirst().orElseThrow(() -> new AssertionError("Missing button: " + label));
    }

    private static Button sectionButton(RidingTweaksConfigScreen screen) {
        return screen.children().stream().filter(c -> c instanceof Button b && b.getMessage().getString().endsWith(SearchablePicker.INDICATOR))
                .map(c -> (Button) c).findFirst().orElseThrow();
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void field(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object call(Object target, String name) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class MetricsFont extends Font {
        MetricsFont() { super(id -> null, false); }
        @Override public int width(String text) { return text.length() * 6; }
        @Override public int width(FormattedText text) { return width(text.getString()); }
        @Override public String plainSubstrByWidth(String text, int width) { return text.substring(0, Math.clamp(width / 6, 0, text.length())); }
        @Override public String plainSubstrByWidth(String text, int width, boolean reverse) {
            int length = Math.clamp(width / 6, 0, text.length());
            return reverse ? text.substring(text.length() - length) : text.substring(0, length);
        }
    }
}
