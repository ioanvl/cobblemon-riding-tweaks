package com.example.cobblemonridingtweaks.test;

import com.example.cobblemonridingtweaks.client.FormNames;
import com.example.cobblemonridingtweaks.client.BundledSpeciesCatalog;
import com.example.cobblemonridingtweaks.config.RidingTweaksConfig;
import com.example.cobblemonridingtweaks.config.RidingTweaksConfig.SpeciesOverride;
import com.example.cobblemonridingtweaks.config.RidingTweaksConfigManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.net.JarURLConnection;
import java.nio.file.FileSystems;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Runs on both loaders, using their actual Cobblemon species and language resources. */
public final class SpeciesFormTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("riding-forms-");
        try {
            migration(directory);
            matching(directory);
            syncAndDrafts(directory);
            names();
            bundledCatalog();
            System.out.println("Species forms passed: legacy migration, precedence, labels, both features/modes, "
                    + "sync, draft isolation, namespaces, missing forms and Cobblemon form names.");
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
    }

    private static void migration(Path directory) throws Exception {
        Path file = directory.resolve("cobblemon-riding-tweaks.json");
        Files.writeString(file, """
                {"configVersion":"1.2.0", "debugLogging":true,
                 "speed":{"globalMultiplier":1.2,"speciesOverrides":{"cobblemon:goodra":1.5,"addon:mount":0.8}},
                 "stamina":{"speciesMode":"stacking","speciesOverrides":{"goodra":2.0}}}
                """);
        var manager = RidingTweaksConfigManager.load(directory);
        var config = manager.config();
        check(config.configVersion.equals(RidingTweaksConfig.SUPPORTED_CONFIG_VERSION), "Migration version");
        check(config.debugLogging && config.speed.globalMultiplier == 1.2, "Unrelated settings survive");
        check(config.speed.speciesOverrides.size() == 2 && config.stamina.speciesOverrides.size() == 1, "All old entries survive");
        for (var feature : List.of(config.stamina, config.speed)) {
            check(feature.speciesOverrides.stream().allMatch(entry -> entry.form.equals("*")), "Legacy entries target All forms");
        }
        check(config.speed.speciesOverrides.get(1).species.equals("addon:mount"), "Custom namespace survives");
        check(config.speed.speciesOverrides.get(1).multiplier == 0.8, "Sub-one multiplier survives");
        expect(manager, "cobblemon:goodra", "normal", 1.7, 2.0);
        expect(manager, "cobblemon:goodra", "Hisui", 1.7, 2.0);
        String saved = manager.localConfigJson();
        check(JsonParser.parseString(saved).getAsJsonObject().getAsJsonObject("speed").get("speciesOverrides").isJsonArray(),
                "Saved schema is an explicit list");
        manager.reload();
        check(saved.equals(manager.localConfigJson()), "Migration is idempotent");
    }

    private static void matching(Path directory) {
        var manager = RidingTweaksConfigManager.load(directory);
        for (String mode : List.of("additive", "multiplicative")) {
            var config = new RidingTweaksConfig();
            for (var feature : List.of(config.speed, config.stamina)) {
                feature.stackingMode = mode;
                feature.maxFinalMultiplier = 100;
                feature.labelMultipliers.put("kalosian_form", 3.0);
                feature.speciesOverrides.add(new SpeciesOverride("cobblemon:goodra", "*", 2));
                feature.speciesOverrides.add(new SpeciesOverride("cobblemon:goodra", "normal", 1.5));
                feature.speciesOverrides.add(new SpeciesOverride("cobblemon:goodra", "hisui", 4));
                feature.speciesOverrides.add(new SpeciesOverride("addon:goodra", "normal", 0.5));
            }
            manager.replaceAndSaveActiveLocal(config);
            expect(manager, "cobblemon:goodra", "Normal", 1.5, 1.5);
            expect(manager, "cobblemon:goodra", "Hisui", 4, 4);
            expect(manager, "cobblemon:goodra", "Future-Form", 2, 2);
            expect(manager, "addon:goodra", "Normal", 0.5, 0.5);
            expect(manager, "other:goodra", "Normal", 3, 3);
            // Labels cannot turn an unknown form into the standard form.
            for (var feature : List.of(config.speed, config.stamina)) {
                feature.speciesOverrides.removeIf(entry -> entry.form.equals("*"));
            }
            expect(manager, "cobblemon:goodra", "Missing-Form", 3, 3);
            for (var feature : List.of(config.speed, config.stamina)) {
                feature.speciesMode = "stacking";
            }
            double stacked = mode.equals("additive") ? 3.5 : 4.5;
            expect(manager, "cobblemon:goodra", "Normal", stacked, stacked);
            for (var feature : List.of(config.speed, config.stamina)) {
                feature.speciesOverridesEnabled = false;
            }
            expect(manager, "cobblemon:goodra", "Normal", 3, 3);
            for (var feature : List.of(config.speed, config.stamina)) {
                feature.speciesOverridesEnabled = true;
                feature.labelMultipliersEnabled = false;
                feature.speciesOverrides.add(new SpeciesOverride("goodra", "*", 8));
                Collections.reverse(feature.speciesOverrides);
            }
            expect(manager, "cobblemon:goodra", "Normal", 1.5, 1.5);
            expect(manager, "other:goodra", "Normal", 8, 8);
            expect(manager, "cobblemon:goodra", null, 8, 8);
            expect(manager, "cobblemon:unrelated", "Normal", 1, 1);
            for (var feature : List.of(config.speed, config.stamina)) {
                feature.speciesOverrides.add(new SpeciesOverride("goodra", "hisui", 1.25));
                feature.speciesOverrides.add(new SpeciesOverride("cobblemon:goodra", "*", 9));
            }
            // Exact species wins within a form; a specific form wins over an exact-species fallback.
            expect(manager, "cobblemon:goodra", "Hisui", 4, 4);
            for (var feature : List.of(config.speed, config.stamina)) {
                feature.speciesOverrides.removeIf(entry -> entry.species.equals("cobblemon:goodra") && entry.form.equals("hisui"));
            }
            expect(manager, "cobblemon:goodra", "Hisui", 1.25, 1.25);
        }
    }

    private static void syncAndDrafts(Path directory) throws Exception {
        var manager = RidingTweaksConfigManager.load(directory);
        var config = new RidingTweaksConfig();
        config.speed.speciesOverrides.add(new SpeciesOverride(" COBBLEMON:GOODRA ", " NORMAL ", 1.5));
        config.stamina.speciesOverrides.add(new SpeciesOverride("cobblemon:goodra", "normal", 2.5));
        config.speed.speciesOverrides.add(new SpeciesOverride("addon:missing", "Unregistered form", 2));
        manager.replaceAndSaveActiveLocal(config);
        var copy = manager.copyLocalConfig();
        copy.speed.speciesOverrides.getFirst().multiplier = 9;
        expect(manager, "cobblemon:goodra", "normal", 1.5, 2.5);
        manager.reload();
        expect(manager, "cobblemon:goodra", "hisui", 1, 1);
        check(manager.localConfig().speed.speciesOverrides.get(1).form.equals("unregistered form"), "Unknown forms persist without broadening");
        String json = manager.localConfigJson();
        manager.awaitServerConfig();
        expect(manager, "cobblemon:goodra", "normal", 1, 1);
        manager.applyServerConfig(json, false);
        expect(manager, "cobblemon:goodra", "normal", 1.5, 2.5);
        check(!manager.canEditServerConfig(), "Form support grants no edit permission");
        manager.replaceAndSave(new RidingTweaksConfig());
        expect(manager, "cobblemon:goodra", "normal", 1.5, 2.5);
        manager.clearServerConfig();
        expect(manager, "cobblemon:goodra", "normal", 1, 1);
        check(manager.replaceFromRemoteJson(json), "Exact-contract form edit accepted");
        expect(manager, "cobblemon:goodra", "normal", 1.5, 2.5);
        String before = Files.readString(manager.path());
        String old = json.replace(RidingTweaksConfig.SUPPORTED_CONFIG_VERSION, "1.2.0");
        check(!manager.replaceFromRemoteJson(old), "Old contract rejected");
        check(before.equals(Files.readString(manager.path())), "Rejected edit preserves disk");
        manager.applyServerConfig(old, true);
        expect(manager, "cobblemon:goodra", "normal", 1, 1);
        check(!manager.canEditServerConfig(), "Old contract disables editing");
    }

    private static void names() throws Exception {
        JsonObject language = resource("assets/cobblemon/lang/en_us.json");
        Function<String, String> translate = key -> language.has(key) ? language.get(key).getAsString() : null;
        Map<String, String> expected = Map.of("goodra", "Standard (Kalos)", "meowth", "Standard (Kanto)",
                "giratina", "Altered Forme", "lycanroc", "Midday Form");
        for (var entry : expected.entrySet()) {
            String generation = switch (entry.getKey()) {
                case "goodra" -> "generation6";
                case "meowth" -> "generation1";
                case "giratina" -> "generation4";
                default -> "generation7";
            };
            JsonObject species = resource("data/cobblemon/species/" + generation + "/" + entry.getKey() + ".json");
            var labels = new ArrayList<String>();
            if (species.has("labels")) {
                species.getAsJsonArray("labels").forEach(label -> labels.add(label.getAsString()));
            }
            var option = FormNames.forForm(entry.getKey(), "Normal", true, labels, translate);
            check(option.id().equals("normal") && option.label().equals(entry.getValue()), "Base name for " + entry.getKey() + ": " + option.label());
        }
        check(FormNames.forForm("goodra", "Hisui", false, List.of("hisuian_form"), translate).label().startsWith("Hisuian"), "Translated alternate form");
        Function<String, String> missing = key -> key;
        check(FormNames.forForm("custom", "Normal", true, List.of(), missing).label().equals("Standard"), "Missing base data fallback");
        check(FormNames.forForm("custom", "Normal", true, List.of("kalosian_form", "hisuian_form"), missing).label().equals("Standard"), "Ambiguous region fallback");
        var custom = FormNames.forForm("custom", "Crystal Wing", false, List.of(), missing);
        check(custom.id().equals("crystal wing") && custom.label().equals("Crystal Wing"), "Unknown alternate name and exact identity survive");
        check(FormNames.forForm("goodra", "Normal", true, List.of("kalosian_form"), key -> switch (key) {
            case "cobblemon.ui.pokedex.info.form.normal" -> "Standardform";
            case "cobblemon.ui.pokedex.region.kalos" -> "Kalos-localized";
            default -> null;
        }).label().equals("Standardform (Kalos-localized)"), "Display uses current translations");
    }

    private static JsonObject resource(String path) throws Exception {
        try (var stream = SpeciesFormTest.class.getClassLoader().getResourceAsStream(path)) {
            check(stream != null, "Missing Cobblemon resource: " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static void bundledCatalog() throws Exception {
        var resource = SpeciesFormTest.class.getClassLoader().getResource("data/cobblemon/species/generation6/goodra.json");
        check(resource != null, "Bundled Goodra resource exists");
        if (resource.openConnection() instanceof JarURLConnection jar) {
            try (var files = FileSystems.newFileSystem(Path.of(jar.getJarFileURL().toURI()))) {
                checkCatalog(BundledSpeciesCatalog.load(List.of(files.getPath("/data/cobblemon/species"))));
            }
        } else {
            checkCatalog(BundledSpeciesCatalog.load(List.of(Path.of(resource.toURI()).getParent().getParent())));
        }
    }

    private static void checkCatalog(BundledSpeciesCatalog catalog) {
        // No world, PokemonSpecies registry or Minecraft instance is initialized for this test.
        var species = catalog.species(key -> null);
        check(species.size() > 900, "Title-screen catalogue reads all bundled species");
        check(species.stream().anyMatch(option -> option.id().equals("cobblemon:goodra") && option.label().equals("Goodra")),
                "Species picker displays Goodra and stores the full ID");
        var forms = catalog.forms("cobblemon:goodra", key -> null);
        check(forms.size() == 2, "Title-screen Goodra has both forms");
        check(forms.getFirst().id().equals("normal") && forms.getFirst().label().equals("Standard (Kalos)"), "Bundled standard naming");
        check(forms.stream().anyMatch(form -> form.id().equals("hisui")), "Bundled Hisui form");
        check(catalog.forms("addon:unknown", key -> null).isEmpty(), "Does not invent unavailable datapack species");
        check(catalog.labels().containsAll(List.of("legendary", "kalosian_form", "hisuian_form")), "Bundled labels available before a world loads");
        check(catalog.species(key -> key.equals("cobblemon.species.goodra.name") ? "Localized Goodra" : null)
                .stream().anyMatch(option -> option.label().equals("Localized Goodra")), "Species display responds to current language");
    }

    private static void expect(RidingTweaksConfigManager manager, String species, String form, double speed, double stamina) {
        List<String> labels = List.of("kalosian_form");
        double actualSpeed = manager.speedMultiplier(50, labels, species, form, "land", "horse", 0, 0, 0, false, false, false, false);
        double actualStamina = manager.enduranceMultiplier(50, labels, species, form, "land", "horse", 0, 0, 0, false, false, false, false);
        check(Math.abs(speed - actualSpeed) < 1e-8, species + "/" + form + ": speed expected " + speed + ", got " + actualSpeed);
        check(Math.abs(stamina - actualStamina) < 1e-8, species + "/" + form + ": stamina expected " + stamina + ", got " + actualStamina);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
