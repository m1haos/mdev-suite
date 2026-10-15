package com.mdevstudio.eventide;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.mdevstudio.core.util.Durations;
import com.mdevstudio.eventide.command.EventideCommand;
import com.mdevstudio.eventide.definition.RegionRule;
import com.mdevstudio.eventide.definition.SurfaceRule;
import com.mdevstudio.eventide.running.RandomSettings;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.slf4j.Logger;

public record EventideSettings(
        String language,
        boolean useClientLanguage,
        ZoneId timezone,
        int maxActive,
        Modules modules,
        MenuMode menu,
        long glowIntervalTicks,
        SurfaceRule spotCheck,
        RegionRule eventRegion,
        int claimMargin,
        Set<String> ignoredWorldGuardRegions,
        RandomSettings random,
        CommandNames commands) {

    // Every system of the plugin can be switched off without touching event files.
    public record Modules(
            boolean scheduler,
            boolean randomEvents,
            boolean countdownBar,
            boolean guards,
            Keys keys,
            boolean leaderboard,
            boolean rewards,
            boolean effects,
            boolean cacheGlow,
            boolean mobGlow,
            boolean mythicMobs,
            boolean fancyNpcs,
            boolean marker,
            boolean stats,
            boolean placeholders,
            boolean spotCheck,
            boolean structures,
            boolean eventRegion,
            boolean claims) {

        static Modules from(ConfigurationSection section) {
            return new Modules(
                    enabled(section, "scheduler"),
                    enabled(section, "random-events"),
                    enabled(section, "countdown-bar"),
                    enabled(section, "guards"),
                    Keys.from(section),
                    enabled(section, "leaderboard"),
                    enabled(section, "rewards"),
                    enabled(section, "effects"),
                    enabled(section, "cache-glow"),
                    enabled(section, "mob-glow"),
                    enabled(section, "mythic-mobs"),
                    enabled(section, "fancy-npcs"),
                    enabled(section, "marker"),
                    enabled(section, "stats"),
                    enabled(section, "placeholders"),
                    enabled(section, "spot-check"),
                    enabled(section, "structures"),
                    enabled(section, "event-region"),
                    enabled(section, "claims"));
        }
    }

    /** Whether caches need keys, and each way a player can get one. */
    public record Keys(boolean enabled, boolean fromGuards, boolean asReward, boolean shop, boolean captain) {

        static Keys from(ConfigurationSection modules) {
            if (modules != null && modules.isBoolean("keys")) {
                boolean on = modules.getBoolean("keys");
                return new Keys(on, on, on, false, on);
            }
            ConfigurationSection keys = modules == null ? null : modules.getConfigurationSection("keys");
            return new Keys(
                    EventideSettings.enabled(keys, "enabled"),
                    EventideSettings.enabled(keys, "from-guards"),
                    EventideSettings.enabled(keys, "as-reward"),
                    keys != null && keys.getBoolean("shop", false),
                    EventideSettings.enabled(keys, "captain"));
        }
    }

    public enum MenuMode { DIALOG, CHAT, OFF }

    /** Other names for /eventide and its subcommands. Read once at startup, when commands are registered. */
    public record CommandNames(List<String> aliases, Map<String, List<String>> subcommands) {

        private static final Pattern NAME = Pattern.compile("[a-z0-9_-]+");

        public List<String> aliasesOf(String subcommand) {
            return subcommands.getOrDefault(subcommand, List.of());
        }

        static CommandNames from(ConfigurationSection section, Logger logger) {
            if (section == null) {
                return new CommandNames(List.of("ev"), Map.of());
            }
            List<String> aliases = names(section.getStringList("aliases"), "commands.aliases", logger).stream()
                    .filter(alias -> !alias.equals(EventideCommand.NAME))
                    .toList();
            Map<String, List<String>> subcommands = new HashMap<>();
            Set<String> taken = new HashSet<>(EventideCommand.SUBCOMMANDS);
            ConfigurationSection subSection = section.getConfigurationSection("subcommands");
            if (subSection != null) {
                for (String subcommand : subSection.getKeys(false)) {
                    if (!EventideCommand.SUBCOMMANDS.contains(subcommand)) {
                        logger.warn("config.yml: commands.subcommands.{}: there is no such subcommand. Known: {}",
                                subcommand, EventideCommand.SUBCOMMANDS);
                        continue;
                    }
                    List<String> own = new ArrayList<>();
                    for (String alias : names(subSection.getStringList(subcommand), "commands.subcommands." + subcommand,
                            logger)) {
                        // Two subcommands answering to one word would make the second one unreachable.
                        if (taken.add(alias)) {
                            own.add(alias);
                        } else {
                            logger.warn("config.yml: commands.subcommands.{}: '{}' is already used by another subcommand",
                                    subcommand, alias);
                        }
                    }
                    subcommands.put(subcommand, List.copyOf(own));
                }
            }
            return new CommandNames(aliases, Map.copyOf(subcommands));
        }

        private static List<String> names(List<String> raw, String path, Logger logger) {
            List<String> names = new ArrayList<>();
            for (String name : raw) {
                String clean = name.trim().toLowerCase(Locale.ROOT);
                if (NAME.matcher(clean).matches()) {
                    names.add(clean);
                } else {
                    logger.warn("config.yml: {}: '{}' may contain only a-z, 0-9, _ and -", path, name);
                }
            }
            return names;
        }
    }

    public static EventideSettings from(YamlConfiguration yaml, Logger logger) {
        ZoneId zone;
        String zoneName = yaml.getString("schedule.timezone", "Europe/Moscow");
        try {
            zone = ZoneId.of(zoneName);
        } catch (DateTimeException e) {
            logger.warn("config.yml: schedule.timezone '{}' is not a valid zone id, using Europe/Moscow", zoneName);
            zone = ZoneId.of("Europe/Moscow");
        }

        MenuMode menu;
        String menuName = yaml.getString("schedule.menu", "dialog");
        try {
            menu = MenuMode.valueOf(menuName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            logger.warn("config.yml: schedule.menu '{}' must be dialog, chat or off; using dialog", menuName);
            menu = MenuMode.DIALOG;
        }

        long glow;
        try {
            glow = Math.max(10, Durations.toTicks(Durations.parse(yaml.getString("visuals.cache-glow-every", "3s"))));
        } catch (IllegalArgumentException e) {
            logger.warn("config.yml: visuals.cache-glow-every: {}; using 3s", e.getMessage());
            glow = 60;
        }

        Modules modules = Modules.from(yaml.getConfigurationSection("modules"));
        Keys keys = modules.keys();
        boolean guardKeys = keys.fromGuards() && modules.guards();
        if (keys.enabled() && !guardKeys && !keys.asReward() && !keys.shop() && !keys.captain()) {
            logger.warn("config.yml: caches need keys, but no key source is on (guards, rewards, shop, captain). "
                    + "Only /eventide givekey can hand them out. Turn modules.keys.enabled off to open caches freely.");
        }

        // Started from an empty ground list: the bundled config always has one, so a missing key cannot clear it.
        SurfaceRule empty = new SurfaceRule(true, 8, 4, 0.9, Set.of());
        SurfaceRule spotCheck;
        try {
            spotCheck = SurfaceRule.parse(yaml.getConfigurationSection("spot-check"), empty);
        } catch (IllegalArgumentException e) {
            logger.warn("config.yml: spot-check.{}; using the default spot check", e.getMessage());
            spotCheck = yaml.getDefaults() == null
                    ? empty
                    : SurfaceRule.parse(yaml.getDefaults().getConfigurationSection("spot-check"), empty);
        }

        RegionRule nothing = new RegionRule(0, 16, 64, Set.of(), Set.of());
        RegionRule eventRegion;
        try {
            eventRegion = RegionRule.parse(yaml.getConfigurationSection("event-region"), nothing);
        } catch (IllegalArgumentException e) {
            logger.warn("config.yml: event-region.{}; using the default region", e.getMessage());
            eventRegion = yaml.getDefaults() == null
                    ? nothing
                    : RegionRule.parse(yaml.getDefaults().getConfigurationSection("event-region"), nothing);
        }

        return new EventideSettings(
                yaml.getString("language", "ru"),
                yaml.getBoolean("use-client-language", true),
                zone,
                Math.max(1, yaml.getInt("schedule.max-active", 1)),
                modules,
                menu,
                glow,
                spotCheck.checked(modules.spotCheck()),
                eventRegion,
                Math.max(0, yaml.getInt("claims.margin", 8)),
                yaml.getStringList("claims.worldguard-ignore").stream()
                        .map(region -> region.trim().toLowerCase(Locale.ROOT))
                        .collect(Collectors.toUnmodifiableSet()),
                RandomSettings.parse(yaml.getConfigurationSection("random-events"), logger),
                CommandNames.from(yaml.getConfigurationSection("commands"), logger));
    }

    private static boolean enabled(ConfigurationSection section, String key) {
        return section == null || section.getBoolean(key, true);
    }
}
