package com.example.cobblemonridingtweaks.client;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/** Resolves translated form names while preserving registered IDs for matching. */
public final class FormNames {
    private FormNames() {}

    private static final String FORM_KEY = "cobblemon.ui.pokedex.info.form.";
    private static final Map<String, String> REGIONS = Map.of(
            "kantonian_form", "kanto", "johtonian_form", "johto", "hoennian_form", "hoenn",
            "sinnohan_form", "sinnoh", "unovan_form", "unova", "kalosian_form", "kalos",
            "alolan_form", "alola", "galarian_form", "galar", "hisuian_form", "hisui", "paldean_form", "paldea"
    );

    public static PickerOption forForm(String speciesTranslationId, String name, boolean standard,
                                         Collection<String> labels, Function<String, String> translate) {
        String id = name.trim().toLowerCase(Locale.ROOT);
        String suffix = standard ? "" : "-" + id.replaceAll("[^a-z0-9]", "");
        String display = translated(translate, FORM_KEY + speciesTranslationId + suffix);
        if (display == null && standard) {
            display = translated(translate, FORM_KEY + "normal");
            if (display == null) {
                display = "Standard";
            }
            var regions = labels.stream().map(label -> REGIONS.get(label.toLowerCase(Locale.ROOT)))
                    .filter(region -> region != null).distinct().toList();
            if (regions.size() == 1) {
                String region = regions.getFirst();
                String regionName = translated(translate, "cobblemon.ui.pokedex.region." + region);
                if (regionName == null) {
                    regionName = Character.toUpperCase(region.charAt(0)) + region.substring(1);
                }
                display += " (" + regionName + ")";
            }
        }
        if (display == null) {
            display = translated(translate, FORM_KEY + id);
        }
        return new PickerOption(id, display == null ? name : display);
    }

    private static String translated(Function<String, String> translate, String key) {
        String value = translate.apply(key);
        return value == null || value.isBlank() || value.equals(key) ? null : value;
    }
}
