package com.mdevstudio.eventide.hook;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

/**
 * Claimed land from every supported protection plugin that is installed. Each one is looked up by
 * name first, so its classes are only loaded on servers that have it.
 */
public final class Claims {

    private final List<ClaimSource> sources;

    private Claims(List<ClaimSource> sources) {
        this.sources = sources;
    }

    public static Claims detect(Plugin plugin, Set<String> ignoredWorldGuardRegions) {
        PluginManager plugins = plugin.getServer().getPluginManager();
        List<ClaimSource> sources = new ArrayList<>();
        if (plugins.isPluginEnabled("WorldGuard")) {
            sources.add(new WorldGuardClaims(ignoredWorldGuardRegions));
        }
        if (plugins.isPluginEnabled("GriefPrevention")) {
            sources.add(new GriefPreventionClaims());
        }
        if (plugins.isPluginEnabled("Lands")) {
            sources.add(new LandsClaims(plugin));
        }
        if (plugins.isPluginEnabled("Towny")) {
            sources.add(new TownyClaims());
        }
        return new Claims(List.copyOf(sources));
    }

    public static Claims none() {
        return new Claims(List.of());
    }

    public List<String> plugins() {
        return sources.stream().map(ClaimSource::plugin).toList();
    }

    /** Which plugin has land claimed within {@code radius} blocks of the spot, or null if none does. */
    public String claimedBy(Location spot, int radius) {
        for (ClaimSource source : sources) {
            if (source.claimed(spot.getWorld(), spot.getBlockX() - radius, spot.getBlockZ() - radius,
                    spot.getBlockX() + radius, spot.getBlockZ() + radius)) {
                return source.plugin();
            }
        }
        return null;
    }
}
