package com.mdevstudio.eventide.definition;

import java.time.Duration;
import java.util.Locale;

import com.mdevstudio.core.util.Durations;
import org.bukkit.configuration.ConfigurationSection;

/** The "random" block of an event file: how the event takes part in random starts. */
public record RandomSpec(String rarity, double weight, Duration cooldown, int minOnline) {

    public static RandomSpec parse(ConfigurationSection section) {
        String rarity = section.getString("rarity", "").trim().toLowerCase(Locale.ROOT);
        if (rarity.isEmpty()) {
            throw new IllegalArgumentException("random.rarity is required, e.g. common");
        }
        double weight = section.getDouble("weight", 1);
        if (weight <= 0) {
            throw new IllegalArgumentException("random.weight must be above 0");
        }
        Duration cooldown;
        try {
            cooldown = Durations.parse(section.getString("cooldown", "0"));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("random.cooldown: " + e.getMessage());
        }
        return new RandomSpec(rarity, weight, cooldown, Math.max(0, section.getInt("min-online", 0)));
    }
}
