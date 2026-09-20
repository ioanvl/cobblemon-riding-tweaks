package com.example.cobblemonridingtweaks.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RidingTweaksConfig {
    public static final String SUPPORTED_CONFIG_VERSION = "1.2.0";
    public static final String STACKING_MODE_ADDITIVE = "additive";
    public static final String STACKING_MODE_MULTIPLICATIVE = "multiplicative";
    public static final String LABEL_MODE_HIGHEST = "highest";
    public static final String LABEL_MODE_STACKING = "stacking";
    public static final String SPECIES_MODE_OVERRIDE = "override";
    public static final String SPECIES_MODE_STACKING = "stacking";
    public static final String IV_EV_MODE_OFF = "off";
    public static final String IV_EV_MODE_COMBINED = "combined";
    public static final String IV_EV_MODE_SEPARATE = "separate";
    public static final String STAT_HP = "hp";
    public static final String STAT_ATTACK = "attack";
    public static final String STAT_DEFENCE = "defence";
    public static final String STAT_SPECIAL_ATTACK = "special_attack";
    public static final String STAT_SPECIAL_DEFENCE = "special_defence";
    public static final String STAT_SPEED = "speed";
    private static final String STACKING_MODE_STACKING_ALIAS = "stacking";
    private static final String SPECIES_MODE_REPLACE_ALIAS = "replace";

    public String configVersion = SUPPORTED_CONFIG_VERSION;
    public boolean enabled = true;
    public boolean debugLogging = false;
    public StaminaTweaks stamina = new StaminaTweaks();
    public SpeedTweaks speed = new SpeedTweaks();

    /** Creates an independent preset for editing; fresh configs remain neutral by default. */
    public static RidingTweaksConfig balancedPreset() {
        RidingTweaksConfig config = new RidingTweaksConfig();
        for (FeatureTweaks feature : List.of(config.stamina, config.speed)) {
            feature.stackingMode = STACKING_MODE_MULTIPLICATIVE;
            feature.levelScaling.level1Multiplier = 0.9D;
            feature.minFinalMultiplier = 0.8D;
        }
        config.stamina.levelScaling.level100Multiplier = 3.5D;
        config.speed.levelScaling.level100Multiplier = 1.5D;
        config.stamina.maxFinalMultiplier = 6.5D;
        config.speed.maxFinalMultiplier = 2.5D;

        for (NatureIvEvStatScaling scaling : List.of(config.stamina.statScaling, config.speed.statScaling)) {
            scaling.ivEvScalingMode = IV_EV_MODE_SEPARATE;
            scaling.ivZeroMultiplier = 0.95D;
            scaling.ivMaxMultiplier = 1.1D;
        }
        config.stamina.statScaling.evZeroMultiplier = 0.9D;
        config.stamina.statScaling.evMaxMultiplier = 1.35D;
        config.speed.statScaling.evZeroMultiplier = 0.95D;
        config.speed.statScaling.evMaxMultiplier = 1.2D;
        config.speed.statScaling.natureScalingEnabled = true;

        config.stamina.labelMultipliers.putAll(Map.of(
                "powerhouse", 1.15D,
                "ultra_beast", 1.2D,
                "legendary", 1.25D,
                "mythical", 1.25D,
                "restricted", 1.3D
        ));
        config.speed.labelMultipliers.putAll(Map.of(
                "powerhouse", 1.05D,
                "ultra_beast", 1.1D,
                "legendary", 1.15D,
                "mythical", 1.15D,
                "restricted", 1.2D
        ));
        return config.sanitize();
    }

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

    private static String sanitizeIvEvMode(String value) {
        if (value == null) {
            return IV_EV_MODE_OFF;
        }

        String normalized = normalizeKey(value);
        if (IV_EV_MODE_COMBINED.equals(normalized) || IV_EV_MODE_SEPARATE.equals(normalized)) {
            return normalized;
        }
        return IV_EV_MODE_OFF;
    }

    public static String sanitizeStatKey(String value) {
        if (value == null) {
            return STAT_HP;
        }

        String normalized = normalizeKey(value);
        if (STAT_ATTACK.equals(normalized)
                || STAT_DEFENCE.equals(normalized)
                || STAT_SPECIAL_ATTACK.equals(normalized)
                || STAT_SPECIAL_DEFENCE.equals(normalized)
                || STAT_SPEED.equals(normalized)) {
            return normalized;
        }
        return STAT_HP;
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

    public static List<String> statKeys() {
        return List.of(
                STAT_HP,
                STAT_ATTACK,
                STAT_DEFENCE,
                STAT_SPECIAL_ATTACK,
                STAT_SPECIAL_DEFENCE,
                STAT_SPEED
        );
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
        public StaminaStatScaling statScaling = new StaminaStatScaling();

        public StaminaTweaks() {
            rideStyleMultipliers = allRideStyleMultipliers();
            behaviourMultipliers = allBehaviourMultipliers();
            labelMultipliers = defaultLabelMultipliers();
            maxFinalMultiplier = 25.0D;
        }

        private void sanitize() {
            super.sanitize(true);
            if (statScaling == null) {
                statScaling = new StaminaStatScaling();
            }
            statScaling.sanitize();
        }
    }

    public static final class SpeedTweaks extends FeatureTweaks {
        public SpeedStatScaling statScaling = new SpeedStatScaling();

        public SpeedTweaks() {
            rideStyleMultipliers = allRideStyleMultipliers();
            behaviourMultipliers = allBehaviourMultipliers();
            labelMultipliers = defaultLabelMultipliers();
            maxFinalMultiplier = 5.0D;
        }

        private void sanitize() {
            super.sanitize(true);
            if (statScaling == null) {
                statScaling = new SpeedStatScaling();
            }
            statScaling.sanitize();
        }
    }

    public static class FeatureTweaks {
        public boolean enabled = true;
        public String stackingMode = STACKING_MODE_ADDITIVE;
        public double globalMultiplier = 1.0D;
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
            globalMultiplier = sanitizeMultiplier(globalMultiplier, 1.0D);
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

    public static class IvEvStatScaling {
        public String ivEvScalingMode = IV_EV_MODE_OFF;
        public boolean allowHyperTraining = true;
        public double combinedZeroMultiplier = 1.0D;
        public double combinedMaxMultiplier = 1.0D;
        public double ivZeroMultiplier = 1.0D;
        public double ivMaxMultiplier = 1.0D;
        public double evZeroMultiplier = 1.0D;
        public double evMaxMultiplier = 1.0D;

        protected void sanitize() {
            ivEvScalingMode = sanitizeIvEvMode(ivEvScalingMode);
            combinedZeroMultiplier = sanitizeMultiplier(combinedZeroMultiplier, 1.0D);
            combinedMaxMultiplier = sanitizeMultiplier(combinedMaxMultiplier, 1.0D);
            ivZeroMultiplier = sanitizeMultiplier(ivZeroMultiplier, 1.0D);
            ivMaxMultiplier = sanitizeMultiplier(ivMaxMultiplier, 1.0D);
            evZeroMultiplier = sanitizeMultiplier(evZeroMultiplier, 1.0D);
            evMaxMultiplier = sanitizeMultiplier(evMaxMultiplier, 1.0D);
        }
    }

    public static class NatureIvEvStatScaling extends IvEvStatScaling {
        public boolean natureScalingEnabled = false;
        public boolean allowMints = true;
    }

    public static final class StaminaStatScaling extends NatureIvEvStatScaling {
        public String stat = STAT_HP;

        @Override
        protected void sanitize() {
            super.sanitize();
            stat = sanitizeStatKey(stat);
        }
    }

    public static final class SpeedStatScaling extends NatureIvEvStatScaling {
    }
}
