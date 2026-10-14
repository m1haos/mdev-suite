package com.mdevstudio.core.effect;

import com.mdevstudio.core.util.Durations;
import org.bukkit.configuration.ConfigurationSection;

/** When an element plays inside its effect, in ticks: {@code delay}, {@code duration}, {@code interval}. */
public record Timing(int delay, int duration, int interval) {

    static Timing parse(ConfigurationSection section, int defaultDuration) {
        return new Timing(
                ticks(section, "delay", 0),
                Math.max(1, ticks(section, "duration", defaultDuration)),
                Math.max(1, ticks(section, "interval", 1)));
    }

    int end() {
        return delay + duration;
    }

    // -1 when the element should not draw on this tick of the whole effect.
    int localTick(int effectTick) {
        int local = effectTick - delay;
        if (local < 0 || local >= duration || local % interval != 0) {
            return -1;
        }
        return local;
    }

    private static int ticks(ConfigurationSection section, String key, int fallback) {
        if (!section.contains(key)) {
            return fallback;
        }
        Object value = section.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return (int) Durations.toTicks(Durations.parse(String.valueOf(value)));
    }
}
