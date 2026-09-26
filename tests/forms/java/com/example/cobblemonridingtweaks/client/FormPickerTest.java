package com.example.cobblemonridingtweaks.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Exercises the actual widget's input and positioning without opening a render window. */
public final class FormPickerTest {
    public static void main(String[] args) {
        var choices = new ArrayList<PickerOption>();
        choices.add(new PickerOption("*", "All forms"));
        choices.add(new PickerOption("normal", "Standard (Kalos)"));
        choices.add(new PickerOption("hisui", "Hisuian Form"));
        for (int i = 0; i < 40; i++) {
            choices.add(new PickerOption("custom-" + i, "Custom " + i));
        }
        var selected = new AtomicReference<String>();
        for (int width : List.of(320, 427, 640)) {
            for (int y : List.of(40, 190)) {
                Button anchor = anchor(width - 90, y);
                SearchablePicker picker = new SearchablePicker(null, width, 240, anchor, choices, "custom-25", "Choose form", "Search forms", "No matching forms", null, selected::set);
                check(picker.getX() >= 0 && picker.getRight() <= width, "Popover fits horizontally");
                check(picker.getY() >= 0 && picker.getBottom() <= 240, "Popover fits vertically");
                check(y == 40 ? picker.getY() >= anchor.getBottom() : picker.getBottom() <= anchor.getY(),
                        "Opens below or above the anchor according to available space");
                picker.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
                check("custom-25".equals(selected.get()), "Current selection remains selected, even outside first page");
            }
        }
        SearchablePicker picker = new SearchablePicker(null, 320, 240, anchor(100, 40), choices, "*", "Choose form", "Search forms", "No matching forms", null, selected::set);
        selected.set(null);
        for (char c : "hisui".toCharArray()) {
            picker.charTyped(c, 0);
        }
        picker.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        check("hisui".equals(selected.get()), "Search and Enter choose the matching form");
        picker.charTyped('z', 0);
        selected.set(null);
        picker.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        check(selected.get() == null, "No-results Enter does not change the selected form");

        picker = new SearchablePicker(null, 320, 240, anchor(100, 40), choices, "*", "Choose form", "Search forms", "No matching forms", null, selected::set);
        picker.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0);
        picker.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        check("normal".equals(selected.get()), "Keyboard distinguishes Standard from All forms");
        picker.keyPressed(GLFW.GLFW_KEY_UP, 0, 0);
        picker.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        check("*".equals(selected.get()), "Keyboard can return to All forms");
        for (int i = 0; i < 60; i++) {
            picker.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0);
        }
        picker.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        check("custom-39".equals(selected.get()), "Keyboard scroll reaches last item and clamps");
        picker.mouseScrolled(-1, -1, 0, 1);
        picker.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        check("custom-39".equals(selected.get()), "Wheel outside popover leaves it unchanged");
        for (int i = 0; i < 60; i++) {
            picker.mouseScrolled(picker.getX() + 10, picker.getY() + 35, 0, 1);
        }
        picker.mouseClicked(picker.getX() + 10, picker.getY() + 35, 0);
        check("*".equals(selected.get()), "Wheel and click return to first item");
        speciesAndLabels();
        System.out.println("Form picker passed: anchor placement, high GUI scales, search, empty results, keyboard, scroll and mouse selection.");
    }

    private static void speciesAndLabels() {
        var selected = new AtomicReference<String>();
        var species = List.of(new PickerOption("cobblemon:goodra", "Goodra"), new PickerOption("addon:goodra", "Goodra"),
                new PickerOption("cobblemon:meowth", "Meowth"));
        for (String query : List.of("GOODRA", "cobblemon:goodra", "addon:goodra")) {
            var picker = new SearchablePicker(null, 320, 240, anchor(100, 40), species, null,
                    "Choose species", "Search Pokémon", "No matching Pokémon", null, selected::set);
            for (char c : query.toCharArray()) {
                picker.charTyped(c, 0);
            }
            picker.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
            check(selected.get().equals(query.startsWith("addon:") ? "addon:goodra" : "cobblemon:goodra"),
                    "Species search is case-insensitive and returns the selected ID, preserving namespaces");
        }
        var labels = List.of(new PickerOption("legendary", "Legendary"), new PickerOption("ultra_beast", "Ultra Beast"));
        for (String query : List.of("Ultra Beast", "ultra_beast", "custom_pack_label")) {
            var picker = new SearchablePicker(null, 320, 240, anchor(100, 190), labels, null,
                    "Choose label", "Search labels", "No matching labels",
                    text -> text.isBlank() ? null : new PickerOption(text, "Use custom label: " + text), selected::set);
            for (char c : query.toCharArray()) {
                picker.charTyped(c, 0);
            }
            picker.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
            check(selected.get().equals(query.equals("custom_pack_label") ? query : "ultra_beast"),
                    "Labels search by display text/ID and allow explicit custom values");
        }
        // An empty catalogue is still a usable custom-label picker.
        var picker = new SearchablePicker(null, 320, 240, anchor(100, 190), List.of(), null,
                "Choose label", "Search labels", "No matching labels",
                text -> text.isBlank() ? null : new PickerOption(text, text), selected::set);
        picker.charTyped('x', 0);
        picker.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        check(selected.get().equals("x"), "Custom label can be added when there are no remaining known choices");
    }

    private static Button anchor(int x, int y) {
        return Button.builder(Component.literal("Form"), button -> {}).bounds(x, y, 80, 20).build();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
