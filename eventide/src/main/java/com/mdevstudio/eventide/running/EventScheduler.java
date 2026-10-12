package com.mdevstudio.eventide.running;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.mdevstudio.eventide.EventidePlugin;
import com.mdevstudio.eventide.definition.EventDefinition;
import org.bukkit.scheduler.BukkitTask;

public final class EventScheduler {

    // Twice a minute, so a lag spike cannot skip a scheduled minute entirely.
    private static final long CHECK_PERIOD_TICKS = 20 * 20;

    private final EventidePlugin plugin;
    private final Map<String, ZonedDateTime> lastStarted = new HashMap<>();
    private BukkitTask task;

    public EventScheduler(EventidePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, CHECK_PERIOD_TICKS, CHECK_PERIOD_TICKS);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public Optional<ZonedDateTime> nextStart(EventDefinition definition) {
        ZonedDateTime now = now();
        return definition.schedule().stream()
                .map(time -> at(now.toLocalDate(), time))
                .map(time -> time.isAfter(now) ? time : time.plusDays(1))
                .min(ZonedDateTime::compareTo);
    }

    private void tick() {
        ZonedDateTime minute = now().truncatedTo(ChronoUnit.MINUTES);
        int online = plugin.getServer().getOnlinePlayers().size();
        for (EventDefinition definition : plugin.definitions().values()) {
            boolean due = definition.schedule().stream().anyMatch(time -> at(minute.toLocalDate(), time).equals(minute));
            if (!due || minute.equals(lastStarted.get(definition.id()))) {
                continue;
            }
            lastStarted.put(definition.id(), minute);
            if (online < definition.minOnline()) {
                plugin.getSLF4JLogger().info("Skipped scheduled {}: {} online, {} required",
                        definition.id(), online, definition.minOnline());
                continue;
            }
            plugin.manager().start(definition, reason -> { });
        }
    }

    private ZonedDateTime now() {
        return ZonedDateTime.now(plugin.settings().timezone());
    }

    private ZonedDateTime at(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(plugin.settings().timezone()).truncatedTo(ChronoUnit.MINUTES);
    }
}
