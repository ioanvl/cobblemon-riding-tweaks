# Riding verification

Run `./gradlew build` to compile/package both loaders and run:

- `:common:ridingRegressionTest`: numerical regression scenarios using the production math helpers. Covers finite stamina exhaustion, immediate exhaustion checks, Rocket drain/boost charges, unchanged recovery, Rocket coasting/acceleration, and repeated Bird wall/ceiling contact at multipliers above and below one, including fast diving and full stops.
- `:common:configCompatibilityTest`: exercises config file migration, preserving all settings, and the public sync/edit APIs. Confirms that exact version matches accept settings, all version differences (including patches) reject settings, incompatible sync clears previously active server multipliers and disables editing, and rejected edits leave the file untouched. It also rejects malformed and noncanonical versions. Temporary test config files are removed afterward; test logging stays under `common/build/config-compatibility-test/`.
- `:common:configPresetTest`: checks Balanced's calculated progression, rarity bonuses, mints/hypertraining, lower/upper clamps, independent preset copies, save/reload, multiplayer isolation, and restoration of neutral defaults. Temporary config files are removed afterward; logging stays under `common/build/config-preset-test/`. It exercises config APIs, not screen interaction.
- `:fabric:ridingMixinTest` and `:neoforge:ridingMixinTest`: apply the actual compiled mixins to all nine targeted Cobblemon classes and check the number of injected calls. The harness supplies Mixin with the existing Gradle classpath; it adds no runtime dependencies and does not start Minecraft.

The Mixin checks use the development mappings. They validate injection signatures, target matching, and argument coercion, but do not test production refmap remapping, loader startup, other mods' transformations, or gameplay. The numerical scenarios model relevant controller calculations; they do not execute full Cobblemon controllers.

To check another supported Cobblemon release without changing the minimum dependency:

```sh
./gradlew build -Pcobblemon_fabric_version_id=YgmyyFcs -Pcobblemon_neoforge_version_id=2oL01rSF
./gradlew build -Pcobblemon_fabric_version_id=gBW3vLC7 -Pcobblemon_neoforge_version_id=7otgw3aH
```

These are 1.8.0 and 1.8.1 respectively. Run the default build again to restore the release artifacts compiled against 1.7.3.

In-game checks before release:

1. On a Rocket-style flyer (such as Golurk), compare speed x1 and x2 while accelerating, coasting, boosting, and bumping a wall. Speed should remain bounded; a stopped collision axis should stay stopped. Change speed settings and switch riding modes while moving.
2. At endurance x0.5, x2, and x10, keep draining stamina until the bar reaches zero. Check land sprint, boat sprint, bird/jet flight, and submarine air protection. Exhaustion should happen at the scaled time, and normal recovery should be unchanged.
3. On a Rocket-style flyer, compare jump/boost drain and the initial boost charge at endurance x1 and x10.
4. Recheck the user-confirmed Bird collision boost: fly alongside a long wall and steer gently into it while sliding forward, comparing speed x0.5, x1 and x2. Repeat under a low ceiling, while powered and while gliding/diving. Repeated contact should no longer amplify the speed multiplier or introduce extra multiplier-based slowing below x1; ordinary collision slowdown still applies.
5. Upgrade an existing config to 1.2.0 and verify its settings survive. Test old client/new server and new client/old server combinations: synced settings must be rejected with neutral x1 fallback and a version warning. Matching 1.2.0 configurations should sync normally; even a patch mismatch such as 1.2.1 must be rejected. These checks do not disconnect clients or provide server enforcement of riding physics.
6. In General → Configuration, check the Default/Balanced buttons at normal and high GUI scales. Cancel each confirmation and check that the draft stays unchanged. Confirm Balanced, inspect the settings/summary, and check that riding settings change only after Save; closing without Save must discard the preset. Repeat with Default and verify neutral multipliers, cleared custom overrides and preserved Debug Logging. Check local/server draft separation, disabled buttons for non-admins, and server Save/resync for admins. Local presets must not override multiplayer server settings.

Repeat on Fabric and NeoForge, including a server with synchronized settings.
