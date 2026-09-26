package com.example.cobblemonridingtweaks.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/** Verifies the preset's gameplay outcomes and isolation through the public config APIs. */
public final class ConfigPresetTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("riding-config-presets-");
        try {
            RidingTweaksConfigManager manager = RidingTweaksConfigManager.load(directory);
            String defaultJson = manager.localConfigJson();
            String savedBefore = Files.readString(manager.path());

            RidingTweaksConfig editedPreset = RidingTweaksConfig.balancedPreset();
            editedPreset.stamina.labelMultipliers.put("restricted", 99.0D);
            editedPreset.speed.speciesOverrides.add(new RidingTweaksConfig.SpeciesOverride("cobblemon:pidgeot", "*", 0.01D));
            editedPreset.speed.statScaling.evMaxMultiplier = 0.01D;
            RidingTweaksConfig balanced = RidingTweaksConfig.balancedPreset();
            check(manager.localConfigJson().equals(defaultJson), "Creating/editing a preset must not change the active config");
            check(Files.readString(manager.path()).equals(savedBefore), "Creating/editing a preset must not write to disk");
            check(manager.toJson(new RidingTweaksConfig().sanitize()).equals(defaultJson),
                    "Creating/editing Balanced must not change fresh neutral defaults");

            manager.replaceAndSaveActiveLocal(balanced);
            manager.reload();
            check(manager.staminaStatKey().equals(RidingTweaksConfig.STAT_HP), "Balanced stamina must use HP training");
            expect(manager, 50, 16, 16, 0, List.of(), 0, 2.022148, 1.168300);
            expect(manager, 100, 31, 31, 252, List.of(), 0, 5.1975, 1.98);
            // Effective IVs and a Speed mint must count even when natural IVs/nature differ.
            expect(manager, 100, 0, 31, 252, List.of(), 1, 5.1975, 2.178);
            expect(manager, 100, 31, 31, 252, List.of("powerhouse"), 1, 5.977125, 2.2869);
            expect(manager, 100, 31, 31, 252, List.of("ultra_beast"), 1, 6.237, 2.3958);
            expect(manager, 100, 31, 31, 252, List.of("legendary"), 1, 6.496875, 2.5);
            expect(manager, 100, 31, 31, 252, List.of("mythical"), 1, 6.496875, 2.5);
            expect(manager, 100, 0, 31, 252, List.of("restricted", "legendary", "mega"), 1, 6.5, 2.5);
            // The lower floor includes bad stats/nature; higher custom level caps stay bounded too.
            expect(manager, 1, 0, 0, 0, List.of(), -1, 0.8, 0.8);
            expect(manager, 200, 31, 31, 252, List.of("restricted"), 1, 6.5, 2.5);

            String balancedJson = manager.localConfigJson();
            manager.awaitServerConfig();
            manager.replaceAndSave(RidingTweaksConfig.balancedPreset());
            expect(manager, 100, 31, 31, 252, List.of("restricted"), 1, 1.0, 1.0);
            var result = manager.applyServerConfigWithResult(balancedJson, false);
            check(!result.versionMismatch(), "Balanced must use the current config contract");
            check(!manager.canEditServerConfig(), "Using Balanced must not grant server editing permission");
            expect(manager, 100, 31, 31, 252, List.of("restricted"), 1, 6.5, 2.5);

            manager.replaceAndSaveActiveLocal(new RidingTweaksConfig().sanitize());
            manager.reload();
            check(manager.localConfigJson().equals(defaultJson), "Default must fully restore neutral riding settings");
            for (int level : List.of(1, 50, 100, 200)) {
                expect(manager, level, 0, 31, 252, List.of("restricted", "legendary", "mega"), 1, 1.0, 1.0);
            }
            System.out.println("Config presets passed: balanced progression, labels, mints/hypertraining, clamps, "
                    + "independent drafts, save/reload, multiplayer isolation and neutral defaults.");
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
    }

    private static void expect(RidingTweaksConfigManager manager, int level, int naturalIv, int effectiveIv,
                               int ev, List<String> labels, int effectiveNature, double endurance, double speed) {
        double actualEndurance = manager.enduranceMultiplier(level, labels, "cobblemon:pidgeot", "normal", "air", "bird",
                naturalIv, effectiveIv, ev, false, true, effectiveNature > 0, effectiveNature < 0);
        double actualSpeed = manager.speedMultiplier(level, labels, "cobblemon:pidgeot", "normal", "air", "bird",
                naturalIv, effectiveIv, ev, false, true, effectiveNature > 0, effectiveNature < 0);
        check(Math.abs(actualEndurance - endurance) < 0.000001D,
                "Expected endurance " + endurance + ", got " + actualEndurance + " at level " + level + " with " + labels);
        check(Math.abs(actualSpeed - speed) < 0.000001D,
                "Expected speed " + speed + ", got " + actualSpeed + " at level " + level + " with " + labels);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
