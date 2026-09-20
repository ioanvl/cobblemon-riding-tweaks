package com.example.cobblemonridingtweaks.config;

import com.google.gson.Gson;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/** Exercises the file migration and public sync/edit APIs without a Minecraft server. */
public final class ConfigCompatibilityTest {
    private static final Gson GSON = new Gson();

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("riding-config-compatibility-");
        try {
            localSettingsMigrate(directory);
            syncedVersionsAreChecked(directory);
            submittedVersionsAreChecked(directory);
            System.out.println("Config compatibility passed: settings-preserving migration, exact version matching, "
                    + "patch rejection, neutral fallback and edit rejection.");
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
    }

    private static void localSettingsMigrate(Path directory) throws Exception {
        for (String oldVersion : List.of("1.1.0", "1.1.1", "1.1.99")) {
            RidingTweaksConfig original = fixture(oldVersion);
            Path file = directory.resolve("cobblemon-riding-tweaks.json");
            Files.writeString(file, GSON.toJson(original));
            RidingTweaksConfigManager manager = RidingTweaksConfigManager.load(directory);
            check(manager.config().configVersion.equals("1.2.0"), "Local config must migrate to 1.2.0");
            original.configVersion = "1.2.0";
            check(GSON.toJsonTree(original).equals(JsonParser.parseString(Files.readString(file))),
                    "Migration must preserve every setting from " + oldVersion);
            manager.reload();
            check(GSON.toJsonTree(original).equals(JsonParser.parseString(manager.localConfigJson())),
                    "Reload must preserve migrated settings");
        }
    }

    private static void syncedVersionsAreChecked(Path directory) {
        RidingTweaksConfigManager manager = RidingTweaksConfigManager.load(directory);
        String localBefore = manager.localConfigJson();
        manager.awaitServerConfig();
        checkNeutral(manager);
        for (String incompatible : List.of("1.1.0", "1.1.99", "1.2.1", "1.2.99", "1.3.0", "2.2.0",
                "1.2", "1.2.-1", "1.2.0.1", "01.2.0", "1.2.0-beta", "")) {
            var result = manager.applyServerConfigWithResult(GSON.toJson(fixture("1.2.0")), true);
            check(!result.versionMismatch(), "Identical config versions must be compatible");
            check(manager.canEditServerConfig(), "Compatible admin config must remain editable");
            check(speed(manager) == 0.75, "Compatible server speed must apply");
            check(endurance(manager) == 2.5, "Compatible server endurance must apply");

            result = manager.applyServerConfigWithResult(GSON.toJson(fixture(incompatible)), true);
            check(result.versionMismatch(), "Must reject incompatible server config: " + incompatible);
            check(result.serverConfigVersion().equals(incompatible), "Warning must identify received version");
            check(result.supportedConfigVersion().equals("1.2.0"), "Warning must identify supported version");
            checkNeutral(manager);
            check(!manager.canEditServerConfig(), "Incompatible server editing must be disabled");
        }
        manager.applyServerConfigWithResult(GSON.toJson(fixture("1.2.0")), false);
        check(!manager.canEditServerConfig(), "Version compatibility must not grant editing permission");
        check(manager.localConfigJson().equals(localBefore), "Server sync must not replace local settings");
        manager.clearServerConfig();
        check(speed(manager) == 0.75 && endurance(manager) == 2.5, "Disconnect must restore local settings");
    }

    private static void submittedVersionsAreChecked(Path directory) throws Exception {
        RidingTweaksConfigManager manager = RidingTweaksConfigManager.load(directory);
        String before = Files.readString(manager.path());
        for (String incompatible : List.of("1.1.0", "1.1.99", "1.2.1", "1.2.99", "1.3.0", "2.2.0",
                "1.2", "1.2.-1", "1.2.0.1", "01.2.0", "1.2.0-beta", "")) {
            check(!manager.replaceFromRemoteJson(GSON.toJson(fixture(incompatible))),
                    "Incompatible server edit must be rejected: " + incompatible);
            check(Files.readString(manager.path()).equals(before), "Rejected edit must not change disk settings");
        }
        RidingTweaksConfig compatible = fixture("1.2.0");
        compatible.speed.globalMultiplier = 1.25;
        check(manager.replaceFromRemoteJson(GSON.toJson(compatible)), "An exact version match must be accepted");
        check(speed(manager) == 1.25, "Accepted edit must take effect");
        check(GSON.toJsonTree(compatible).equals(JsonParser.parseString(Files.readString(manager.path()))),
                "Accepted edit must persist");
    }

    private static RidingTweaksConfig fixture(String version) {
        RidingTweaksConfig config = new RidingTweaksConfig();
        config.configVersion = version;
        config.stamina.globalMultiplier = 2.5;
        config.speed.globalMultiplier = 0.75;
        config.stamina.statScaling.stat = RidingTweaksConfig.STAT_SPEED;
        config.stamina.speciesOverrides.put("cobblemon:pidgeot", 3.0);
        config.speed.behaviourMultipliers.put("bird", 0.5);
        config.sanitize();
        // Keep malformed wire versions intact instead of sanitizing them into the supported version.
        config.configVersion = version;
        return config;
    }

    private static double speed(RidingTweaksConfigManager manager) {
        return manager.speedMultiplier(50, List.of(), "cobblemon:bouffalant", "land", "horse",
                0, 0, 0, false, false, false, false);
    }

    private static double endurance(RidingTweaksConfigManager manager) {
        return manager.enduranceMultiplier(50, List.of(), "cobblemon:bouffalant", "land", "horse",
                0, 0, 0, false, false, false, false);
    }

    private static void checkNeutral(RidingTweaksConfigManager manager) {
        check(speed(manager) == 1.0 && endurance(manager) == 1.0, "Incompatible/unsynced gameplay must be neutral");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
