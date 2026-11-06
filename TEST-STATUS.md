# PvpBotDifficulty 1.2.5 build status

## Target
- Paper API: `26.3.build.214-stable`.
- Java toolchain/release: 25.
- PvpBot dependency: 3.0.0, integrated reflectively.

## Changes in 1.2.5
- Random skin requested per spawned bot through PvpBot's built-in `BotSkins.random` API.
- Passive mode disables automatic target scanning and enables revenge targeting.
- Aggressive mode enables player target scanning and no longer registers the spawner as PvpBot's native owner.
- Creative/Spectator targeting is blocked with `attack-invincible: false`.
- Addon ownership remains stored in `owners.yml`, preserving owner-only removal behavior.
- Existing GUI, tiers, kit editor, commands, and preset persistence retained.
- Arena remains unimplemented.

## Static API verification
The supplied PvpBot 3.0.0 JAR exposes `BotSkins.random(Plugin, Consumer)`, `BotManager.spawn(String, Location, Property)`, `BotManager.contains(String)`, and `BotOwnership.clearOwner(String)`.

## Not verified
This package has not yet been compiled or runtime-tested. Build with the included GitHub Actions workflow (Java 25) or `mvn -U --batch-mode clean package`.
