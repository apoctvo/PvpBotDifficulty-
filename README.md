# PvpBotDifficulty 1.2.9

Wrapper for PvpBot 3.0.0. Main command: `/bott`.

## Tier balancing (1.2.9)

| Setting | LT3 | LT2 | LT1 | HT3 | HT2 | HT1 | TT1 |
|---|---:|---:|---:|---:|---:|---:|---:|
| Mistake chance (%) | 50 | 40 | 30 | 20 | 10 | 5 | 0 |
| Miss chance (%) | 45 | 35 | 25 | 15 | 8 | 3 | 0 |
| Reaction ticks | 100 | 80 | 60 | 40 | 20 | 5 | 0 |
| Crit timing | 3 | 15 | 30 | 50 | 70 | 85 | 100 |
| Turn speed (degrees/tick) | 10 | 15 | 22 | 35 | 55 | 100 | 180 |
| Totem delay (ticks) | 50 | 40 | 30 | 20 | 10 | 5 | 0 |
| Pearl escape health | 2 | 3 | 4 | 5 | 6 | 8 | 10 |
| Food threshold | 3 | 5 | 7 | 9 | 11 | 13 | 16 |
| Web escape delay (ticks) | 50 | 40 | 30 | 20 | 10 | 5 | 0 |
| Web drain chance (%) | 17 | 30 | 45 | 60 | 75 | 90 | 100 |
| Totem health | 2 | 4 | 6 | 8 | 10 | 14 | 18 |
| Retreat health | 3 | 4 | 5 | 6 | 7 | 8 | 10 |
| Heal distance | 8 | 10 | 12 | 14 | 16 | 18 | 22 |
| Movement speed | 1.0 | 1.0 | 1.0 | 1.0 | 1.0 | 1.0 | 1.0 |

`web-drain-chance` means the chance that a bot scoops up an opponent's escape water; it does not mean damage or health drain. Pearl escape health is a health threshold: lower values make the bot wait until it is more heavily damaged before using a pearl to escape.

Other utility settings remain tiered, including cobweb cooldown/range, pearl cooldown/chase distance, potion/shield thresholds, web-escape fumble chance, and clutch success chance. LT3 cobweb use remains disabled for the first 400 ticks (20 seconds) after its profile is applied.

Tier balancing does not change damage or edit a bot's kit. A bot can only use utility items present in its kit. TT1 remains maximum skill with 0% miss and mistake chance.

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
Target: Paper 26.3 build 214, Java 25, PvpBot 3.0.0. Build with `mvn -U --batch-mode clean package`. The source has not been compiled or live-tested in this environment; validate on a test server before release.
