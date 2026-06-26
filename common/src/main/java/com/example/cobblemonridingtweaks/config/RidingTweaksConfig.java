package com.example.cobblemonridingtweaks.config;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class RidingTweaksConfig {
    public static final String SUPPORTED_CONFIG_VERSION = "1.0.0";

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
        defaults.put("bird", 1.0D);
        defaults.put("boat", 1.0D);
        defaults.put("burst", 1.0D);
        defaults.put("composite", 1.0D);
        defaults.put("dolphin", 1.0D);
        defaults.put("glider", 1.0D);
        defaults.put("helicopter", 1.0D);
        defaults.put("horse", 1.0D);
        defaults.put("hover", 1.0D);
        defaults.put("jet", 1.0D);
        defaults.put("minekart", 1.0D);
        defaults.put("rocket", 1.0D);
        defaults.put("submarine", 1.0D);
        defaults.put("vehicle", 1.0D);
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

    private static Set<String> knownCobblemonLabels() {
        return Set.of(
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
        public boolean levelScalingEnabled = true;
        public LevelScaling levelScaling = new LevelScaling();

        public StaminaTweaks() {
            rideStyleMultipliers = allRideStyleMultipliers();
            behaviourMultipliers = allBehaviourMultipliers();
            labelMultipliers = defaultLabelMultipliers();
            maxFinalMultiplier = 25.0D;
        }

        private void sanitize() {
            super.sanitize(true);
            if (levelScaling == null) {
                levelScaling = new LevelScaling();
            }
            levelScaling.sanitize();
        }
    }

    public static final class SpeedTweaks extends FeatureTweaks {
        public SpeedTweaks() {
            enabled = false;
            rideStyleMultipliers = emptyMultipliers();
            behaviourMultipliers = emptyMultipliers();
            labelMultipliers = emptyMultipliers();
            maxFinalMultiplier = 5.0D;
        }

        private void sanitize() {
            super.sanitize(false);
        }
    }

    public static class FeatureTweaks {
        public boolean enabled = true;
        public Map<String, Double> rideStyleMultipliers = emptyMultipliers();
        public Map<String, Double> behaviourMultipliers = emptyMultipliers();
        public Map<String, Double> labelMultipliers = emptyMultipliers();
        public Map<String, Double> speciesOverrides = emptyMultipliers();
        public double defaultLabelMultiplier = 1.0D;
        public double maxFinalMultiplier = 10.0D;

        private void sanitize(boolean includeKnownKeys) {
            rideStyleMultipliers = sanitizeMultiplierMap(rideStyleMultipliers, allRideStyleMultipliers(), includeKnownKeys);
            behaviourMultipliers = sanitizeMultiplierMap(behaviourMultipliers, allBehaviourMultipliers(), includeKnownKeys);
            labelMultipliers = sanitizeMultiplierMap(labelMultipliers, defaultLabelMultipliers(), includeKnownKeys);
            speciesOverrides = sanitizeMultiplierMap(speciesOverrides, emptyMultipliers(), false);
            defaultLabelMultiplier = sanitizeMultiplier(defaultLabelMultiplier, 1.0D);
            maxFinalMultiplier = sanitizeMultiplier(maxFinalMultiplier, 10.0D);
        }
    }

    public static final class LevelScaling {
        public int minLevel = 1;
        public int maxLevel = 100;
        public double minMultiplier = 1.0D;
        public double maxMultiplier = 1.0D;

        private void sanitize() {
            minLevel = Math.max(1, minLevel);
            maxLevel = Math.max(1, maxLevel);
            minMultiplier = sanitizeMultiplier(minMultiplier, 1.0D);
            maxMultiplier = sanitizeMultiplier(maxMultiplier, 1.0D);
        }
    }
}
