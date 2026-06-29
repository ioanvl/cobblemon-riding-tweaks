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
- In-game config screen on Fabric through Mod Menu, and on NeoForge through the built-in Mods screen, with server editing available to admins.

## Multiplayer

On multiplayer servers, the server's settings are used.

If the server does not have the mod, or if the client and server config versions are not compatible, the client uses neutral `x1` riding values for that server.

Clients without the mod are still able to join a server, but they keep normal Cobblemon riding behavior without this mod's tweaks.

If the server has the mod installed, compatible clients receive the server's riding config when they join. Players can view the server settings in the config screen. Players with permission level 3 or higher can also edit and save the server settings from that screen.

## Config

Most values are multipliers. `1.0` means no change, values above `1.0` make stamina last longer or speed faster, and values below `1.0` make stamina drain faster or speed slower.

The config screen edits a draft. Changes are only written and applied when you press **Save**.

## Command

```text
/cobblemonridingtweaks reload
```

Reloads the server config from disk and applies it to compatible clients. Requires permission level 3.

## Requirements

- Minecraft 1.21.1
- Java 21
- Cobblemon 1.7.3
- Fabric Loader + Fabric API, or NeoForge
- Mod Menu is optional on Fabric, but recommended for in-game config editing
