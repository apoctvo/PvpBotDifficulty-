# PvpBotDifficulty 1.2.6

Wrapper for PvpBot 3.0.0. Main command: `/bott`.

## Tier balancing (1.2.6)
Each tier now applies its own per-bot combat and utility profile, not just a native PvpBot preset:

- LT3: slow reactions, low aim/technique, frequent mistakes, long utility cooldowns, slower totem/web responses, and weaker movement.
- LT2 / LT1: gradual improvement in reactions, accuracy, utility timing, and placement consistency.
- HT3 / HT2: advanced combos and utility usage progressively unlock; cooldowns and response delays shorten.
- HT1: near-maximum skill and advanced techniques with a small amount of human-like error.
- TT1: maximum skill/aim/technique/tracking, zero miss/mistake chance, minimum reaction/attack/swap delays, and maximum supported turn speed/reach.

Utility profiles tune cobweb frequency/range, pearl cooldown and escape threshold, totem delay/health threshold, potion/shield thresholds, web escape, water clutch, retreat/heal spacing, and movement speed. Advanced combat techniques (attribute swaps, mace/spear combos, hit-select, d-tap, web combos, etc.) unlock progressively.

Crystal PvP does not have a standalone crystal-enable/cooldown setting in PvpBot 3.0.0's exposed settings schema. Its placement/timing quality therefore scales through the native `aim`, `technique`, `tracking`, `skill`, and mistake settings. It remains limited by the bot's actual saved kit.

Tier balancing does not change damage or edit a bot's kit. A bot can only use utility items present in its kit. TT1 intentionally remains max-stat rather than being capped for realism.

## Existing player features
- `/bott gui` opens the chest GUI: Preset, Kit, Add Bot, Remove Bot.
- Players can select a mode, tier, and behavior in the Preset flow.
- Passive does not seek targets and retaliates against the latest valid player who attacks it.
- Aggressive actively seeks nearby eligible players, including its spawner.
- Creative and Spectator players are excluded from targeting and retaliation.
- Arena remains unimplemented and refuses to spawn.
- Random skins use PvpBot 3.0.0's native `BotSkins.random` API and built-in pool/cache.
- Admin kit editor: SAVE, IMPORT (inventory + armor + offhand), DELETE, BACK.
- Regular players may have at most 2 active owned bots; admins bypass the cap.

## Persistent data
Kit files are stored under `plugins/PvpBotDifficulty/presets/<mode>/kit.yml`. Tier assignments, behavior, mode, and ownership are persisted.

## Build/testing
Target: Paper 26.3 build 214, Java 25, PvpBot 3.0.0. Build with `mvn -U --batch-mode clean package`. Tier values are source-implemented but still require in-server balancing tests, especially crystal behavior and utility cadence.
