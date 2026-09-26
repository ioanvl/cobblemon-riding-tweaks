# Cobblemon Riding Tweaks

Configurable stamina and speed multipliers for Cobblemon mounts.

Cobblemon Riding Tweaks is a Minecraft 1.21.1 mod for Cobblemon 1.7.3, 1.8.0, and 1.8.1. It lets servers and singleplayer worlds tune how long Pokemon can ride before running out of stamina, and how fast different mounts move, without editing Cobblemon data packs by hand.

## Features

- Stamina drain multipliers for Cobblemon riding.
- Speed multipliers for ridden movement.
- Separate settings for stamina and speed.
- Global stamina and speed multipliers for quick overall tuning.
- Level scaling between a level 1 multiplier and a level 100 multiplier, with linear extrapolation beyond level 100.
- Optional stat scaling for speed, and configurable stat-based stamina scaling.
- Stat scaling can use IVs, EVs, natures, mints, and hyper training.
- Ride style multipliers for land, liquid, and air.
- Behaviour multipliers for all Cobblemon riding behaviours, such as horse, bird, jet, boat, dolphin, submarine, and more.
- Label multipliers for Cobblemon form labels such as legendary, mythical, ultra beast, mega, primal, gmax, and any other labels.
- Species overrides for all forms or individual forms of a Pokemon, including datapack forms.
- Additive or multiplicative multiplier combining.
- Label behaviour modes: highest matching label or stacking labels.
- Species behaviour modes: override labels or stack with labels.
- Default and Balanced config presets for neutral `x1` values or curated riding progression.
- Config screen on Fabric through Mod Menu, and on NeoForge through the built-in Mods screen, with server editing available to admins.

## Multiplayer Behavior

In multiplayer, the server's settings are the ones that count.

If the server has this mod installed, compatible clients receive the server's riding config when they join. Players can view server settings in the config screen. Players with permission level 3 or higher can edit and save server settings from the server tab, and the server then syncs the updated config to connected compatible clients.

If the server does not have this mod, does not send a compatible config, or sends a config with a different config version, the client uses neutral `x1` riding values for that server. Local client settings do not change multiplayer riding values.

Clients without the mod are still able to join a server, but they keep normal Cobblemon riding behavior without this mod's tweaks.

Config versions must match exactly for multiplayer sync and editing, including the patch number. Mod and config versions are independent: a mod update can keep the same config version when its settings and riding mechanics remain compatible.

## Requirements

- Minecraft 1.21.1
- Java 21
- Cobblemon 1.7.3, 1.8.0, or 1.8.1
- Fabric Loader + Fabric API, or NeoForge
- Mod Menu is optional on Fabric, but recommended for in-game config editing

## Installation

Install the correct jar for your loader:

- Fabric: `cobblemon_riding_tweaks-fabric-1.21.1-1.2.0.jar`
- NeoForge: `cobblemon_riding_tweaks-neoforge-1.21.1-1.2.0.jar`

For multiplayer servers, install the mod on the server and on clients that should use the configured riding tweaks.

## Config

The config file is created at:

```text
config/cobblemon-riding-tweaks.json
```

Most values are multipliers. `1.0` means no change, values above `1.0` make stamina last longer or speed faster, and values below `1.0` make stamina drain faster or speed slower. The mod keeps multiplier values at or above `0.01` internally, so zero or negative entries do not break the math.

This development branch uses config version `1.3.0` for form-specific species overrides; the published mod `1.2.0` uses config `1.2.0`. Existing local/server files migrate automatically without resetting settings. Update the server and clients together when the config version changes: config `1.3.0` accepts only `1.3.0`, including the patch number. Incompatible clients can still join using neutral `x1` tweaks; this check does not enforce client versions or prevent modified clients.

Changes in the config screen are only written when you press **Save**.

Wider windows show a sidebar with General and grouped Stamina/Speed pages. **Behaviour** contains both ride styles and individual behaviours. Narrow windows and high GUI scales use compact navigation with a searchable section dropdown. Disabled sections stay accessible and appear dimmed; the summary shows **Off** when the mod or feature switch is off. The main Mod/Stamina/Speed switches use green **On** and red **Off** text. **Reload**, **Save** and **Done** share one row below the settings, with a single line for unsaved changes or save feedback.

### Species and forms

