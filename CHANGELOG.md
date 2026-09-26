# Changelog

## 1.3.0

- Fix a NeoForge dedicated-server startup crash caused by client-only stamina hooks loading on the server.
- Add stamina and speed overrides for individual Pokémon forms. Existing species overrides continue to apply to All forms.
- Refresh the config screen UI with sidebar navigation and searchable species, form and label dropdowns.

Config version is now `1.3.0`. Existing config files migrate automatically without losing settings. Update servers and clients together: multiplayer sync/editing requires matching config versions. Incompatible clients fall back to neutral `x1` tweaks.

Targets Minecraft 1.21.1 with Cobblemon 1.7.3, 1.8.0, and 1.8.1 on Fabric and NeoForge.

## 1.2.0

- Add a Balanced config preset with level, training and rarity bonuses, beside the neutral Default preset (formerly Clear). Both replace draft riding settings after confirmation and wait for Save; existing configs keep their settings.
- Fix Rocket-style movement repeatedly multiplying existing velocity at non-neutral speed settings.
- Fix stamina failing to reach zero or triggering exhaustion at the wrong time when endurance multipliers are applied.
- Fix stamina multipliers not applying to Rocket's regular drain and boost-start charge.
- Fix Bird-style movement gaining repeated speed boosts when glancing against walls or ceilings.
- Require exact config version matches for multiplayer sync and editing, including patch versions. Mod updates can retain the existing config version when settings and mechanics remain compatible.

Config version is now `1.2.0`. Existing config files migrate without losing settings, but multiplayer sync/editing requires both sides to use config `1.2.0`. Update servers and clients together. Incompatible clients still fall back to neutral `x1` tweaks; they are not disconnected.

Targets Minecraft 1.21.1 with Cobblemon 1.7.3, 1.8.0, and 1.8.1.
