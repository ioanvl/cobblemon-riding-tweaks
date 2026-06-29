# Cobblemon Riding Tweaks

Configurable stamina and speed multipliers for Cobblemon mounts.

Cobblemon Riding Tweaks is a Minecraft 1.21.1 mod for Cobblemon 1.7.3. It lets servers and singleplayer worlds tune how long Pokemon can ride before running out of stamina, and how fast different mounts move, without editing Cobblemon data packs by hand.

## Features

- Stamina drain multipliers for Cobblemon riding.
- Speed multipliers for ridden movement.
- Separate settings for stamina and speed.
- Level scaling between a level 1 multiplier and a level 100 multiplier, with linear extrapolation beyond level 100.
- Ride style multipliers for land, liquid, and air.
- Behaviour multipliers for all Cobblemon riding behaviours, such as horse, bird, jet, boat, dolphin, submarine, and more.
- Label multipliers for Cobblemon form labels such as legendary, mythical, ultra beast, mega, primal, gmax, and any other labels.
- Species overrides for specific Pokemon species IDs.
- Additive or multiplicative multiplier combining.
- Label behaviour modes: highest matching label or stacking labels.
- Species behaviour modes: override labels or stack with labels.
- Config screen on Fabric through Mod Menu, and on NeoForge through the built-in Mods screen, with server editing available to admins.

## Multiplayer Behavior

In multiplayer, the server's settings are the ones that count.

If the server has this mod installed, compatible clients receive the server's riding config when they join. Players can view server settings in the config screen. Players with permission level 3 or higher can edit and save server settings from the server tab, and the server then syncs the updated config to connected compatible clients.

If the server does not have this mod, does not send a compatible config, or sends a config with an incompatible config major/minor version, the client uses neutral `x1` riding values for that server. Local client settings do not change multiplayer riding values.

Clients without the mod are still able to join a server, but they keep normal Cobblemon riding behavior without this mod's tweaks.

Patch-level config version differences are allowed for safe default/list changes. Major/minor config differences are treated as incompatible because they may change riding mechanics.

## Requirements

- Minecraft 1.21.1
- Java 21
- Cobblemon 1.7.3
- Fabric Loader + Fabric API, or NeoForge
- Mod Menu is optional on Fabric, but recommended for in-game config editing

## Installation

Install the correct jar for your loader:

- Fabric: `cobblemon_riding_tweaks-fabric-1.21.1-1.0.0.jar`
- NeoForge: `cobblemon_riding_tweaks-neoforge-1.21.1-1.0.0.jar`

For multiplayer servers, install the mod on the server and on clients that should use the configured riding tweaks.

## Config

The config file is created at:

```text
config/cobblemon-riding-tweaks.json
```

Most values are multipliers. `1.0` means no change, values above `1.0` make stamina last longer or speed faster, and values below `1.0` make stamina drain faster or speed slower. The mod keeps multiplier values at or above `0.01` internally, so zero or negative entries do not break the math.

The config screen edits a draft. Changes are only written when you press **Save**.

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

## License

Cobblemon Riding Tweaks is licensed under the Mozilla Public License 2.0.
