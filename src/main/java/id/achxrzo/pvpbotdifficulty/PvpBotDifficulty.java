package id.achxrzo.pvpbotdifficulty;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;

import java.lang.reflect.Method;
import java.io.File;
import java.util.*;
import java.util.function.Consumer;

public final class PvpBotDifficulty extends JavaPlugin implements CommandExecutor, TabCompleter, Listener {
    private static final List<String> TIERS = List.of("lt3", "lt2", "lt1", "ht3", "ht2", "ht1", "tt1");
    private static final List<String> BEHAVIORS = List.of("passive", "aggressive", "arena");
    private static final List<String> MODES = List.of("crystal", "sword", "mace", "mace-elytra", "vanilla", "mace-elytra-spear", "minecart", "pot");
    private final Map<String, String> behaviors = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, String> assignments = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, String> modes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private Object pvpPlugin;
    private final Map<UUID, String> editingMode = new HashMap<>();
    private final Map<UUID, String> selectedMode = new HashMap<>();
    private final Map<UUID, String> selectedTier = new HashMap<>();
    private final Map<UUID, String> selectedBehavior = new HashMap<>();
    private final Set<String> pendingNames = new HashSet<>();
    private final Map<String, Integer> profileRevisions = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    private static final class MenuHolder implements InventoryHolder {
        final String type;
        final String mode;
        Inventory inventory;
        MenuHolder(String type, String mode) { this.type = type; this.mode = mode; }
        @Override public Inventory getInventory() { return inventory; }
    }

