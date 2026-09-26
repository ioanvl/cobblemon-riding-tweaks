package com.example.cobblemonridingtweaks.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Display metadata for title-screen editing; never loads or modifies Cobblemon's gameplay registry. */
public final class BundledSpeciesCatalog {
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public static BundledSpeciesCatalog load(List<Path> speciesRoots) throws IOException {
        var catalog = new BundledSpeciesCatalog();
        for (Path root : speciesRoots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (var paths = Files.walk(root)) {
                for (Path path : paths.filter(file -> file.toString().endsWith(".json")).sorted().toList()) {
                    try (var reader = Files.newBufferedReader(path)) {
                        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                        String name = json.get("name").getAsString();
                        String key = path.getFileName().toString().replaceFirst("\\.json$", "");
                        var baseLabels = labels(json);
                        var forms = new ArrayList<Form>();
                        forms.add(new Form("Normal", true, baseLabels));
                        if (json.has("forms")) {
                            for (JsonElement element : json.getAsJsonArray("forms")) {
                                JsonObject form = element.getAsJsonObject();
                                forms.add(new Form(form.get("name").getAsString(), false,
                                        form.has("labels") ? labels(form) : baseLabels));
                            }
                        }
                        catalog.entries.put("cobblemon:" + key, new Entry(name, key, forms));
                    }
                }
            }
        }
        return catalog;
    }

    public List<PickerOption> species(Function<String, String> translate) {
        return entries.entrySet().stream().map(item -> {
            Entry entry = item.getValue();
            String name = translate.apply("cobblemon.species." + entry.translationId + ".name");
            return new PickerOption(item.getKey(), name == null || name.isBlank() ? entry.name : name);
        }).toList();
    }

    public List<PickerOption> forms(String species, Function<String, String> translate) {
        Entry entry = entries.get(species);
        return entry == null ? List.of() : entry.forms.stream().map(form -> FormNames.forForm(
                entry.translationId, form.name, form.standard, form.labels, translate)).toList();
    }

    public Collection<String> labels() {
        return entries.values().stream().flatMap(entry -> entry.forms.stream()).flatMap(form -> form.labels.stream())
                .distinct().toList();
    }

    private static List<String> labels(JsonObject object) {
        var labels = new ArrayList<String>();
        if (object.has("labels")) {
            object.getAsJsonArray("labels").forEach(label -> labels.add(label.getAsString()));
        }
        return labels;
    }

    private record Entry(String name, String translationId, List<Form> forms) {}
    private record Form(String name, boolean standard, List<String> labels) {}
}
