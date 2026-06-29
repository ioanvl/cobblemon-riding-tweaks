package com.example.cobblemonridingtweaks.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RidingTweaksConfig {
    public static final String SUPPORTED_CONFIG_VERSION = "1.0.0";
    public static final String STACKING_MODE_ADDITIVE = "additive";
    public static final String STACKING_MODE_MULTIPLICATIVE = "multiplicative";
    public static final String LABEL_MODE_HIGHEST = "highest";
    public static final String LABEL_MODE_STACKING = "stacking";
    public static final String SPECIES_MODE_OVERRIDE = "override";
    public static final String SPECIES_MODE_STACKING = "stacking";
    private static final String STACKING_MODE_STACKING_ALIAS = "stacking";
    private static final String SPECIES_MODE_REPLACE_ALIAS = "replace";

    public String configVersion = SUPPORTED_CONFIG_VERSION;
    public boolean enabled = true;
    public boolean debugLogging = false;
    public StaminaTweaks stamina = new StaminaTweaks();
    public SpeedTweaks speed = new SpeedTweaks();

    public RidingTweaksConfig sanitize() {
        if (configVersion == null || configVersion.isBlank()) {
            configVersion = SUPPORTED_CONFIG_VERSION;
        }
        if (stamina == null) {
            stamina = new StaminaTweaks();
        }
        if (speed == null) {
            speed = new SpeedTweaks();
        }
        stamina.sanitize();
        speed.sanitize();
        return this;
    }

    static double sanitizeMultiplier(Double value, double fallback) {
        if (value == null || !Double.isFinite(value)) {
            return fallback;
        }
        return Math.max(0.01D, value);
    }

    static String normalizeKey(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String sanitizeStackingMode(String value) {
        if (value == null) {
            return STACKING_MODE_ADDITIVE;
        }

        String normalized = normalizeKey(value);
        return STACKING_MODE_MULTIPLICATIVE.equals(normalized) || STACKING_MODE_STACKING_ALIAS.equals(normalized)
                ? STACKING_MODE_MULTIPLICATIVE
                : STACKING_MODE_ADDITIVE;
    }

    private static String sanitizeLabelMode(String value) {
        if (value == null) {
            return LABEL_MODE_HIGHEST;
        }

        return LABEL_MODE_STACKING.equals(normalizeKey(value)) ? LABEL_MODE_STACKING : LABEL_MODE_HIGHEST;
    }

    private static String sanitizeSpeciesMode(String value) {
        if (value == null) {
            return SPECIES_MODE_OVERRIDE;
        }

        String normalized = normalizeKey(value);
        if (SPECIES_MODE_STACKING.equals(normalized)) {
            return SPECIES_MODE_STACKING;
        }
        if (SPECIES_MODE_OVERRIDE.equals(normalized) || SPECIES_MODE_REPLACE_ALIAS.equals(normalized)) {
            return SPECIES_MODE_OVERRIDE;
        }
        return SPECIES_MODE_OVERRIDE;
    }

    private static Map<String, Double> sanitizeMultiplierMap(
            Map<String, Double> map,
            Map<String, Double> fallback,
            boolean includeFallbackKeys
    ) {
        Map<String, Double> source = map == null ? (includeFallbackKeys ? fallback : emptyMultipliers()) : map;
        Map<String, Double> sanitized = new LinkedHashMap<>();
        if (includeFallbackKeys) {
            fallback.forEach((key, value) -> sanitized.put(normalizeKey(key), sanitizeMultiplier(value, 1.0D)));
        }
        source.forEach((key, value) -> {
            if (key != null && !key.isBlank()) {
                sanitized.put(normalizeKey(key), sanitizeMultiplier(value, 1.0D));
            }
        });
        return sanitized;
    }

    private static Map<String, Double> emptyMultipliers() {
        return new LinkedHashMap<>();
    }

    private static Map<String, Double> allRideStyleMultipliers() {
        Map<String, Double> defaults = new LinkedHashMap<>();
        defaults.put("land", 1.0D);
        defaults.put("liquid", 1.0D);
        defaults.put("air", 1.0D);
        return defaults;
    }

    private static Map<String, Double> allBehaviourMultipliers() {
        Map<String, Double> defaults = new LinkedHashMap<>();
        defaults.put("horse", 1.0D);
        defaults.put("minekart", 1.0D);
        defaults.put("vehicle", 1.0D);
        defaults.put("bird", 1.0D);
        defaults.put("glider", 1.0D);
        defaults.put("helicopter", 1.0D);
        defaults.put("hover", 1.0D);
        defaults.put("jet", 1.0D);
        defaults.put("rocket", 1.0D);
        defaults.put("boat", 1.0D);
        defaults.put("burst", 1.0D);
        defaults.put("dolphin", 1.0D);
        defaults.put("submarine", 1.0D);
        return defaults;
    }

    private static Map<String, Double> defaultLabelMultipliers() {
        Map<String, Double> defaults = new LinkedHashMap<>();
        defaults.put("legendary", 1.0D);
        defaults.put("restricted", 1.0D);
        defaults.put("mythical", 1.0D);
        defaults.put("ultra_beast", 1.0D);
        defaults.put("powerhouse", 1.0D);
        defaults.put("mega", 1.0D);
        defaults.put("primal", 1.0D);
        defaults.put("gmax", 1.0D);
        return defaults;
    }

    public static boolean isKnownCobblemonLabel(String label) {
        return label != null && knownCobblemonLabels().contains(normalizeKey(label));
    }

    public static List<String> knownCobblemonLabels() {
        return List.of(
                "legendary",
                "restricted",
                "mythical",
                "ultra_beast",
                "fossil",
                "powerhouse",
                "baby",
                "regional",
                "kantonian_form",
                "johtonian_form",
                "hoennian_form",
                "sinnohan_form",
                "unovan_form",
                "kalosian_form",
                "alolan_form",
                "galarian_form",
                "hisuian_form",
                "paldean_form",
                "mega",
                "primal",
                "gmax",
                "totem",
                "paradox",
                "gen1",
                "gen2",
                "gen3",
                "gen4",
                "gen5",
                "gen6",
                "gen7",
                "gen7b",
                "gen8",
                "gen8a",
                "gen9",
                "customized_official",
                "custom"
        );
    }

    public static final class StaminaTweaks extends FeatureTweaks {
        public StaminaTweaks() {
            rideStyleMultipliers = allRideStyleMultipliers();
            behaviourMultipliers = allBehaviourMultipliers();
            labelMultipliers = defaultLabelMultipliers();
            maxFinalMultiplier = 25.0D;
        }

        private void sanitize() {
            super.sanitize(true);
        }
    }

    public static final class SpeedTweaks extends FeatureTweaks {
        public SpeedTweaks() {
            rideStyleMultipliers = allRideStyleMultipliers();
            behaviourMultipliers = allBehaviourMultipliers();
            labelMultipliers = defaultLabelMultipliers();
            maxFinalMultiplier = 5.0D;
        }

        private void sanitize() {
            super.sanitize(true);
        }
    }

    public static class FeatureTweaks {
        public boolean enabled = true;
        public String stackingMode = STACKING_MODE_ADDITIVE;
        public boolean levelScalingEnabled = true;
        public boolean ridingMultipliersEnabled = true;
        public boolean labelMultipliersEnabled = true;
        public String labelMode = LABEL_MODE_HIGHEST;
        public boolean speciesOverridesEnabled = true;
        public String speciesMode = SPECIES_MODE_OVERRIDE;
        public double minFinalMultiplier = 0.01D;
        public double maxFinalMultiplier = 10.0D;
        public LevelScaling levelScaling = new LevelScaling();
        public Map<String, Double> rideStyleMultipliers = emptyMultipliers();
        public Map<String, Double> behaviourMultipliers = emptyMultipliers();
        public double defaultLabelMultiplier = 1.0D;
        public Map<String, Double> labelMultipliers = emptyMultipliers();
        public Map<String, Double> speciesOverrides = emptyMultipliers();

        private void sanitize(boolean includeKnownKeys) {
            stackingMode = sanitizeStackingMode(stackingMode);
            labelMode = sanitizeLabelMode(labelMode);
            speciesMode = sanitizeSpeciesMode(speciesMode);
            rideStyleMultipliers = sanitizeMultiplierMap(rideStyleMultipliers, allRideStyleMultipliers(), includeKnownKeys);
            behaviourMultipliers = sanitizeMultiplierMap(behaviourMultipliers, allBehaviourMultipliers(), includeKnownKeys);
            labelMultipliers = sanitizeMultiplierMap(labelMultipliers, defaultLabelMultipliers(), includeKnownKeys);
            speciesOverrides = sanitizeMultiplierMap(speciesOverrides, emptyMultipliers(), false);
            defaultLabelMultiplier = sanitizeMultiplier(defaultLabelMultiplier, 1.0D);
            minFinalMultiplier = sanitizeMultiplier(minFinalMultiplier, 0.01D);
            maxFinalMultiplier = Math.max(minFinalMultiplier, sanitizeMultiplier(maxFinalMultiplier, 10.0D));
            if (levelScaling == null) {
                levelScaling = new LevelScaling();
            }
            levelScaling.sanitize();
        }
    }

    public static final class LevelScaling {
        public double level1Multiplier = 1.0D;

        public double level100Multiplier = 1.0D;

        private void sanitize() {
            level1Multiplier = sanitizeMultiplier(level1Multiplier, 1.0D);
            level100Multiplier = sanitizeMultiplier(level100Multiplier, 1.0D);
        }
    }
}
