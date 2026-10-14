package com.mdevstudio.eventide.running;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mdevstudio.core.util.Durations;
import org.bukkit.configuration.ConfigurationSection;
import org.slf4j.Logger;

/** The random-events section of config.yml. */
public record RandomSettings(
        Duration interval,
        double jitter,
        Duration firstDelay,
        int minOnline,
        int maxConcurrent,
        List<Window> windows,
        Map<String, Tier> tiers) {

    public record Tier(String id, double chance, Duration cooldown, int minOnline) {
    }

    /** A daily time range; one that ends before it starts runs past midnight, 00:00-00:00 is the whole day. */
    public record Window(LocalTime from, LocalTime to) {

        boolean contains(LocalTime time) {
            if (from.equals(to)) {
                return true;
            }
            return from.isBefore(to)
                    ? !time.isBefore(from) && time.isBefore(to)
                    : !time.isBefore(from) || time.isBefore(to);
        }

        static Window parse(String text) {
            String[] parts = text.split("-");
            if (parts.length != 2) {
                throw new IllegalArgumentException("'" + text + "' is not a range like 10:00-23:30");
            }
            try {
                return new Window(time(parts[0]), time(parts[1]));
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("'" + text + "' is not a range like 10:00-23:30");
            }
        }

        private static LocalTime time(String text) {
            String clean = text.trim();
            return LocalTime.parse(clean.length() == 4 ? "0" + clean : clean);
        }
    }

    public boolean allowsTime(LocalTime time) {
        return windows.isEmpty() || windows.stream().anyMatch(window -> window.contains(time));
    }

    public static RandomSettings parse(ConfigurationSection section, Logger logger) {
        if (section == null) {
            return new RandomSettings(Duration.ofMinutes(90), 0.2, Duration.ofMinutes(15), 1, 1, List.of(), Map.of());
        }
        Duration interval = duration(section, "interval", "90m", logger);
        if (interval.toMinutes() < 1) {
            logger.warn("config.yml: random-events.interval must be at least 1m; using 1m");
            interval = Duration.ofMinutes(1);
        }

        double jitter = percent(section.getString("jitter", "20%"), logger);

        List<Window> windows = new ArrayList<>();
        for (String text : section.getStringList("windows")) {
            try {
                windows.add(Window.parse(text));
            } catch (IllegalArgumentException e) {
                logger.warn("config.yml: random-events.windows: {}; this window is skipped", e.getMessage());
            }
        }

        Map<String, Tier> tiers = new LinkedHashMap<>();
        for (Map<?, ?> raw : section.getMapList("rarities")) {
            String id = String.valueOf(raw.get("id")).trim().toLowerCase(Locale.ROOT);
            if (raw.get("id") == null || id.isEmpty()) {
                logger.warn("config.yml: random-events.rarities: an entry has no id and is skipped");
                continue;
            }
            double chance = number(raw.get("chance"), 0);
            if (chance <= 0) {
                logger.warn("config.yml: random-events.rarities.{}: chance must be above 0; the rarity is skipped", id);
                continue;
            }
            Duration cooldown;
            try {
                cooldown = Durations.parse(String.valueOf(raw.get("cooldown") == null ? "0" : raw.get("cooldown")));
            } catch (IllegalArgumentException e) {
                logger.warn("config.yml: random-events.rarities.{}.cooldown: {}; using no cooldown", id, e.getMessage());
                cooldown = Duration.ZERO;
            }
            tiers.put(id, new Tier(id, chance, cooldown, (int) Math.max(0, number(raw.get("min-online"), 0))));
        }

        return new RandomSettings(
                interval,
                jitter,
                duration(section, "first-delay", "15m", logger),
                Math.max(0, section.getInt("min-online", 1)),
                Math.max(1, section.getInt("max-concurrent", 1)),
                List.copyOf(windows),
                Map.copyOf(tiers));
    }

    private static Duration duration(ConfigurationSection section, String key, String fallback, Logger logger) {
        try {
            return Durations.parse(section.getString(key, fallback));
        } catch (IllegalArgumentException e) {
            logger.warn("config.yml: random-events.{}: {}; using {}", key, e.getMessage(), fallback);
            return Durations.parse(fallback);
        }
    }

    // "20%" and "0.2" both mean a fifth of the interval either way.
    private static double percent(String text, Logger logger) {
        String clean = text.trim();
        try {
            double value = clean.endsWith("%")
                    ? Double.parseDouble(clean.substring(0, clean.length() - 1).trim()) / 100
                    : Double.parseDouble(clean);
            return Math.clamp(value, 0, 0.9);
        } catch (NumberFormatException e) {
            logger.warn("config.yml: random-events.jitter '{}' is not a percent like 20%; using 20%", text);
            return 0.2;
        }
    }

    private static double number(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? fallback : Double.parseDouble(value.toString().replace("%", "").trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
