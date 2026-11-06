# PvpBotDifficulty 1.2.2 (source preview)

Wrapper for the PvpBot plugin. Main command: `/bott`.

## Player features
- `/bott gui` opens a four-button chest GUI: Preset, Kit, Add Bot, Remove Bot.
- Players can select a mode, tier, and behavior in the Preset flow.
- Passive is the default behavior. Arena is explicitly marked `BELUM JADI` and refuses to spawn.
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
This is source preview only. It has not been compiled against Paper 26.3 or runtime-tested. Requires PvpBot plugin API compatible with the inspected PvpBot 3.0.0 JAR.
