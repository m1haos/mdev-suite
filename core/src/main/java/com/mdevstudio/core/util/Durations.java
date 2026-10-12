package com.mdevstudio.core.util;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Durations written the way admins write them: {@code 90}, {@code 30s}, {@code 5m}, {@code 1h30m},
 * or game ticks for short animations: {@code 40t}.
 */
public final class Durations {

    private static final Pattern PART = Pattern.compile("(\\d+)\\s*([hmst]?)");

    private Durations() {
    }

    public static Duration parse(String input) {
        String value = input.trim().toLowerCase(Locale.ROOT);
        Matcher matcher = PART.matcher(value);
        Duration total = Duration.ZERO;
        int consumed = 0;
        while (matcher.find()) {
            if (matcher.start() != consumed) {
                throw new IllegalArgumentException("Unexpected duration '" + input + "'");
            }
            long amount = Long.parseLong(matcher.group(1));
            total = total.plus(switch (matcher.group(2)) {
                case "h" -> Duration.ofHours(amount);
                case "m" -> Duration.ofMinutes(amount);
                case "t" -> Duration.ofMillis(amount * 50);
                default -> Duration.ofSeconds(amount);
            });
            consumed = matcher.end();
            while (consumed < value.length() && value.charAt(consumed) == ' ') {
                consumed++;
            }
        }
        if (consumed != value.length() || value.isEmpty()) {
            throw new IllegalArgumentException("Unexpected duration '" + input + "'");
        }
        return total;
    }

    public static long toTicks(Duration duration) {
        return duration.toMillis() / 50;
    }

    /** {@code 4:05} or {@code 1:02:03}, for countdowns. */
    public static String clock(Duration duration) {
        long seconds = Math.max(0, duration.toSeconds());
        long hours = seconds / 3600;
        long minutes = seconds % 3600 / 60;
        long rest = seconds % 60;
        return hours > 0
                ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, rest)
                : String.format(Locale.ROOT, "%d:%02d", minutes, rest);
    }
}