Each species row has searchable, scrollable **Species** and **Form** dropdowns. New rows start with **(Choose Pokémon)**. Search by Pokémon name or species ID; selecting one immediately updates the form choices. The species button displays its name and keeps its full ID in the tooltip and config. Choose **All forms** for the whole species, or a specific form. Add another row with the same species to tune a different form. Existing species overrides migrate to All forms.

When Cobblemon's species registry is empty (for example, on a fresh title screen), the pickers use the species/forms bundled with Cobblemon. Once a world, server or another mod has populated that registry, its current data takes precedence, including datapack additions. This mod does not load worlds or datapacks for the picker.

For example, `cobblemon:goodra` with **Standard (Kalos)** affects standard Goodra; **Hisuian Form** affects Hisuian Goodra. A matching specific-form entry takes precedence over that species' All forms entry. Only one species multiplier applies, then **Species Behaviour** determines whether it replaces label multipliers or combines with them. Other factors and final limits still apply as usual.

Form names use Cobblemon's translations when available. Unnamed base forms use Standard plus an unambiguous region, or just Standard; untranslated custom forms keep their registered names. Matching uses the registered form name, independently of display language. Unavailable saved forms remain visible and do not silently become All forms. Changing a row's species resets its selection to All forms. Duplicate species/form targets must be resolved before saving.

The file format within either `stamina` or `speed` is:

```json
"speciesOverrides": [
  { "species": "cobblemon:goodra", "form": "*", "multiplier": 1.0 },
  { "species": "cobblemon:goodra", "form": "normal", "multiplier": 1.5 }
]
```

`*` means All forms; `normal` is Cobblemon's standard form name. Fully qualified species IDs are recommended. Legacy short names still match as fallbacks; a full ID wins over a short name at the same form specificity. Cosmetics/aspects and held-item conditions are outside this feature's scope.

### Labels

Label rows use the same searchable dropdown. Choices include known labels and labels found in available species/form data. **Add Label** opens the picker directly; labels already used by another row are excluded. To add a custom label, type its ID and choose **Use custom label**. Changing a row's label preserves its multiplier. All edits remain drafts until Save.

### Presets

The **Default** and **Balanced** buttons sit together under Configuration in the General section. Default restores neutral `x1` riding multipliers. Balanced loads the progression settings below. Each button asks for confirmation, replaces the selected draft's riding settings (including custom overrides), and waits for **Save**. Debug Logging is preserved. Server presets require the same editing permission as other server settings. New configs remain neutral until you choose otherwise.

Balanced uses HP IVs/EVs for stamina and Speed IVs/EVs for movement. Factors multiply together, with only the strongest matching label bonus counting. Mints and hypertraining count, and Speed natures apply their usual `x0.9` / `x1.0` / `x1.1` factor. All ride styles and behaviours stay at `x1`; species overrides are empty.

| Balanced setting | Stamina | Speed |
| --- | --- | --- |
| Level 1 → 100 | 0.90 → 3.50 | 0.90 → 1.50 |
| EVs 0 → 252 | 0.90 → 1.35 | 0.95 → 1.20 |
| IVs 0 → 31 | 0.95 → 1.10 | 0.95 → 1.10 |
| Powerhouse | 1.15 | 1.05 |
| Ultra Beast | 1.20 | 1.10 |
| Legendary / Mythical | 1.25 | 1.15 |
| Restricted | 1.30 | 1.20 |
| Final minimum / maximum | 0.80 / 6.50 | 0.80 / 2.50 |

Other labels stay neutral. The preset changes stamina drain, not recovery or stamina capacity. Its values are a starting point for playtesting and remain editable after loading.

## Commands

```text
/cobblemonridingtweaks reload
```

Reloads the server config from disk and syncs it to compatible clients. Requires permission level 3.

## Notes

This mod tweaks Cobblemon's riding calculations through mixins. It is intentionally scoped to riding stamina and movement speed; it does not change Aprijuice, stamina serialization, Pokemon data, or Cobblemon's mount eligibility rules.

## Building

```sh
./gradlew build
```

Built jars are written under:

- `fabric/build/libs/`
- `neoforge/build/libs/`

The build also runs numerical riding regressions and applies the gameplay mixins to Cobblemon's dependency bytecode on both loaders. These checks do not replace in-game riding tests. See [test details](tests/README.md).

## License

Cobblemon Riding Tweaks is licensed under the Mozilla Public License 2.0.
