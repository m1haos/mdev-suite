package com.mdevstudio.eventide.running;

import java.util.UUID;

import com.mdevstudio.core.MDevCore;
import com.mdevstudio.eventide.EventidePlugin;

/** Names of the counters Eventide keeps in the core database, and the one way to add to them. */
public final class EventStats {

    public static final String PLAYED = "eventide.played";
    public static final String WINS = "eventide.wins";
    public static final String POINTS = "eventide.points";
    public static final String CACHES = "eventide.caches";
    public static final String AIRDROPS = "eventide.airdrops";
    public static final String BOSSES = "eventide.bosses";

    private EventStats() {
    }

    public static void add(EventidePlugin plugin, UUID player, String stat, long delta) {
        if (plugin.settings().modules().stats()) {
            MDevCore.stats().ifPresent(stats -> stats.add(player, stat, delta));
        }
    }
}
