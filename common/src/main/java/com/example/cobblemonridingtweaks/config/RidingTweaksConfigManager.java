package com.example.cobblemonridingtweaks.config;

import com.example.cobblemonridingtweaks.CobblemonRidingTweaks;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;

public final class RidingTweaksConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(CobblemonRidingTweaks.MOD_NAME);
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

    private final Path path;
    private RidingTweaksConfig localConfig;
    private RidingTweaksConfig activeConfig;
    private boolean serverConfigActive;
    private boolean serverConfigEditable;
    private boolean awaitingServerConfig;

    private RidingTweaksConfigManager(Path path, RidingTweaksConfig config) {
        this.path = path;
        this.localConfig = config;
        this.activeConfig = config;
    }

    public static RidingTweaksConfigManager load(Path configDirectory) {
        Path path = configDirectory.resolve("cobblemon-riding-tweaks.json");
        RidingTweaksConfig config = read(path);
        warnForInvalidNumbers(config);
        config.sanitize();
        logDebugNotes(config);
        RidingTweaksConfigManager manager = new RidingTweaksConfigManager(path, config);
        manager.save();
        LOGGER.info("Loaded {} config from {}", CobblemonRidingTweaks.MOD_NAME, path);
        return manager;
    }

    public RidingTweaksConfig config() {
        return activeConfig;
    }

    public RidingTweaksConfig localConfig() {
        return localConfig;
    }

    public boolean isServerConfigActive() {
        return serverConfigActive;
    }

    public boolean isAwaitingServerConfig() {
        return awaitingServerConfig;
    }

    public boolean canEditServerConfig() {
        return serverConfigActive && serverConfigEditable;
    }

    public Path path() {
        return path;
    }

    public void replaceAndSave(RidingTweaksConfig config) {
        warnForInvalidNumbers(config);
        this.localConfig = config.sanitize();
        if (!serverConfigActive) {
            this.activeConfig = this.localConfig;
        }
        logDebugNotes(this.localConfig);
        save();
    }

    public void reload() {
        localConfig = read(path);
        warnForInvalidNumbers(localConfig);
        localConfig.sanitize();
        if (!serverConfigActive) {
            activeConfig = localConfig;
        }
        logDebugNotes(localConfig);
        save();
    }

    public void save() {
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(localConfig, writer);
            }
        } catch (IOException exception) {
            LOGGER.error("Failed to save {} config to {}", CobblemonRidingTweaks.MOD_NAME, path, exception);
        }
    }

    public void awaitServerConfig() {
        activeConfig = vanillaConfig();
        serverConfigActive = false;
        serverConfigEditable = false;
        awaitingServerConfig = true;
    }

    public void applyServerConfig(String json, boolean editable) {
        RidingTweaksConfig serverConfig = fromJson(json);
        warnForInvalidNumbers(serverConfig);
        serverConfig.sanitize();
        activeConfig = serverConfig;
        serverConfigActive = true;
        serverConfigEditable = editable;
        awaitingServerConfig = false;
        logDebugNotes(serverConfig);
        LOGGER.info("Applied server-authoritative {} config", CobblemonRidingTweaks.MOD_NAME);
    }

    public void clearServerConfig() {
        activeConfig = localConfig;
        serverConfigActive = false;
        serverConfigEditable = false;
        awaitingServerConfig = false;
    }

    public String localConfigJson() {
        return GSON.toJson(localConfig);
    }

    public String activeConfigJson() {
        return GSON.toJson(activeConfig);
    }

    public boolean replaceFromRemoteJson(String json) {
        RidingTweaksConfig config = submittedConfigFromJson(json);
        if (config == null) {
            return false;
        }

        replaceAndSave(config);
        return true;
    }

    public double enduranceMultiplier(
            int level,
            Collection<String> labels,
            String speciesId,
            String rideStyle,
            String behaviour
    ) {
        if (!activeConfig.enabled || !activeConfig.stamina.enabled) {
            return 1.0D;
        }

        double multiplier = staminaLevelMultiplier(level)
                * keyedMultiplier(activeConfig.stamina.rideStyleMultipliers, rideStyle)
                * keyedMultiplier(activeConfig.stamina.behaviourMultipliers, behaviour)
                * speciesOrLabelMultiplier(activeConfig.stamina, labels, speciesId);

        return Math.clamp(multiplier, 0.01D, activeConfig.stamina.maxFinalMultiplier);
    }

    public double speedMultiplier(
            Collection<String> labels,
            String speciesId,
            String rideStyle,
            String behaviour
    ) {
        if (!activeConfig.enabled || !activeConfig.speed.enabled) {
            return 1.0D;
        }

        double multiplier = keyedMultiplier(activeConfig.speed.rideStyleMultipliers, rideStyle)
                * keyedMultiplier(activeConfig.speed.behaviourMultipliers, behaviour)
                * speciesOrLabelMultiplier(activeConfig.speed, labels, speciesId);

        return Math.clamp(multiplier, 0.01D, activeConfig.speed.maxFinalMultiplier);
    }

    public float scaleDrain(
            float originalDrain,
            int level,
            Collection<String> labels,
            String speciesId,
            String rideStyle,
            String behaviour
    ) {
        return (float) (originalDrain / enduranceMultiplier(level, labels, speciesId, rideStyle, behaviour));
    }

    private double staminaLevelMultiplier(int level) {
        if (!activeConfig.stamina.levelScalingEnabled) {
            return 1.0D;
        }

        RidingTweaksConfig.LevelScaling scaling = activeConfig.stamina.levelScaling;
        if (scaling.minLevel == scaling.maxLevel) {
            return level >= scaling.maxLevel ? scaling.maxMultiplier : scaling.minMultiplier;
        }

        int lowLevel = Math.min(scaling.minLevel, scaling.maxLevel);
        int highLevel = Math.max(scaling.minLevel, scaling.maxLevel);
        double lowMultiplier = scaling.minLevel <= scaling.maxLevel ? scaling.minMultiplier : scaling.maxMultiplier;
        double highMultiplier = scaling.minLevel <= scaling.maxLevel ? scaling.maxMultiplier : scaling.minMultiplier;
        double progress = Math.clamp((level - lowLevel) / (double) (highLevel - lowLevel), 0.0D, 1.0D);
        return lowMultiplier + progress * (highMultiplier - lowMultiplier);
    }

    private double speciesOrLabelMultiplier(
            RidingTweaksConfig.FeatureTweaks feature,
            Collection<String> labels,
            String speciesId
    ) {
        Double speciesMultiplier = speciesMultiplier(feature, speciesId);
        if (speciesMultiplier != null) {
            return speciesMultiplier;
        }

        double labelMultiplier = feature.defaultLabelMultiplier;
        if (labels != null) {
            for (String label : labels) {
                labelMultiplier = Math.max(labelMultiplier, keyedMultiplier(feature.labelMultipliers, label));
            }
        }
        return labelMultiplier;
    }

    private Double speciesMultiplier(RidingTweaksConfig.FeatureTweaks feature, String speciesId) {
        if (speciesId == null || speciesId.isBlank()) {
            return null;
        }

        String normalized = RidingTweaksConfig.normalizeKey(speciesId);
        Double exact = feature.speciesOverrides.get(normalized);
        if (exact != null) {
            return exact;
        }

        int namespaceSeparator = normalized.indexOf(':');
        if (namespaceSeparator >= 0 && namespaceSeparator + 1 < normalized.length()) {
            return feature.speciesOverrides.get(normalized.substring(namespaceSeparator + 1));
        }
        return null;
    }

    private static double keyedMultiplier(Map<String, Double> multipliers, String key) {
        if (key == null || key.isBlank()) {
            return 1.0D;
        }
        String normalized = RidingTweaksConfig.normalizeKey(key);
        Double exact = multipliers.get(normalized);
        if (exact != null) {
            return exact;
        }

        int pathSeparator = Math.max(normalized.lastIndexOf('/'), normalized.lastIndexOf(':'));
        if (pathSeparator >= 0 && pathSeparator + 1 < normalized.length()) {
            return multipliers.getOrDefault(normalized.substring(pathSeparator + 1), 1.0D);
        }
        return 1.0D;
    }

    private static RidingTweaksConfig read(Path path) {
        if (Files.notExists(path)) {
            return new RidingTweaksConfig();
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            RidingTweaksConfig config = GSON.fromJson(reader, RidingTweaksConfig.class);
            return config == null ? new RidingTweaksConfig() : config;
        } catch (IOException | JsonSyntaxException exception) {
            LOGGER.error("Failed to read {} config from {}; using defaults", CobblemonRidingTweaks.MOD_NAME, path, exception);
            return new RidingTweaksConfig();
        }
    }

    private static RidingTweaksConfig fromJson(String json) {
        try {
            String configVersion = readConfigVersion(json);
            if (isNewerVersion(configVersion, RidingTweaksConfig.SUPPORTED_CONFIG_VERSION)) {
                LOGGER.error(
                        "Synced {} config version {} is newer than this client supports ({}); using vanilla behaviour",
                        CobblemonRidingTweaks.MOD_NAME,
                        configVersion,
                        RidingTweaksConfig.SUPPORTED_CONFIG_VERSION
                );
                return vanillaConfig();
            }

            RidingTweaksConfig config = GSON.fromJson(json, RidingTweaksConfig.class);
            return config == null ? new RidingTweaksConfig() : config;
        } catch (JsonSyntaxException exception) {
            LOGGER.error("Failed to parse synced {} config; using vanilla behaviour", CobblemonRidingTweaks.MOD_NAME, exception);
            return vanillaConfig();
        }
    }

    private static RidingTweaksConfig submittedConfigFromJson(String json) {
        try {
            String configVersion = readConfigVersion(json);
            if (isNewerVersion(configVersion, RidingTweaksConfig.SUPPORTED_CONFIG_VERSION)) {
                LOGGER.warn(
                        "Rejected submitted {} config version {}; this server supports up to {}",
                        CobblemonRidingTweaks.MOD_NAME,
                        configVersion,
                        RidingTweaksConfig.SUPPORTED_CONFIG_VERSION
                );
                return null;
            }

            RidingTweaksConfig config = GSON.fromJson(json, RidingTweaksConfig.class);
            return config == null ? null : config;
        } catch (JsonSyntaxException exception) {
            LOGGER.warn("Rejected malformed submitted {} config", CobblemonRidingTweaks.MOD_NAME, exception);
            return null;
        }
    }

    private static String readConfigVersion(String json) {
        JsonElement element = JsonParser.parseString(json);
        if (!element.isJsonObject()) {
            return RidingTweaksConfig.SUPPORTED_CONFIG_VERSION;
        }

        JsonObject object = element.getAsJsonObject();
        JsonElement version = object.get("configVersion");
        if (version == null || version.isJsonNull()) {
            return RidingTweaksConfig.SUPPORTED_CONFIG_VERSION;
        }
        return version.getAsString();
    }

    private static boolean isNewerVersion(String candidate, String supported) {
        int[] candidateParts = parseVersion(candidate);
        int[] supportedParts = parseVersion(supported);
        for (int i = 0; i < 3; i++) {
            if (candidateParts[i] > supportedParts[i]) {
                return true;
            }
            if (candidateParts[i] < supportedParts[i]) {
                return false;
            }
        }
        return false;
    }

    private static int[] parseVersion(String version) {
        int[] parts = new int[] { 0, 0, 0 };
        if (version == null || version.isBlank()) {
            return parts;
        }

        String[] rawParts = version.split("\\.");
        for (int i = 0; i < Math.min(parts.length, rawParts.length); i++) {
            try {
                parts[i] = Math.max(0, Integer.parseInt(rawParts[i]));
            } catch (NumberFormatException exception) {
                LOGGER.warn("Invalid {} config version '{}'; treating it as 0.0.0", CobblemonRidingTweaks.MOD_NAME, version);
                return new int[] { 0, 0, 0 };
            }
        }
        return parts;
    }

    private static RidingTweaksConfig vanillaConfig() {
        RidingTweaksConfig config = new RidingTweaksConfig();
        config.enabled = false;
        return config.sanitize();
    }

    private static void warnForInvalidNumbers(RidingTweaksConfig config) {
        if (config == null) {
            return;
        }
        warnForInvalidNumbers("stamina", config.stamina);
        warnForInvalidNumbers("speed", config.speed);
        if (config.stamina != null) {
            RidingTweaksConfig.LevelScaling scaling = config.stamina.levelScaling;
            if (scaling != null) {
                warnIfInvalid("stamina.levelScaling.minMultiplier", scaling.minMultiplier);
                warnIfInvalid("stamina.levelScaling.maxMultiplier", scaling.maxMultiplier);
            }
        }
    }

    private static void warnForInvalidNumbers(String section, RidingTweaksConfig.FeatureTweaks feature) {
        if (feature == null) {
            return;
        }
        warnIfInvalid(section + ".defaultLabelMultiplier", feature.defaultLabelMultiplier);
        warnIfInvalid(section + ".maxFinalMultiplier", feature.maxFinalMultiplier);
        warnForInvalidNumbers(section + ".rideStyleMultipliers", feature.rideStyleMultipliers);
        warnForInvalidNumbers(section + ".behaviourMultipliers", feature.behaviourMultipliers);
        warnForInvalidNumbers(section + ".labelMultipliers", feature.labelMultipliers);
        warnForInvalidNumbers(section + ".speciesOverrides", feature.speciesOverrides);
    }

    private static void warnForInvalidNumbers(String section, Map<String, Double> values) {
        if (values == null) {
            return;
        }
        values.forEach((key, value) -> {
            if (value == null || !Double.isFinite(value)) {
                LOGGER.warn(
                        "Ignoring invalid {} value for key '{}': {}. Falling back to 1.0.",
                        section,
                        key,
                        value
                );
            }
        });
    }

    private static void warnIfInvalid(String path, double value) {
        if (!Double.isFinite(value)) {
            LOGGER.warn("Ignoring invalid {} value: {}. Falling back to a safe default.", path, value);
        }
    }

    private static void logDebugNotes(RidingTweaksConfig config) {
        if (config == null || !config.debugLogging) {
            return;
        }
        logCustomLabels("stamina.labelMultipliers", config.stamina == null ? null : config.stamina.labelMultipliers);
        logCustomLabels("speed.labelMultipliers", config.speed == null ? null : config.speed.labelMultipliers);
    }

    private static void logCustomLabels(String section, Map<String, Double> labels) {
        if (labels == null) {
            return;
        }
        labels.keySet().stream()
                .filter(label -> !RidingTweaksConfig.isKnownCobblemonLabel(label))
                .forEach(label -> LOGGER.debug(
                        "{} contains custom or unrecognized label '{}'. It will apply if a Pokemon has that exact label.",
                        section,
                        label
                ));
    }
}
