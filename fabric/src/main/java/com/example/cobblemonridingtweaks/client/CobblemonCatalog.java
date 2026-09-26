package com.example.cobblemonridingtweaks.client;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Species;
import com.example.cobblemonridingtweaks.CobblemonRidingTweaks;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/** Live registry metadata, with bundled choices before a world/server has loaded species. */
public final class CobblemonCatalog {
    private static Supplier<List<Path>> bundledRoots = List::of;
    private static BundledSpeciesCatalog bundled;

    private CobblemonCatalog() {}

    public static void setBundledRoots(Supplier<List<Path>> roots) {
        bundledRoots = roots;
        bundled = null;
    }

    public static List<PickerOption> species() {
        if (PokemonSpecies.getSpecies().isEmpty()) {
            return sorted(bundled().species(CobblemonCatalog::translate));
        }
        return sorted(PokemonSpecies.getSpecies().stream().map(species -> {
            var displayName = species.getTranslatedName();
            String name = displayName.getContents() instanceof TranslatableContents contents
                    && !Language.getInstance().has(contents.getKey()) ? species.getName() : displayName.getString();
            return new PickerOption(species.getResourceIdentifier().toString(),
                    name == null || name.isBlank() ? species.getName() : name);
        }).toList());
    }

    public static List<PickerOption> forms(String speciesId) {
        String normalized = speciesId.trim().toLowerCase(Locale.ROOT);
        ResourceLocation id = ResourceLocation.tryParse(normalized.contains(":") ? normalized : "cobblemon:" + normalized);
        if (id == null) {
            return List.of();
        }
        if (PokemonSpecies.getSpecies().isEmpty()) {
            return bundled().forms(id.toString(), CobblemonCatalog::translate);
        }
        Species species = PokemonSpecies.getByIdentifier(id);
        if (species == null) {
            return List.of();
        }
        var forms = new LinkedHashMap<String, PickerOption>();
        add(forms, species, species.getStandardForm());
        for (FormData form : species.getForms()) {
            add(forms, species, form);
        }
        return List.copyOf(forms.values());
    }

    public static Collection<String> labels() {
        if (PokemonSpecies.getSpecies().isEmpty()) {
            return bundled().labels();
        }
        var labels = new LinkedHashSet<String>();
        for (Species species : PokemonSpecies.getSpecies()) {
            labels.addAll(species.getStandardForm().getLabels());
            species.getForms().forEach(form -> labels.addAll(form.getLabels()));
        }
        return labels;
    }

    private static BundledSpeciesCatalog bundled() {
        if (bundled == null) {
            try {
                bundled = BundledSpeciesCatalog.load(bundledRoots.get());
            } catch (Exception exception) {
                LoggerFactory.getLogger(CobblemonRidingTweaks.MOD_NAME).warn("Could not read bundled species choices", exception);
                bundled = new BundledSpeciesCatalog();
            }
        }
        return bundled;
    }

    private static List<PickerOption> sorted(List<PickerOption> choices) {
        return choices.stream().sorted(Comparator.comparing(PickerOption::label, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(PickerOption::id)).toList();
    }

    private static String translate(String key) {
        Language language = Language.getInstance();
        return language.has(key) ? language.getOrDefault(key) : null;
    }

    private static void add(LinkedHashMap<String, PickerOption> forms, Species species, FormData form) {
        PickerOption option = FormNames.forForm(species.showdownId(), form.getName(),
                form.equals(species.getStandardForm()), form.getLabels(), CobblemonCatalog::translate);
        forms.putIfAbsent(option.id(), option);
    }
}
