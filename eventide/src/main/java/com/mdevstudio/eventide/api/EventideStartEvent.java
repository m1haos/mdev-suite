package com.mdevstudio.eventide.api;

import org.bukkit.Location;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Called when an event has found its spot and is about to begin. Cancel it to keep the event from starting. */
public final class EventideStartEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String eventId;
    private final Location location;
    private boolean cancelled;

    public EventideStartEvent(String eventId, Location location) {
        this.eventId = eventId;
        this.location = location;
    }

    /** The file name of the event without {@code .yml}. */
    public String eventId() {
        return eventId;
    }

    public Location location() {
        return location.clone();
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
