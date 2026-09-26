package com.example.cobblemonridingtweaks.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

/** An anchored, modal popover; the parent keeps the surrounding page visible. */
final class SearchablePicker extends AbstractWidget {
    static final String INDICATOR = " ▾";
    private static final int ROW_HEIGHT = 20;
    private static final int SEARCH_HEIGHT = 26;
    private final Font font;
    private final EditBox search;
    private final String emptyMessage;
    private final String current;
    private final String selectedMarker;
    private final Consumer<String> select;
    private List<PickerOption> filtered;
    private int highlighted;
    private int scroll;

    SearchablePicker(Font font, int screenWidth, int screenHeight, AbstractWidget anchor,
               List<PickerOption> options, String current, String title, String searchHint, String emptyMessage,
               Function<String, PickerOption> customOption, Consumer<String> select) {
        this(font, screenWidth, screenHeight, anchor, options, current, title, searchHint, emptyMessage,
                customOption, select, "✓ ");
    }

    SearchablePicker(Font font, int screenWidth, int screenHeight, AbstractWidget anchor,
               List<PickerOption> options, String current, String title, String searchHint, String emptyMessage,
               Function<String, PickerOption> customOption, Consumer<String> select, String selectedMarker) {
        super(0, 0, 0, 0, Component.literal(title));
        this.font = font;
        this.emptyMessage = emptyMessage;
        this.filtered = options;
        this.current = current;
        this.selectedMarker = selectedMarker;
        this.select = select;
        width = Math.min(Math.max(anchor.getWidth(), 200), Math.max(40, screenWidth - 16));
        setX(Math.clamp(anchor.getX(), 8, Math.max(8, screenWidth - width - 8)));
        int below = screenHeight - anchor.getBottom() - 8;
        int above = anchor.getY() - 8;
        int wanted = SEARCH_HEIGHT + ROW_HEIGHT * Math.min(7, Math.max(1, options.size())) + 4;
        boolean openBelow = below >= wanted || below >= above;
        int available = Math.max(SEARCH_HEIGHT + ROW_HEIGHT + 4, openBelow ? below : above);
        int rows = Math.max(1, Math.min(7, (available - SEARCH_HEIGHT - 4) / ROW_HEIGHT));
        height = SEARCH_HEIGHT + Math.min(rows, Math.max(1, options.size())) * ROW_HEIGHT + 4;
        setY(Math.clamp(openBelow ? anchor.getBottom() : anchor.getY() - height,
                4, Math.max(4, screenHeight - height - 4)));
        search = new EditBox(font, getX() + 4, getY() + 4, width - 8, 18, Component.literal(searchHint));
        search.setMaxLength(128);
        search.setHint(Component.literal(searchHint + "..."));
        search.setFocused(true);
        search.setResponder(query -> {
            String normalized = query.trim().toLowerCase(Locale.ROOT);
            filtered = new ArrayList<>(options.stream().filter(option -> option.label().toLowerCase(Locale.ROOT).contains(normalized)
                    || option.id().toLowerCase(Locale.ROOT).contains(normalized)).toList());
            PickerOption custom = customOption == null ? null : customOption.apply(normalized);
            if (custom != null && options.stream().noneMatch(option -> option.id().equals(custom.id()))) {
                filtered.add(custom);
            }
            highlighted = 0;
            scroll = 0;
        });
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).id().equals(current)) {
                highlighted = i;
                break;
            }
        }
        keepHighlightVisible();
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(getX() - 1, getY() - 1, getRight() + 1, getBottom() + 1, 0xFFE0E0E0);
        graphics.fill(getX(), getY(), getRight(), getBottom(), 0xFF202020);
        search.render(graphics, mouseX, mouseY, partialTick);
        int hovered = optionAt(mouseX, mouseY);
        for (int row = 0; row < visibleRows() && scroll + row < filtered.size(); row++) {
            int index = scroll + row;
            PickerOption option = filtered.get(index);
            int y = getY() + SEARCH_HEIGHT + row * ROW_HEIGHT;
            if (index == hovered || index == highlighted) {
                graphics.fill(getX() + 2, y, getRight() - 6, y + ROW_HEIGHT, 0xFF505050);
            }
            String label = (option.id().equals(current) ? selectedMarker : "  ") + option.label();
            graphics.drawString(font, font.plainSubstrByWidth(label, width - 16), getX() + 5, y + 6,
                    option.dimmed() ? 0x8C8C8C : 0xFFFFFF);
        }
        if (filtered.isEmpty()) {
            graphics.drawString(font, emptyMessage, getX() + 5, getY() + SEARCH_HEIGHT + 6, 0xAAAAAA);
        }
        if (filtered.size() > visibleRows()) {
            int trackHeight = visibleRows() * ROW_HEIGHT;
            int thumb = Math.max(8, trackHeight * visibleRows() / filtered.size());
            int y = getY() + SEARCH_HEIGHT + (trackHeight - thumb) * scroll / (filtered.size() - visibleRows());
            graphics.fill(getRight() - 4, y, getRight() - 2, y + thumb, 0xFFCCCCCC);
        }
        if (hovered >= 0) {
            String label = filtered.get(hovered).label();
            String id = filtered.get(hovered).id();
            if (id.contains(":") || font.width(label) > width - 28) {
                graphics.renderTooltip(font, Component.literal(label + " (" + id + ")"), mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return true;
        }
        boolean inSearch = search.isMouseOver(mouseX, mouseY);
        search.setFocused(inSearch);
        if (inSearch) {
            search.mouseClicked(mouseX, mouseY, button);
        } else {
            int index = optionAt(mouseX, mouseY);
            if (index >= 0) {
                select.accept(filtered.get(index).id());
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (isMouseOver(mouseX, mouseY)) {
            scroll = Math.clamp(scroll - (int) Math.signum(vertical), 0, Math.max(0, filtered.size() - visibleRows()));
            highlighted = Math.clamp(highlighted, scroll, Math.max(scroll, Math.min(filtered.size() - 1, scroll + visibleRows() - 1)));
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return search.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        return search.charTyped(character, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_UP) {
            highlighted = Math.clamp(highlighted + (keyCode == GLFW.GLFW_KEY_DOWN ? 1 : -1), 0, Math.max(0, filtered.size() - 1));
            keepHighlightVisible();
        } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (!filtered.isEmpty()) {
                select.accept(filtered.get(highlighted).id());
            }
        } else if (keyCode == GLFW.GLFW_KEY_TAB) {
            search.setFocused(!search.isFocused());
        } else {
            search.keyPressed(keyCode, scanCode, modifiers);
        }
        return true;
    }

    private int visibleRows() {
        return Math.max(1, (height - SEARCH_HEIGHT - 4) / ROW_HEIGHT);
    }

    private int optionAt(double x, double y) {
        if (x < getX() + 2 || x >= getRight() - 6 || y < getY() + SEARCH_HEIGHT
                || y >= getY() + SEARCH_HEIGHT + visibleRows() * ROW_HEIGHT) {
            return -1;
        }
        int index = scroll + (int) (y - getY() - SEARCH_HEIGHT) / ROW_HEIGHT;
        return index < filtered.size() ? index : -1;
    }

    private void keepHighlightVisible() {
        scroll = Math.clamp(scroll, Math.max(0, highlighted - visibleRows() + 1), highlighted);
        scroll = Math.min(scroll, Math.max(0, filtered.size() - visibleRows()));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        String selection = filtered.isEmpty() ? emptyMessage : filtered.get(highlighted).label();
        output.add(NarratedElementType.TITLE, Component.literal(getMessage().getString() + ": " + selection));
        output.add(NarratedElementType.USAGE, Component.literal("Type to search. Up and Down to navigate, Enter to select, Escape to close."));
    }
}
