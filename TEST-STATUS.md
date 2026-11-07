# PvpBotDifficulty 1.2.9 status

## Target
- Paper API: `26.3.build.214-stable`.
- Java toolchain/release: 25.
- Dependency: PvpBot 3.0.0.

## Requested balancing changes
- Mistake chance: 50% at LT3, descending to 0% at TT1.
- Miss chance: 45% at LT3, descending to 0% at TT1.
- Totem delay: 50 ticks at LT3, descending to 0 ticks at TT1.
- Pearl escape threshold: lower at LT3 so it waits until lower health before escaping; increases toward TT1.
- Food threshold: 3 at LT3, increasing by tier.
- Reaction ticks: 100 at LT3, descending to 0 at TT1.
- Crit timing: 3 at LT3, increasing by tier.
- Turn speed: 10 degrees/tick at LT3, increasing to 180 at TT1.
- Web escape delay: 50 ticks at LT3, descending to 0 at TT1.
- Web drain chance: 17% at LT3, increasing to 100% at TT1. This setting means chance to scoop an opponent's escape water.
- Totem health threshold: 2 at LT3, increasing by tier.
- Retreat health threshold: 3 at LT3, increasing by tier.
- Heal distance: 8 at LT3, increasing by tier.
- Movement speed: set to the PvpBot default value (1.0) for every tier.
- Previous LT3 cobweb warm-up (400 ticks / 20 seconds) and tiered cobweb cooldown/range retained.
- Damage, saved kits, armor, weapons, and equipment are not changed.

## Verification limits
- The setting names and supported ranges were checked against the supplied PvpBot 3.0.0 settings schema.
- Maven is not installed in this environment and the available runtime is Java 21, while the project targets Java 25; no compile was completed.
- No live Paper 26.3 runtime test has been performed. Build and test on a staging server before replacing the installed plugin.
