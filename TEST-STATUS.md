# PvpBotDifficulty 1.2.6 status

## Target
- Paper API: `26.3.build.214-stable`.
- Java toolchain/release: 25.
- PvpBot dependency: 3.0.0, integrated reflectively.

## Changes in 1.2.6
- Replaced the single native-preset-only tier mapping with seven distinct per-bot profiles.
- Tier-specific aim, reaction, timing, technique, tracking, miss chance, mistake chance, swing cadence, and turn speed.
- Tier-specific utility cadence for cobwebs, pearls, totems, potions, shields, web escape, water clutch, retreat/healing, and movement.
- Advanced techniques progressively unlock from HT3 through HT1; TT1 retains maximum supported stats and techniques.
- Crystal timing/placement quality scales through PvpBot's exposed aim/technique/tracking/skill settings; PvpBot 3.0.0 exposes no separate crystal utility toggle in its settings schema.
- No tier changes damage or modifies the saved kit.

## Existing 1.2.5 features retained
- Random skin through PvpBot's native BotSkins API.
- Passive revenge and aggressive auto-target behavior.
- Creative/Spectator target exclusion.
- Existing GUI, kit editor, commands, ownership, and persistence.
- Arena remains unimplemented.

## Verification status
- Profile keys were cross-checked against the supplied PvpBot 3.0.0 config/settings classes.
- Full Paper 26.3 / Java 25 Maven build and live server tests have not been run in this environment.
- Utility frequency, crystal placement, and advanced technique progression must be validated in-game and tuned from observed bot behavior.
