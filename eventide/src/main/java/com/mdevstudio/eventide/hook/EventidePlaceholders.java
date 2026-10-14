package com.mdevstudio.eventide.hook;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mdevstudio.core.MDevCore;
import com.mdevstudio.core.stats.StatEntry;
import com.mdevstudio.core.util.Durations;
import com.mdevstudio.eventide.EventidePlugin;
import com.mdevstudio.eventide.definition.EventDefinition;
import com.mdevstudio.eventide.running.ActiveEvent;
import com.mdevstudio.eventide.running.EventStats;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

/**
 * {@code %eventide_...%} placeholders. Scoreboard and tab plugins ask for them from their own
 * threads, so the answers come from a snapshot taken on the main thread every second and from
 * the statistics cache of the core, never from live event state or the database.
 */
public final class EventidePlaceholders extends PlaceholderExpansion {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    // Scoreboard and tab plugins that read placeholders expect section-sign colors, not components.
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final Map<String, String> STATS = Map.of(
            "wins", EventStats.WINS,
            "played", EventStats.PLAYED,
            "total_points", EventStats.POINTS,
            "caches", EventStats.CACHES,
            "airdrops", EventStats.AIRDROPS,
            "bosses", EventStats.BOSSES);
    private static final Map<String, String> TOPS = Map.of(
            "wins", EventStats.WINS,
            "points", EventStats.POINTS);

    private record Next(String name, String id, ZonedDateTime at) {
    }

    private record Active(String name, String id, String world, int x, int y, int z, String leader, int leaderPoints) {
    }

    private record Snapshot(Next next, Active active, Map<UUID, Integer> points, Duration nextRandom) {
    }

    private final EventidePlugin plugin;
    private volatile Snapshot snapshot = new Snapshot(null, null, Map.of(), null);

    private EventidePlaceholders(EventidePlugin plugin) {
        this.plugin = plugin;
    }

    public static void register(EventidePlugin plugin) {
        EventidePlaceholders expansion = new EventidePlaceholders(plugin);
        plugin.getServer().getScheduler().runTaskTimer(plugin, expansion::takeSnapshot, 0, 20);
        expansion.register();
    }

    @Override
    public @NotNull String getIdentifier() {
        return "eventide";
    }

    @Override
    public @NotNull String getAuthor() {
        return "M/Dev Studio";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        String key = params.toLowerCase(Locale.ROOT);
        Snapshot current = snapshot;
        if (key.equals("next_random")) {
            return current.nextRandom() == null ? "" : Durations.clock(current.nextRandom());
        }
        if (key.startsWith("next")) {
            return next(key, current.next());
        }
        if (key.startsWith("active") || key.startsWith("leader")) {
            return active(key, current.active());
        }
        if (key.startsWith("top_")) {
            return top(key);
        }
        if (key.equals("points")) {
            return player == null ? "0" : Integer.toString(current.points().getOrDefault(player.getUniqueId(), 0));
        }
        String stat = STATS.get(key);
        if (stat != null) {
            return player == null ? "0" : MDevCore.stats()
                    .map(stats -> Long.toString(stats.cached(player.getUniqueId(), stat)))
                    .orElse("0");
        }
        return null;
    }

    private void takeSnapshot() {
        Next next = null;
        for (EventDefinition definition : plugin.definitions().values()) {
            Optional<ZonedDateTime> at = plugin.scheduler().nextStart(definition);
            if (at.isPresent() && (next == null || at.get().isBefore(next.at()))) {
                next = new Next(legacy(definition.displayName()), definition.id(), at.get());
            }
        }

        Active active = null;
        Map<UUID, Integer> points = new HashMap<>();
        for (ActiveEvent event : plugin.manager().active()) {
            if (active == null) {
                Location at = event.location();
                var leader = event.score().leader();
                active = new Active(legacy(event.definition().displayName()), event.definition().id(),
                        at.getWorld().getName(), at.getBlockX(), at.getBlockY(), at.getBlockZ(),
                        leader.map(entry -> nameOf(entry.getKey())).orElse(""),
                        leader.map(Map.Entry::getValue).orElse(0));
            }
            event.score().ranking().forEach(entry -> points.merge(entry.getKey(), entry.getValue(), Integer::sum));
        }
        snapshot = new Snapshot(next, active, Map.copyOf(points), plugin.randomEvents().untilNextRoll().orElse(null));
    }

    private static String next(String key, Next next) {
        if (next == null) {
            return "";
        }
        return switch (key) {
            case "next" -> next.name();
            case "next_id" -> next.id();
            case "next_time" -> TIME.format(next.at());
            case "next_in" -> Durations.clock(Duration.between(ZonedDateTime.now(next.at().getZone()), next.at()));
            default -> null;
        };
    }

    private static String active(String key, Active active) {
        if (active == null) {
            return key.endsWith("points") ? "0" : "";
        }
        return switch (key) {
            case "active" -> active.name();
            case "active_id" -> active.id();
            case "active_world" -> active.world();
            case "active_x" -> Integer.toString(active.x());
            case "active_y" -> Integer.toString(active.y());
            case "active_z" -> Integer.toString(active.z());
            case "leader" -> active.leader();
            case "leader_points" -> Integer.toString(active.leaderPoints());
            default -> null;
        };
    }

    // top_<wins|points>_<place>_<name|value>
    private static String top(String key) {
        String[] parts = key.split("_");
        if (parts.length != 4 || !TOPS.containsKey(parts[1])) {
            return null;
        }
        int place;
        try {
            place = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return null;
        }
        List<StatEntry> top = MDevCore.stats().map(stats -> stats.cachedTop(TOPS.get(parts[1]))).orElse(List.of());
        if (place < 1 || place > top.size()) {
            return parts[3].equals("value") ? "0" : "";
        }
        StatEntry entry = top.get(place - 1);
        return switch (parts[3]) {
            case "name" -> entry.name();
            case "value" -> Long.toString(entry.value());
            default -> null;
        };
    }

    private String legacy(String miniMessage) {
        return LEGACY.serialize(plugin.text().parse(miniMessage));
    }

    private static String nameOf(UUID id) {
        return Optional.ofNullable(Bukkit.getOfflinePlayer(id).getName()).orElse("?");
    }
}
