# Changelog

## 1.2.0

- Add a Balanced config preset with level, training and rarity bonuses, beside the neutral Default preset (formerly Clear). Both replace draft riding settings after confirmation and wait for Save; existing configs keep their settings.
- Fix Rocket-style movement repeatedly multiplying existing velocity at non-neutral speed settings.
- Fix stamina failing to reach zero or triggering exhaustion at the wrong time when endurance multipliers are applied.
- Fix stamina multipliers not applying to Rocket's regular drain and boost-start charge.
- Fix Bird-style movement gaining repeated speed boosts when glancing against walls or ceilings.
- Require exact config version matches for multiplayer sync and editing, including patch versions. Mod updates can retain the existing config version when settings and mechanics remain compatible.

Config version is now `1.2.0`. Existing config files migrate without losing settings, but multiplayer sync/editing requires both sides to use config `1.2.0`. Update servers and clients together. Incompatible clients still fall back to neutral `x1` tweaks; they are not disconnected.

Targets Minecraft 1.21.1 with Cobblemon 1.7.3, 1.8.0, and 1.8.1.
