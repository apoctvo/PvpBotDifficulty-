# PvpBotDifficulty 1.2.4 build status

## Target
- Paper API: `26.3.build.214-stable` (the exact API JAR supplied for the user's Paper build 214 server).
- Java toolchain/release: 25. Paper API classes use class-file major version 69 (Java 25).
- PvP Bot dependency: plugin name `PvpBot`; integration is reflective, so the PvP Bot JAR is not shaded into this plugin.

## Verified statically
- Supplied Paper API JAR is readable and contains Bukkit/Paper classes.
- Paper API `org.bukkit.Bukkit` class-file major version is 69.
- Supplied PvP Bot JAR exposes `PvpBotPlugin.get()`, `bots()`, `setBotSettings(String, ConfigurationSection)`, `ownership()`, `BotNameGenerator.generate(BotManager)`, `BotManager.spawn(String, Location)`, `all()`, `get(String)`, `remove(String)`, and `BotOwnership` ownership methods.
- Build metadata now targets the exact Paper 26.3 build 214 API and Java 25.

## Not verified
- Maven compile/package: this environment currently has JDK 21 only, which cannot read Java 25 class files (major version 69).
- Paper 26.3 build 214 runtime load.
- GUI behavior on Java and Bedrock/Geyser.
- Actual PvpBot AI targeting and kit application in a live server.

## Build locally with JDK 25
Run `mvn -U clean package`. Output should be in `target/pvpbotdifficulty-1.2.4.jar`.
