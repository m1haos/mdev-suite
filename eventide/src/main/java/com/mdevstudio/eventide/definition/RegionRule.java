package com.mdevstudio.eventide.definition;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.bukkit.configuration.ConfigurationSection;

/**
 * The area around a running event where players and the world are limited. {@code radius} 0 means
 * the event area plus {@link #AUTO_MARGIN}.
 */
public record RegionRule(int radius, int below, int above, Set<RegionFlag> deny, Set<String> blockedCommands) {

    // Caches and guards land up to about 24 blocks from the spot; a region that stops short of them protects nothing.
    private static final int AUTO_MARGIN = 16;

    public RegionRule {
        deny = Collections.unmodifiableSet(deny.isEmpty() ? EnumSet.noneOf(RegionFlag.class) : EnumSet.copyOf(deny));
        blockedCommands = Set.copyOf(blockedCommands);
    }

    /** Keys the same as {@code event-region} in config.yml; missing keys keep the values of {@code base}. */
    public static RegionRule parse(ConfigurationSection section, RegionRule base) {
        if (section == null) {
            return base;
        }
        Set<RegionFlag> deny = base.deny();
        if (section.contains("deny")) {
            deny = EnumSet.noneOf(RegionFlag.class);
            for (String name : section.getStringList("deny")) {
                try {
                    deny.add(RegionFlag.parse(name));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("deny: " + e.getMessage());
                }
            }
        }
        Set<String> commands = base.blockedCommands();
        if (section.contains("blocked-commands")) {
            commands = commandNames(section.getStringList("blocked-commands"));
        }
        return new RegionRule(
                Math.max(0, section.getInt("radius", base.radius())),
                Math.max(0, section.getInt("below", base.below())),
                Math.max(0, section.getInt("above", base.above())),
                deny,
                commands);
    }

    public RegionRule resolve(int areaRadius) {
        return radius > 0 ? this : new RegionRule(areaRadius + AUTO_MARGIN, below, above, deny, blockedCommands);
    }

    /** The same area with nothing forbidden, for when the event-region module is off. */
    public RegionRule permissive() {
        return new RegionRule(radius, below, above, Set.of(), Set.of());
    }

    public boolean blocks(String command) {
        return blockedCommands.contains(command);
    }

    private static Set<String> commandNames(List<String> raw) {
        Set<String> names = new LinkedHashSet<>();
        for (String name : raw) {
            String clean = name.trim().toLowerCase(Locale.ROOT);
            names.add(clean.startsWith("/") ? clean.substring(1) : clean);
        }
        return names;
    }
}
