package com.mdevstudio.eventide.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Called after an event ended. A completed event has handed out its rewards; a stopped one
 * ({@code /eventide stop}, server shutdown) gave none.
 */
public final class EventideFinishEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String eventId;
    private final Location location;
    private final List<Map.Entry<UUID, Integer>> ranking;
    private final boolean completed;

    public EventideFinishEvent(String eventId, Location location, List<Map.Entry<UUID, Integer>> ranking,
            boolean completed) {
        this.eventId = eventId;
        this.location = location;
        this.ranking = List.copyOf(ranking);
        this.completed = completed;
    }

    public boolean completed() {
        return completed;
    }

    public String eventId() {
        return eventId;
    }

    public Location location() {
        return location.clone();
    }

    /** Players and their points, best first. Empty when nobody took part. */
    public List<Map.Entry<UUID, Integer>> ranking() {
        return ranking;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