    @Override public void onEnable() {
        saveDefaultConfig();
        loadAssignments();
        loadBehaviors();
        loadModes();
        loadLocalOwners();
        Objects.requireNonNull(getCommand("bott")).setExecutor(this);
        Objects.requireNonNull(getCommand("bott")).setTabCompleter(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        getDataFolder().toPath().resolve("presets").toFile().mkdirs();
        if (!hookPvpBot()) {
            getLogger().severe("PvpBot was not found. Install PvpBot 3.0.0 before enabling PvpBotDifficulty.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        Bukkit.getScheduler().runTaskLater(this, this::reapplyOnlineAssignments, 60L);
        getLogger().info("PvpBotDifficulty enabled. Per-bot tiers: " + String.join(", ", TIERS));
    }

    @Override public void onDisable() { saveAssignments(); saveBehaviors(); saveModes(); }

    private boolean hookPvpBot() {
        try {
            Class<?> type = Class.forName("com.stashes.pvpbot.PvpBotPlugin");
            pvpPlugin = type.getMethod("get").invoke(null);
            return pvpPlugin != null;
        } catch (Throwable ex) { getLogger().severe("Cannot hook PvpBot: " + ex.getClass().getSimpleName() + ": " + ex.getMessage()); return false; }
    }

    private Object call(Object target, String method, Class<?>[] signature, Object... args) throws Exception {
        Method m = target.getClass().getMethod(method, signature);
        return m.invoke(target, args);
    }

    private Object botManager() throws Exception { return call(pvpPlugin, "bots", new Class<?>[0]); }

    private boolean applyTier(String botName, String tier) throws Exception {
        assignments.put(botName, tier.toUpperCase(Locale.ROOT));
        applyProfile(botName, tier, behaviors.getOrDefault(botName, "passive"));
        saveAssignments();
        return true;
    }

    private void applyProfile(String botName, String tier, String behavior) throws Exception {
        int revision = profileRevisions.merge(botName, 1, Integer::sum);
        boolean novice = tier != null && tier.equalsIgnoreCase("lt3");
        applyProfileSettings(botName, tier, behavior, !novice);
        if (novice) scheduleLt3WebUnlock(botName, revision, 400L, 12);
    }

    /*
     * LT3 starts without direct cobweb placement. PvpBot's cobweb cooldown is
     * measured between uses, not from spawn, so a long cooldown alone still
     * lets a novice place a web on the very first close-range tick. Unlock it
     * after 20 seconds, once the bot has had to fight without that instant play.
     */
    private void scheduleLt3WebUnlock(String botName, int revision, long delayTicks, int retries) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!isEnabled() || profileRevisions.getOrDefault(botName, 0) != revision
                    || !"LT3".equalsIgnoreCase(assignments.get(botName))) return;
            try {
                Object bot = call(botManager(), "get", new Class<?>[]{String.class}, botName);
                if (bot == null) {
                    if (retries > 0) scheduleLt3WebUnlock(botName, revision, 100L, retries - 1);
                    else getLogger().warning("LT3 cobweb unlock skipped: bot did not appear: " + botName);
                    return;
                }
                applyProfileSettings(botName, "lt3", behaviors.getOrDefault(botName, "passive"), true);
            } catch (Throwable ex) {
                getLogger().warning("Could not unlock LT3 cobwebs for " + botName + ": " + ex.getMessage());
            }
        }, delayTicks);
    }

    private void applyProfileSettings(String botName, String tier, String behavior, boolean lt3WebUnlocked) throws Exception {
        YamlConfiguration section = new YamlConfiguration();
        if (tier != null && TIERS.contains(tier.toLowerCase(Locale.ROOT))) {
            Map<String,Object> values = tierValues(tier);
            if (tier.equalsIgnoreCase("lt3")) {
                values.put("cobwebs", lt3WebUnlocked);
                values.put("cobweb-cooldown", 600);
                values.put("cobweb-global-cooldown", 120);
            }
            for (Map.Entry<String,Object> e : values.entrySet()) section.set(e.getKey(), e.getValue());
        }
        boolean aggressive = behavior.equalsIgnoreCase("aggressive");
        section.set("auto-target", aggressive);
        section.set("target-players", aggressive);
        section.set("revenge", !aggressive);
        section.set("attack-invincible", false);
        section.set("target-passive-mobs", false);
        call(pvpPlugin, "setBotSettings", new Class<?>[]{String.class, ConfigurationSection.class}, botName, section);


    }

    @SuppressWarnings("unchecked")
    private Map<String,Object> tierValues(String tier) throws Exception {
        String normalized = tier.toLowerCase(Locale.ROOT);
        if (!TIERS.contains(normalized)) throw new IllegalArgumentException("Unknown tier: " + tier);
        String nativeLevel = switch (normalized) {
            case "lt3" -> "easy";
            case "lt2" -> "medium";
            case "lt1" -> "hard";
            case "ht3" -> "expert";
            case "ht2" -> "professional";
            case "ht1", "tt1" -> "perfect";
            default -> throw new IllegalArgumentException("Unknown tier: " + tier);
        };
        Class<?> difficulty = Class.forName("com.stashes.pvpbot.bot.BotDifficulty");
        Map<String,Object> base = (Map<String,Object>) difficulty.getMethod("preset", String.class).invoke(null, nativeLevel);
        Map<String,Object> result = new LinkedHashMap<>(base);
        applyTierTuning(result, normalized);
        return result;
    }

    /*
     * Native PvpBot presets cover core combat skill only. These per-bot
     * overrides give every addon tier its own utility cadence and technique
     * profile without changing damage or the bot's saved kit.
     */
    private void applyTierTuning(Map<String,Object> values, String tier) {
        values.put("aim-hits", true);
        switch (tier) {
            case "lt3" -> {
                utility(values, 600, 0.5, 300, 8.0, 50, 50, 70, 45, 8, 5, 2, 2.0, 3.0, 8.0, 1.0);
                utilityStrategy(values, 17, false, false, false, 8.0);
                values.put("food-threshold", 3); values.put("shield-disable-chance", 15);
                values.put("clutch", 10); values.put("shield-disable-speed", 10);
                values.put("skill", 15);
                values.put("aim", 15); values.put("reaction", 10); values.put("crit-timing", 3);
                values.put("technique", 10); values.put("tracking", 15);
                values.put("miss-chance", 45); values.put("mistake-chance", 50);
                values.put("reaction-ticks", 100); values.put("attack-cooldown", 20); values.put("turn-speed", 10.0);
                advancedTechniques(values, false, false, false);
                values.put("special-weapon-melee-chance", 80);
            }
            case "lt2" -> {
                utility(values, 450, 1.0, 240, 10.0, 40, 40, 60, 30, 9, 6, 4, 3.0, 4.0, 10.0, 1.0);
                utilityStrategy(values, 30, false, false, false, 6.0);
                values.put("food-threshold", 5); values.put("shield-disable-chance", 30);
                values.put("clutch", 30); values.put("shield-disable-speed", 30);
                values.put("skill", 40);
                values.put("aim", 40); values.put("reaction", 35); values.put("crit-timing", 15);
                values.put("technique", 35); values.put("tracking", 40);
                values.put("miss-chance", 35); values.put("mistake-chance", 40);
                values.put("reaction-ticks", 80); values.put("attack-cooldown", 18); values.put("turn-speed", 15.0);
                advancedTechniques(values, false, false, false);
                values.put("special-weapon-melee-chance", 70);
            }
            case "lt1" -> {
                utility(values, 300, 1.5, 180, 12.0, 30, 30, 50, 25, 10, 7, 6, 4.0, 5.0, 12.0, 1.0);
                utilityStrategy(values, 45, false, false, false, 5.0);
                values.put("food-threshold", 7); values.put("shield-disable-chance", 45);
                values.put("clutch", 50); values.put("shield-disable-speed", 50);
                values.put("skill", 60);
                values.put("aim", 60); values.put("reaction", 55); values.put("crit-timing", 30);
                values.put("technique", 55); values.put("tracking", 60);
                values.put("miss-chance", 25); values.put("mistake-chance", 30);
                values.put("reaction-ticks", 60); values.put("attack-cooldown", 16); values.put("turn-speed", 22.0);
                advancedTechniques(values, false, false, false);
                values.put("special-weapon-melee-chance", 60);
            }
            case "ht3" -> {
                utility(values, 220, 2.0, 140, 14.0, 20, 20, 35, 20, 11, 8, 8, 5.0, 6.0, 14.0, 1.0);
                utilityStrategy(values, 60, true, false, false, 4.0);
                values.put("food-threshold", 9); values.put("shield-disable-chance", 65);
                values.put("clutch", 70); values.put("shield-disable-speed", 70);
                values.put("skill", 75);
                values.put("aim", 75); values.put("reaction", 75); values.put("crit-timing", 50);
                values.put("technique", 75); values.put("tracking", 75);
                values.put("miss-chance", 15); values.put("mistake-chance", 20);
                values.put("reaction-ticks", 40); values.put("attack-cooldown", 14); values.put("turn-speed", 35.0);
                advancedTechniques(values, true, false, false);
                values.put("special-weapon-melee-chance", 45);
            }
            case "ht2" -> {
                utility(values, 150, 2.6, 100, 16.0, 10, 10, 20, 15, 12, 9, 10, 6.0, 7.0, 16.0, 1.0);
                utilityStrategy(values, 75, true, true, false, 3.0);
                values.put("food-threshold", 11); values.put("shield-disable-chance", 80);
                values.put("clutch", 85); values.put("shield-disable-speed", 85);
                values.put("skill", 90);
                values.put("aim", 90); values.put("reaction", 90); values.put("crit-timing", 70);
                values.put("technique", 90); values.put("tracking", 90);
                values.put("miss-chance", 8); values.put("mistake-chance", 10);
                values.put("reaction-ticks", 20); values.put("attack-cooldown", 12); values.put("turn-speed", 55.0);
                advancedTechniques(values, true, true, false);
                values.put("special-weapon-melee-chance", 25);
            }
            case "ht1" -> {
                utility(values, 80, 3.2, 65, 17.0, 5, 5, 8, 10, 13, 10, 14, 8.0, 8.0, 18.0, 1.0);
                utilityStrategy(values, 90, true, true, true, 2.0);
                values.put("food-threshold", 13); values.put("shield-disable-chance", 95);
                values.put("clutch", 95); values.put("shield-disable-speed", 95);
                values.put("skill", 95);
                values.put("aim", 95); values.put("reaction", 95); values.put("crit-timing", 85);
                values.put("technique", 95); values.put("tracking", 95);
                values.put("miss-chance", 3); values.put("mistake-chance", 5);
                values.put("reaction-ticks", 5); values.put("attack-cooldown", 10); values.put("turn-speed", 100.0);
                advancedTechniques(values, true, true, true);
                values.put("special-weapon-melee-chance", 10);
                values.put("elytra-mace", true); values.put("mace-pearl-launch", true); values.put("cart-pvp", true);
            }
            case "tt1" -> {
                utility(values, 40, 3.5, 40, 18.0, 0, 0, 0, 5, 14, 14, 18, 10.0, 10.0, 22.0, 1.0);
                utilityStrategy(values, 100, true, true, true, 1.5);
                values.put("food-threshold", 16); values.put("shield-disable-chance", 100);
                values.put("clutch", 100); values.put("shield-disable-speed", 100);
                values.put("skill", 100);
                values.put("aim", 100); values.put("reaction", 100); values.put("crit-timing", 100);
                values.put("technique", 100); values.put("tracking", 100);
                values.put("shield-charge", 100); values.put("strafe", 0);
                values.put("miss-chance", 0); values.put("mistake-chance", 0);
                values.put("reaction-ticks", 0); values.put("attack-cooldown", 1); values.put("turn-speed", 180.0);
                values.put("melee-range", 4.2);
                advancedTechniques(values, true, true, true);
                values.put("special-weapon-melee-chance", 0);
                values.put("elytra-mace", true); values.put("mace-pearl-launch", true); values.put("cart-pvp", true);
                values.put("cart-cooldown", 40); values.put("swap-cooldown", 1);
            }
            default -> throw new IllegalArgumentException("Unknown tier: " + tier);
        }
    }

    private void utility(Map<String,Object> values, int webCooldown, double webRange,
                         int pearlCooldown, double pearlChase, int totemDelay,
                         int webEscapeDelay, int webFumble, int drainDelay,
                         int potionHealth, int shieldHealth, int totemHealth,
                         double pearlEscapeHealth, double retreatHealth,
                         double healDistance, double movementSpeed) {
        values.put("cobwebs", true);
        values.put("cobweb-cooldown", webCooldown);
        values.put("cobweb-global-cooldown", webCooldown);
        values.put("cobweb-range", webRange);
        values.put("pearls", true);
        values.put("pearl-cooldown", pearlCooldown);
        values.put("pearl-chase-distance", pearlChase);
        values.put("pearl-escape-health", pearlEscapeHealth);
        values.put("totem-delay-ticks", totemDelay);
        values.put("totem-health", totemHealth);
        values.put("web-escape-delay", webEscapeDelay);
        values.put("web-escape-fumble-chance", webFumble);
        values.put("drain", true);
        values.put("drain-delay", drainDelay);
        values.put("auto-totem", true);
        values.put("auto-potion", true);
        values.put("potion-health", potionHealth);
        values.put("auto-shield", true);
        values.put("shield-health", shieldHealth);
        values.put("buff-potions", true);
        values.put("retreat", true);
        values.put("retreat-health", retreatHealth);
        values.put("heal-distance", healDistance);
        values.put("heal-distance-min", Math.max(4.0, healDistance - 4.0));
        values.put("heal-distance-max", healDistance + 12.0);
        values.put("auto-food", true);
        values.put("water-clutch", true);
        values.put("water-clutch-min-fall", 6.0);
        values.put("water-clutch-window", 4.0);
        values.put("water-clutch-pickup", true);
        values.put("speed", movementSpeed);
    }

    private void utilityStrategy(Map<String,Object> values, int drainChance,
                             boolean prediction, boolean headWeb, boolean hitWeb, double potionGap) {
        values.put("web-drain-chance", drainChance);
        values.put("web-prediction", prediction);
        values.put("head-web", headWeb);
        values.put("hit-web", hitWeb);
        values.put("potion-gap", potionGap);
    }

    private void advancedTechniques(Map<String,Object> values, boolean basic, boolean combos, boolean elite) {
        values.put("crits", true);
        values.put("shield-disable", true);
        values.put("attribute-swap", combos);
        values.put("breach-swap", combos);
        values.put("mace-stun", combos);
        values.put("spear-lunge", combos);
        values.put("mace-pearl-launch", elite);
        values.put("elytra-mace", elite);
        values.put("cart-pvp", elite);
        values.put("crit-chain", basic);
        values.put("cooldown-trick", combos);
        values.put("jump-reset", basic);
        values.put("mirror-punish", combos);
        values.put("hit-select", combos);
        values.put("d-tap", combos);
        values.put("hit-web-combo", elite);
        values.put("sweep-hit", basic);
        values.put("priority-hp-target", combos);
        values.put("shield-read-windup", combos);
    }

    private void reapplyOnlineAssignments() {
        if (pvpPlugin == null) return;
        ConfigurationSection owners = localOwners.getConfigurationSection("owners");
        if (owners != null) for (String name : owners.getKeys(false)) clearNativeOwner(name);
        for (Map.Entry<String,String> entry : new ArrayList<>(assignments.entrySet())) {
            try {
                Object player = call(botManager(), "get", new Class<?>[]{String.class}, entry.getKey());
                if (player != null) applyProfile(entry.getKey(), entry.getValue(), behaviors.getOrDefault(entry.getKey(), "passive"));
            } catch (Throwable ex) { getLogger().warning("Could not restore tier for " + entry.getKey() + ": " + ex.getMessage()); }
        }
        for (Map.Entry<String,String> entry : new ArrayList<>(behaviors.entrySet())) {
            try {
                Object player = call(botManager(), "get", new Class<?>[]{String.class}, entry.getKey());
                if (player != null) applyProfile(entry.getKey(), assignments.get(entry.getKey()), entry.getValue());
            } catch (Throwable ex) { getLogger().warning("Could not restore behavior for " + entry.getKey() + ": " + ex.getMessage()); }
        }
    }

    private void clearNativeOwner(String botName) {
        try {
            Object ownership = call(pvpPlugin, "ownership", new Class<?>[0]);
            call(ownership, "clearOwner", new Class<?>[]{String.class}, botName);
        } catch (Throwable ex) {
            getLogger().fine("Native owner clear unavailable for " + botName + ": " + ex.getMessage());
        }
    }

    private void loadAssignments() {
        assignments.clear();
        if (getConfig().isConfigurationSection("bots")) {
            for (String name : getConfig().getConfigurationSection("bots").getKeys(false)) {
                String tier = getConfig().getString("bots." + name, "").toLowerCase(Locale.ROOT);
                if (TIERS.contains(tier)) assignments.put(name, tier.toUpperCase(Locale.ROOT));
            }
        }
    }
    private void saveAssignments() {
        getConfig().set("bots", null);
        for (Map.Entry<String,String> e : assignments.entrySet()) getConfig().set("bots." + e.getKey(), e.getValue().toLowerCase(Locale.ROOT));
        saveConfig();
    }


    private void loadBehaviors() {
        behaviors.clear();
        if (getConfig().isConfigurationSection("behavior")) {
            for (String name : getConfig().getConfigurationSection("behavior").getKeys(false)) {
                String value = getConfig().getString("behavior." + name, "passive").toLowerCase(Locale.ROOT);
                if (BEHAVIORS.contains(value)) behaviors.put(name, value);
            }
        }
    }
    private void saveBehaviors() {
        getConfig().set("behavior", null);
        for (Map.Entry<String,String> e : behaviors.entrySet()) getConfig().set("behavior." + e.getKey(), e.getValue());
        saveConfig();
    }

    private void loadModes() {
        modes.clear();
        if (getConfig().isConfigurationSection("mode")) {
            for (String name : getConfig().getConfigurationSection("mode").getKeys(false)) {
                String value = getConfig().getString("mode." + name, "none").toLowerCase(Locale.ROOT);
                if (value.equals("none") || MODES.contains(value)) modes.put(name, value);
            }
        }
    }
    private void saveModes() {
        getConfig().set("mode", null);
        for (Map.Entry<String,String> e : modes.entrySet()) getConfig().set("mode." + e.getKey(), e.getValue());
        saveConfig();
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) { help(sender); return true; }
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        boolean admin = sender.hasPermission("pvpbotdifficulty.admin");
        if (subcommand.equals("spawn")) {
            if (!admin && !sender.hasPermission("pvpbotdifficulty.spawn")) {
                sender.sendMessage("§cKamu tidak punya izin untuk spawn bot."); return true;
            }
        } else if (!admin && !Set.of("gui", "remove", "list", "tiers").contains(subcommand)) {
            sender.sendMessage("§cFitur ini hanya untuk admin."); return true;
        }
        try {
            switch (subcommand) {
                case "spawn" -> spawn(sender, args);
                case "gui" -> openMainMenu(sender);
                case "tier", "set" -> setTier(sender, args);
                case "list" -> list(sender);
                case "remove" -> remove(sender, args);
                case "tiers" -> sender.sendMessage("§eTier: §fLT3, LT2, LT1, HT3, HT2, HT1, TT1");
                case "behavior", "behaviour" -> setBehavior(sender, args);
                default -> help(sender);
            }
        } catch (IllegalArgumentException ex) { sender.sendMessage("§c" + ex.getMessage()); }
          catch (Throwable ex) { sender.sendMessage("§cGagal: " + ex.getClass().getSimpleName() + ": " + ex.getMessage()); getLogger().warning("Command failed: " + ex); }
        return true;
    }

    private void spawn(CommandSender sender, String[] args) throws Exception {
        if (!(sender instanceof Player player)) throw new IllegalArgumentException("Spawn hanya bisa dilakukan oleh pemain.");
        if (args.length < 3 || args.length > 5) {
            sender.sendMessage("§e/bott spawn <jumlah> <tier> [perilaku] [mode]");
            sender.sendMessage("§7Default perilaku: passive. Pemain biasa maksimal 2 bot aktif."); return;
        }
        int quantity;
        try { quantity = Integer.parseInt(args[1]); } catch (NumberFormatException ex) { throw new IllegalArgumentException("Jumlah bot harus berupa angka."); }
        boolean admin = player.hasPermission("pvpbotdifficulty.admin");
        if (quantity < 1 || quantity > (admin ? 5 : 2)) throw new IllegalArgumentException(admin ? "Jumlah per perintah maksimal 5 bot." : "Pemain biasa maksimal spawn 2 bot.");
        if (!admin) {
            int owned = ownedBots(player).size();
            if (owned + quantity > 2) throw new IllegalArgumentException("Batasmu 2 bot aktif. Gunakan /bott remove <nama> atau GUI Remove Bot dulu.");
        }
        String tier = args[2].toLowerCase(Locale.ROOT);
        if (!TIERS.contains(tier)) throw new IllegalArgumentException("Tier tidak dikenal. Gunakan /bott tiers.");
        String behavior = "passive", mode = "none";
        if (args.length == 4) {
            String value = args[3].toLowerCase(Locale.ROOT);
            if (BEHAVIORS.contains(value)) behavior = value;
            else if (MODES.contains(value)) mode = value;
            else throw new IllegalArgumentException("Perilaku atau mode tidak dikenal.");
        } else if (args.length == 5) {
            behavior = args[3].toLowerCase(Locale.ROOT); mode = args[4].toLowerCase(Locale.ROOT);
            if (!BEHAVIORS.contains(behavior)) throw new IllegalArgumentException("Perilaku harus passive, aggressive, atau arena.");
            if (!MODES.contains(mode)) throw new IllegalArgumentException("Mode tidak dikenal.");
        }
        if (behavior.equals("arena")) throw new IllegalArgumentException("§eARENA BELUM JADI — arena belum dikonfigurasi.");
        spawnBots(player, quantity, tier, behavior, mode, true);
    }

    private void spawnBots(Player owner, int quantity, String tier, String behavior, String mode, boolean applyKit) throws Exception {
        boolean admin = owner.hasPermission("pvpbotdifficulty.admin");
        if (!admin && ownedBots(owner).size() + quantity > 2) throw new IllegalArgumentException("Batasmu 2 bot aktif. Hapus bot lama sebelum spawn lagi.");

        Object manager = botManager();
        Class<?> generator = Class.forName("com.stashes.pvpbot.bot.BotNameGenerator");
        Method generate = generator.getMethod("generate", manager.getClass());
        Location base = owner.getLocation().clone();
        List<String> requestedNames = new ArrayList<>();

        for (int i = 0; i < quantity; i++) {
            String name = null;
            for (int attempt = 0; attempt < 30; attempt++) {
                String candidate = (String) generate.invoke(null, manager);
                if (candidate != null && !pendingNames.contains(candidate)
                        && !managerContains(manager, candidate)
                        && Bukkit.getPlayerExact(candidate) == null) {
                    name = candidate;
                    break;
                }
            }
            if (name == null) throw new IllegalArgumentException("Gagal mendapatkan nama bot unik. Coba lagi.");
            pendingNames.add(name);
            requestedNames.add(name);
            if (!tier.equals("native")) applyTier(name, tier);
            applyBehavior(name, behavior);
            modes.put(name, mode);
            saveModes();
            spawnWithRandomSkin(manager, name, base.clone().add(i * 1.5, 0, 0), owner, behavior, mode, applyKit);
        }
        owner.sendMessage("§eMeminta skin acak PvpBot untuk " + requestedNames.size() + " bot...");
    }

    private boolean managerContains(Object manager, String name) {
        try { return (Boolean) call(manager, "contains", new Class<?>[]{String.class}, name); }
        catch (Throwable ignored) { return false; }
    }

    private void spawnWithRandomSkin(Object manager, String name, Location location, Player owner,
                                     String behavior, String mode, boolean applyKit) {
        try {
            Class<?> skins = Class.forName("com.stashes.pvpbot.bot.BotSkins");
            Method random = skins.getMethod("random", org.bukkit.plugin.Plugin.class, Consumer.class);
            Consumer<Object> callback = result -> Bukkit.getScheduler().runTask(this, () -> {
                try {
                    if (!isEnabled()) return;
                    Object skin = result instanceof Optional<?> value ? value.orElse(null) : null;
                    Object spawned = null;
                    if (skin != null) {
                        for (Method method : manager.getClass().getMethods()) {
                            Class<?>[] params = method.getParameterTypes();
                            if (method.getName().equals("spawn") && params.length == 3
                                    && params[0] == String.class && params[1] == Location.class
                                    && params[2].isInstance(skin)) {
                                spawned = method.invoke(manager, name, location, skin);
                                break;
                            }
                        }
                    }
                    if (spawned == null) spawned = call(manager, "spawn",
                            new Class<?>[]{String.class, Location.class}, name, location);
                    if (!(spawned instanceof Player bot)) {
                        cleanupFailedSpawn(name);
                        if (owner.isOnline()) owner.sendMessage("§cBot " + name + " gagal spawn.");
                        return;
                    }

                    localOwners.set("owners." + name, owner.getUniqueId().toString());
                    localOwners.save(new File(getDataFolder(), "owners.yml"));
                    clearNativeOwner(name);
                    clearLoadout(bot);
                    if (applyKit && !mode.equals("none")) applySavedKit(bot, mode);
                    if (owner.isOnline()) owner.sendMessage("§aBot §f" + name + " §aberhasil spawn §7(" + behavior + ", skin acak).");
                } catch (Throwable ex) {
                    cleanupFailedSpawn(name);
                    getLogger().warning("Failed to finish spawning " + name + ": " + ex.getMessage());
                    if (owner.isOnline()) owner.sendMessage("§cGagal spawn " + name + ": " + ex.getMessage());
                } finally {
                    pendingNames.remove(name);
                }
            });
            random.invoke(null, this, callback);
        } catch (Throwable ex) {
            pendingNames.remove(name);
            cleanupFailedSpawn(name);
            getLogger().warning("PvpBot random skin API failed for " + name + ": " + ex.getMessage());
            if (owner.isOnline()) owner.sendMessage("§cGagal meminta skin acak untuk " + name + ": " + ex.getMessage());
        }
    }

    private void cleanupFailedSpawn(String name) {
        profileRevisions.merge(name, 1, Integer::sum);
        assignments.remove(name); behaviors.remove(name); modes.remove(name);
        saveAssignments(); saveBehaviors(); saveModes();
    }

    private YamlConfiguration localOwners = new YamlConfiguration();
    private void loadLocalOwners() { File f = new File(getDataFolder(), "owners.yml"); if (f.isFile()) localOwners = YamlConfiguration.loadConfiguration(f); }
    private List<String> ownedBots(Player player) {
        Set<String> active = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        active.addAll(botNames());
        Set<String> result = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        try {
            Object ownership = call(pvpPlugin, "ownership", new Class<?>[0]);
            List<String> nativeOwned = (List<String>) call(ownership, "botsOwnedBy", new Class<?>[]{UUID.class}, player.getUniqueId());
            for (String name : nativeOwned) if (active.contains(name)) result.add(name);
        } catch (Throwable ignored) { /* use local fallback below */ }
        for (String name : active) if (player.getUniqueId().toString().equals(localOwners.getString("owners." + name))) result.add(name);
        return new ArrayList<>(result);
    }
    @SuppressWarnings("unchecked") private List<String> botNames() {
        try { return (List<String>) call(botManager(), "all", new Class<?>[0]); } catch (Throwable ignored) { return new ArrayList<>(); }
    }
    private boolean owns(Player player, String botName) { return ownedBots(player).stream().anyMatch(n -> n.equalsIgnoreCase(botName)); }

    private void applyBehavior(String botName, String behavior) throws Exception {
        behaviors.put(botName, behavior.toLowerCase(Locale.ROOT));
        applyProfile(botName, assignments.get(botName), behavior);
        saveBehaviors();
    }

    private void setBehavior(CommandSender sender, String[] args) throws Exception {
        if (args.length != 3) { sender.sendMessage("§e/bott behavior <nama-bot> <passive|aggressive|arena>"); return; }
        String name = args[1], behavior = args[2].toLowerCase(Locale.ROOT);
        if (!BEHAVIORS.contains(behavior)) throw new IllegalArgumentException("Behavior harus passive, aggressive, atau arena.");
        if (behavior.equals("arena")) throw new IllegalArgumentException("ARENA BELUM JADI — arena belum dikonfigurasi.");
        if (call(botManager(), "get", new Class<?>[]{String.class}, name) == null) throw new IllegalArgumentException("Bot tidak ditemukan: " + name);
        applyBehavior(name, behavior);
        sender.sendMessage("§aBehavior §f" + name + " §adiubah menjadi §e" + behavior + "§a.");
    }

    private void setTier(CommandSender sender, String[] args) throws Exception {
        if (args.length != 3) { sender.sendMessage("§e/bott tier <nama-bot> <tier>"); return; }
        String name = args[1], tier = args[2].toLowerCase(Locale.ROOT);
        if (!TIERS.contains(tier)) throw new IllegalArgumentException("Tier tidak dikenal. Gunakan /bott tiers.");
        Object player = call(botManager(), "get", new Class<?>[]{String.class}, name);
        if (player == null) throw new IllegalArgumentException("Bot tidak ditemukan atau sedang offline: " + name);
        applyTier(name, tier);
        sender.sendMessage("§aTier " + name + " diubah menjadi §b" + tier.toUpperCase(Locale.ROOT));
    }

    @SuppressWarnings("unchecked") private void list(CommandSender sender) throws Exception {
        Object manager = botManager(); List<String> names = (List<String>) call(manager, "all", new Class<?>[0]);
        if (sender instanceof Player p && !p.hasPermission("pvpbotdifficulty.admin")) names = ownedBots(p);
        sender.sendMessage("§6PvP bots: §f" + names.size());
        for (String name : names) sender.sendMessage("§7- §f" + name + " §8[§b" + assignments.getOrDefault(name, "PVPBOT DEFAULT") + "§8] §7(" + behaviors.getOrDefault(name, "default") + ")");
    }

    private void remove(CommandSender sender, String[] args) throws Exception {
        if (!(sender instanceof Player player)) throw new IllegalArgumentException("Remove hanya bisa dilakukan pemain.");
        if (args.length != 2) { sender.sendMessage("§e/bott remove <nama-bot|all>"); return; }
        boolean admin = player.hasPermission("pvpbotdifficulty.admin");
        String name = args[1];
        if (name.equalsIgnoreCase("all")) {
            List<String> targets = admin ? botNames() : ownedBots(player);
            int removed = 0; for (String bot : targets) if (removeOne(bot)) removed++;
            player.sendMessage("§aBerhasil menghapus " + removed + " bot."); return;
        }
        if (!admin && !owns(player, name)) throw new IllegalArgumentException("Kamu hanya bisa menghapus bot milikmu sendiri.");
        if (removeOne(name)) player.sendMessage("§aBot " + name + " dihapus."); else player.sendMessage("§cBot tidak ditemukan: " + name);
    }
    private boolean removeOne(String name) throws Exception {
        boolean removed = (Boolean) call(botManager(), "remove", new Class<?>[]{String.class}, name);
        if (removed) {
            profileRevisions.merge(name, 1, Integer::sum);
            assignments.remove(name); behaviors.remove(name); modes.remove(name); localOwners.set("owners." + name, null);
            saveAssignments(); saveBehaviors(); saveModes(); localOwners.save(new File(getDataFolder(), "owners.yml"));
            clearNativeOwner(name);
        }
        return removed;
    }

    private void openMainMenu(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage("§cGUI hanya bisa dibuka di dalam game."); return; }
        MenuHolder h = new MenuHolder("main", null); Inventory inv = Bukkit.createInventory(h, 27, "§8PvpBotDifficulty"); h.inventory = inv;
        inv.setItem(10, named(Material.ENCHANTED_BOOK, "§ePRESET", "§7Pilih mode, tier, dan perilaku"));
        inv.setItem(12, named(Material.CHEST, "§6KIT", player.hasPermission("pvpbotdifficulty.admin") ? "§7Shift + klik kanan mode untuk edit" : "§cKhusus admin"));
        inv.setItem(14, named(Material.PLAYER_HEAD, "§aADD BOT", "§7Spawn bot default PvpBot", "§7Tanpa armor dan perlengkapan", "§7Pemain biasa maksimal 2 bot"));
        inv.setItem(16, named(Material.BARRIER, "§cREMOVE BOT", "§7Hapus bot milikmu"));
        player.openInventory(inv);
    }
    private ItemStack named(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material); ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName(name); meta.setLore(Arrays.asList(lore)); item.setItemMeta(meta); } return item;
    }
    private String display(ItemStack item) { return item == null || !item.hasItemMeta() ? "" : ChatColor.stripColor(item.getItemMeta().getDisplayName()).toLowerCase(Locale.ROOT); }
    private void openModeMenu(Player p, boolean kit) {
        MenuHolder h = new MenuHolder(kit ? "kitlist" : "modes", null); Inventory inv = Bukkit.createInventory(h, 27, kit ? "§8Kit Presets" : "§8Pilih Mode"); h.inventory = inv;
        int[] slots = {10,11,12,13,14,15,16,19};
        for (int i=0;i<MODES.size();i++) inv.setItem(slots[i], named(modeIcon(MODES.get(i)), "§e"+MODES.get(i), kit ? "§7Shift + klik kanan: edit (admin)" : "§7Klik untuk pilih mode"));
        inv.setItem(22, named(Material.ARROW,"§cKembali")); p.openInventory(inv);
    }
    private Material modeIcon(String mode) { return switch(mode) { case "crystal"->Material.END_CRYSTAL; case "sword"->Material.DIAMOND_SWORD; case "mace"->Material.MACE; case "mace-elytra"->Material.ELYTRA; case "vanilla"->Material.GRASS_BLOCK; case "mace-elytra-spear"->Material.TRIDENT; case "minecart"->Material.TNT_MINECART; case "pot"->Material.SPLASH_POTION; default->Material.CHEST; }; }
    private void openTierMenu(Player p, String mode) {
        selectedMode.put(p.getUniqueId(), mode); MenuHolder h = new MenuHolder("tiers", mode); Inventory inv = Bukkit.createInventory(h, 27, "§8Pilih Tier | "+mode); h.inventory=inv;
        Material[] icons={Material.WOODEN_SWORD,Material.STONE_SWORD,Material.IRON_SWORD,Material.GOLDEN_SWORD,Material.DIAMOND_SWORD,Material.NETHERITE_SWORD,Material.NETHER_STAR};
        for(int i=0;i<TIERS.size();i++) inv.setItem(10+i,named(icons[i],"§b"+TIERS.get(i).toUpperCase(Locale.ROOT),"§7Klik untuk memilih tier"));
        inv.setItem(22,named(Material.ARROW,"§cKembali")); p.openInventory(inv);
    }
    private void openBehaviorMenu(Player p, String tier) {
        selectedTier.put(p.getUniqueId(), tier); MenuHolder h=new MenuHolder("behaviors",selectedMode.get(p.getUniqueId())); Inventory inv=Bukkit.createInventory(h,27,"§8Pilih Perilaku"); h.inventory=inv;
        inv.setItem(11,named(Material.IRON_DOOR,"§aPASSIVE","§7Tidak memburu pemain"));
        inv.setItem(13,named(Material.IRON_SWORD,"§cAGGRESSIVE","§7Mencari pemain terdekat"));
        inv.setItem(15,named(Material.BARRIER,"§eARENA — BELUM JADI","§7Arena belum dibuat"));
        inv.setItem(22,named(Material.ARROW,"§cKembali")); p.openInventory(inv);
    }
    private void openRemoveMenu(Player p) {
        List<String> names = p.hasPermission("pvpbotdifficulty.admin") ? botNames() : ownedBots(p);
        MenuHolder h=new MenuHolder("remove",null); Inventory inv=Bukkit.createInventory(h,54,"§8Remove Bot"); h.inventory=inv;
        int slot=0; for(String name:names) { if(slot>=45) break; Player bot=null; try { bot=(Player)call(botManager(),"get",new Class<?>[]{String.class},name); } catch(Throwable ignored){}; String world=bot==null?"offline":bot.getWorld().getName(); inv.setItem(slot++,named(Material.SKELETON_SKULL,"§c"+name,"§7World: "+world,"§eKlik untuk hapus")); }
        if(names.isEmpty()) inv.setItem(22,named(Material.BARRIER,"§cTidak ada bot milikmu")); inv.setItem(49,named(Material.ARROW,"§cKembali")); p.openInventory(inv);
    }
    private void openKitEditor(Player p,String mode) {
        MenuHolder h=new MenuHolder("editor",mode); Inventory inv=Bukkit.createInventory(h,54,"§8Kit Editor | "+mode); h.inventory=inv;
        File file=new File(new File(getDataFolder(),"presets/"+mode),"kit.yml");
        if(file.isFile()) { YamlConfiguration y=YamlConfiguration.loadConfiguration(file); List<?> list=y.getList("contents",List.of()); for(int i=0;i<Math.min(41,list.size());i++) if(list.get(i) instanceof ItemStack it) inv.setItem(i,it.clone()); }
        inv.setItem(45,named(Material.LIME_CONCRETE,"§aSAVE","§7Simpan kit ke folder preset"));
        inv.setItem(49,named(Material.HOPPER,"§eIMPORT","§7Salin inventory + armor + offhand admin"));
        inv.setItem(51,named(Material.TNT,"§cDELETE","§7Hapus kit tersimpan"));
        inv.setItem(53,named(Material.ARROW,"§fBACK")); editingMode.put(p.getUniqueId(),mode); p.openInventory(inv);
    }
    private void clearLoadout(Player bot) {
        bot.getInventory().clear();
        bot.getInventory().setArmorContents(new ItemStack[4]);
        bot.getInventory().setItemInOffHand(null);
    }
    private void applySavedKit(Player bot,String mode) {
        File file=new File(new File(getDataFolder(),"presets/"+mode),"kit.yml"); if(!file.isFile()) return;
        try { List<?> stored=YamlConfiguration.loadConfiguration(file).getList("contents",List.of()); ItemStack[] main=new ItemStack[36], armor=new ItemStack[4];
            for(int i=0;i<36&&i<stored.size();i++) if(stored.get(i) instanceof ItemStack it) main[i]=it.clone();
            for(int i=0;i<4&&36+i<stored.size();i++) if(stored.get(36+i) instanceof ItemStack it) armor[i]=it.clone();
            bot.getInventory().clear(); bot.getInventory().setContents(main); bot.getInventory().setArmorContents(armor); if(stored.size()>40&&stored.get(40) instanceof ItemStack off) bot.getInventory().setItemInOffHand(off.clone());
        } catch(Exception ex){getLogger().warning("Gagal menerapkan kit "+mode+": "+ex.getMessage());}
    }
    private void saveKit(Inventory inv,String mode,Player p) {
        try { File folder=new File(getDataFolder(),"presets/"+mode); if(!folder.exists()&&!folder.mkdirs()) throw new IllegalStateException("Gagal membuat folder");
            ItemStack[] data=new ItemStack[41]; for(int i=0;i<41;i++) data[i]=inv.getItem(i)==null?null:inv.getItem(i).clone(); YamlConfiguration y=new YamlConfiguration(); y.set("contents",Arrays.asList(data)); y.save(new File(folder,"kit.yml")); p.sendMessage("§aKit "+mode+" disimpan.");
        } catch(Exception ex){p.sendMessage("§cGagal menyimpan: "+ex.getMessage());}
    }
    private void importKit(Inventory inv,Player p) {
        for(int i=0;i<36;i++) inv.setItem(i,p.getInventory().getItem(i)==null?null:p.getInventory().getItem(i).clone());
        ItemStack[] armor=p.getInventory().getArmorContents(); for(int i=0;i<4;i++) inv.setItem(36+i,armor[i]==null?null:armor[i].clone()); inv.setItem(40,p.getInventory().getItemInOffHand()==null?null:p.getInventory().getItemInOffHand().clone());
        p.sendMessage("§aInventory, armor, dan offhand berhasil di-import ke editor. Tekan SAVE untuk menyimpan.");
    }
    @EventHandler public void onGuiClick(InventoryClickEvent e) {
        if(!(e.getView().getTopInventory().getHolder() instanceof MenuHolder h)) return; int raw=e.getRawSlot(); if(raw<0||raw>=e.getView().getTopInventory().getSize()) return;
        if(!(e.getWhoClicked() instanceof Player p)) {e.setCancelled(true);return;}
        if(h.type.equals("editor")) {
            if(!p.hasPermission("pvpbotdifficulty.admin")){e.setCancelled(true);p.closeInventory();return;}
            if(raw<45) { if(e.getClick().isShiftClick()) e.setCancelled(true); return; } e.setCancelled(true);
            if(raw==45) {saveKit(e.getView().getTopInventory(),h.mode,p);p.closeInventory();}
            else if(raw==49) importKit(e.getView().getTopInventory(),p);
            else if(raw==51) {File f=new File(new File(getDataFolder(),"presets/"+h.mode),"kit.yml"); if(f.exists()) f.delete(); p.sendMessage("§cKit "+h.mode+" dihapus."); p.closeInventory();}
            else if(raw==53) openModeMenu(p,true); return;
        }
        e.setCancelled(true); String value=display(e.getCurrentItem());
        switch(h.type) {
            case "main" -> { if(raw==10) openModeMenu(p,false); else if(raw==12) {if(p.hasPermission("pvpbotdifficulty.admin")) openModeMenu(p,true); else p.sendMessage("§cKit hanya untuk admin.");} else if(raw==14) {try {if(!p.hasPermission("pvpbotdifficulty.admin")&&ownedBots(p).size()>=2) throw new IllegalArgumentException("Batas 2 bot. Hapus bot lama dulu."); spawnBots(p,1,"native","passive","none",false); }catch(Exception ex){p.sendMessage("§c"+ex.getMessage());}} else if(raw==16) openRemoveMenu(p); }
            case "modes" -> { if(value.equals("kembali")) openMainMenu(p); else if(MODES.contains(value)) openTierMenu(p,value); }
            case "tiers" -> { if(value.equals("kembali")) openModeMenu(p,false); else if(TIERS.contains(value)) openBehaviorMenu(p,value); }
            case "behaviors" -> { if(value.equals("kembali")) openTierMenu(p,h.mode); else if(value.startsWith("arena")) p.sendMessage("§eARENA BELUM JADI."); else if(BEHAVIORS.contains(value)) {try {spawnBots(p,1,selectedTier.get(p.getUniqueId()),value,h.mode,true);p.closeInventory();}catch(Exception ex){p.sendMessage("§c"+ex.getMessage());}} }
            case "kitlist" -> { if(value.equals("kembali")) openMainMenu(p); else if(MODES.contains(value)) {if(p.hasPermission("pvpbotdifficulty.admin")&&e.getClick()==ClickType.SHIFT_RIGHT) openKitEditor(p,value); else p.sendMessage("§7Shift + klik kanan pada mode untuk edit kit.");} }
            case "remove" -> { if(raw==49) openMainMenu(p); else if(raw<45&&e.getCurrentItem()!=null) {String name=ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()); try {if(!p.hasPermission("pvpbotdifficulty.admin")&&!owns(p,name)) throw new IllegalArgumentException("Bukan bot milikmu."); if(removeOne(name)) p.sendMessage("§aBot "+name+" dihapus."); openRemoveMenu(p);}catch(Exception ex){p.sendMessage("§c"+ex.getMessage());}} }
        }
    }
    @EventHandler public void onGuiDrag(InventoryDragEvent e) {
        if(!(e.getView().getTopInventory().getHolder() instanceof MenuHolder h)) return;
        if(!h.type.equals("editor")) {e.setCancelled(true);return;}
        for(int slot:e.getRawSlots()) if(slot>=45) {e.setCancelled(true);return;}
        if(!(e.getWhoClicked() instanceof Player p)||!p.hasPermission("pvpbotdifficulty.admin")) e.setCancelled(true);
    }
    @EventHandler public void onGuiClose(InventoryCloseEvent e) { if(e.getInventory().getHolder() instanceof MenuHolder h && h.type.equals("editor")) editingMode.remove(e.getPlayer().getUniqueId()); }

    private void help(CommandSender sender) {
        sender.sendMessage("§6PvpBotDifficulty §7- per-bot difficulty");
        sender.sendMessage("§e/bott spawn <jumlah> <tier> [perilaku] [mode]\n§e/bott gui");
        sender.sendMessage("§e/bott tier <nama-bot> <tier>");
        sender.sendMessage("§e/bott behavior <nama-bot> <behavior>");
        sender.sendMessage("§e/bott list §7| §e/bott remove <nama> §7| §e/bott tiers");
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return starts(List.of("spawn","gui","tier","set","behavior","list","remove","tiers"), args[0]);
        if (args.length == 2 && (args[0].equalsIgnoreCase("tier") || args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("behavior"))) {
            try { return starts((List<String>) call(botManager(), "all", new Class<?>[0]), args[1]); } catch (Throwable ignored) { return List.of(); }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("spawn")) return starts(TIERS, args[2]);
        if (args.length == 3 && (args[0].equalsIgnoreCase("tier") || args[0].equalsIgnoreCase("set"))) return starts(TIERS, args[2]);
        if (args.length == 3 && args[0].equalsIgnoreCase("behavior")) return starts(BEHAVIORS, args[2]);
        if (args.length == 4 && args[0].equalsIgnoreCase("spawn")) {
            List<String> choices = new ArrayList<>(BEHAVIORS); choices.addAll(MODES);
            return starts(choices, args[3]);
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("spawn")) return starts(MODES, args[4]);
        return List.of();
    }
    private List<String> starts(List<String> values, String prefix) { return values.stream().filter(v -> v.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))).toList(); }
}
