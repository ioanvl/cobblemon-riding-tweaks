package com.example.cobblemonridingtweaks.client;

/** A stable config ID and its human-readable display text. */
public record PickerOption(String id, String label, boolean dimmed) {
    public PickerOption(String id, String label) {
        this(id, label, false);
    }
}
