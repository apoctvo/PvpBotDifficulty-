# PvpBotDifficulty 1.2.5

Wrapper for the PvpBot plugin. Main command: `/bott`.

## Player features
- `/bott gui` opens a four-button chest GUI: Preset, Kit, Add Bot, Remove Bot.
- Players can select a mode, tier, and behavior in the Preset flow.
- Passive does not seek targets and retaliates against the latest valid player who attacks it.
- Aggressive actively seeks nearby eligible players, including its spawner.
- Creative and Spectator players are excluded from targeting and retaliation.
- Arena is explicitly marked `BELUM JADI` and refuses to spawn.
- Every spawned bot requests a random skin through PvpBot 3.0.0's native `BotSkins.random` API and built-in pool/cache; no curated username list or SkinsRestorer.
- Add Bot creates one unarmored, unequipped bot using the main PvpBot plugin's default profile.
- Regular players may have at most 2 active owned bots. They must remove an owned bot before spawning another.
- Players can remove only their own bots, from the GUI or `/bott remove <name|all>`.
- If the PvpBot ownership API is unavailable, a local `owners.yml` fallback is used.

## Admin features
- Admins bypass the 2-bot player cap and can remove any bot.
- Kit menu is admin-only. Shift + right-click a mode to open its editor.
- Editor actions: SAVE, IMPORT (player inventory + armor + offhand), DELETE, BACK.

## Persistent data
Kit files are stored independently under `plugins/PvpBotDifficulty/presets/<mode>/kit.yml`. They are only created/changed by explicit editor actions and are not overwritten on startup. The plugin never deletes its data folder on disable.

## Build/testing status
Source package for 1.2.5. Requires Paper 26.3 build 214, Java 25, and PvpBot 3.0.0. Random skins are requested through PvpBot's native BotSkins API. Compile with `mvn -U --batch-mode clean package`.
