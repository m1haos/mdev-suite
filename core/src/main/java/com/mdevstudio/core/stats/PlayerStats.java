package com.mdevstudio.core.stats;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;

import com.mdevstudio.core.storage.Database;
import com.mdevstudio.core.storage.Dialect;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Named counters per player, such as {@code eventide.wins}. Values of online players and the
 * leaderboards registered with {@link #trackTop} are kept in memory, so placeholders and menus
 * read them on the main thread without waiting for the database.
 */
public final class PlayerStats {

    private static final Pattern STAT_NAME = Pattern.compile("[a-z0-9_.-]{1,64}");

    private final JavaPlugin plugin;
    private final Database database;
    private final String players;
    private final String stats;
    private final Executor mainThread;
    private final Map<UUID, Map<String, Long>> online = new ConcurrentHashMap<>();
    private final Map<String, Integer> trackedTops = new ConcurrentHashMap<>();
    private final Map<String, List<StatEntry>> tops = new ConcurrentHashMap<>();

    public PlayerStats(JavaPlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
        this.players = database.table("players");
        this.stats = database.table("stats");
        this.mainThread = task -> plugin.getServer().getScheduler().runTask(plugin, task);
    }

    /** Creates missing tables. Called once by the core before {@link #startCaching}. */
    public CompletableFuture<Void> createTables() {
        return database.submit(connection -> {
            boolean mysql = database.dialect() == Dialect.MYSQL;
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS " + players + " ("
                        + "uuid CHAR(36) NOT NULL PRIMARY KEY, "
                        + "name VARCHAR(16) NOT NULL, "
                        + "last_seen BIGINT NOT NULL)");
                statement.execute("CREATE TABLE IF NOT EXISTS " + stats + " ("
                        + "uuid CHAR(36) NOT NULL, "
                        + "stat VARCHAR(64) NOT NULL, "
                        + "value BIGINT NOT NULL, "
                        + "PRIMARY KEY (uuid, stat)"
                        + (mysql ? ", INDEX " + stats + "_top (stat, value)" : "") + ")");
                if (!mysql) {
                    statement.execute("CREATE INDEX IF NOT EXISTS " + stats + "_top ON " + stats + " (stat, value)");
                }
            }
            return null;
        });
    }

    /** Starts loading the counters of players as they join and refreshing tracked leaderboards. */
    public void startCaching(long topRefreshTicks) {
        plugin.getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onJoin(PlayerJoinEvent event) {
                load(event.getPlayer());
            }

            @EventHandler
            public void onQuit(PlayerQuitEvent event) {
                online.remove(event.getPlayer().getUniqueId());
            }
        }, plugin);
        plugin.getServer().getOnlinePlayers().forEach(this::load);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::refreshTops, topRefreshTicks, topRefreshTicks);
    }

    /** Adds {@code delta} (may be negative) to the counter. The cache updates at once, the database shortly after. */
    public CompletableFuture<Void> add(UUID player, String stat, long delta) {
        checkName(stat);
        Map<String, Long> cached = online.get(player);
        if (cached != null) {
            cached.merge(stat, delta, Long::sum);
        }
        Dialect dialect = database.dialect();
        String sql = "INSERT INTO " + stats + " (uuid, stat, value) VALUES (?, ?, ?) "
                + dialect.upsert("uuid, stat", "value = value + " + dialect.incoming("value"));
        return report(database.submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, player.toString());
                statement.setString(2, stat);
                statement.setLong(3, delta);
                statement.executeUpdate();
            }
            return null;
        }), "add to " + stat);
    }

    /** The cached value for an online player; 0 while it is still loading or for offline players. */
    public long cached(UUID player, String stat) {
        Map<String, Long> cached = online.get(player);
        return cached == null ? 0 : cached.getOrDefault(stat, 0L);
    }

    public CompletableFuture<Long> get(UUID player, String stat) {
        return database.submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT value FROM " + stats + " WHERE uuid = ? AND stat = ?")) {
                statement.setString(1, player.toString());
                statement.setString(2, stat);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? rows.getLong(1) : 0L;
                }
            }
        });
    }

    public CompletableFuture<List<StatEntry>> top(String stat, int limit) {
        return database.submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT s.uuid, p.name, s.value FROM " + stats + " s LEFT JOIN " + players
                            + " p ON p.uuid = s.uuid WHERE s.stat = ? ORDER BY s.value DESC LIMIT ?")) {
                statement.setString(1, stat);
                statement.setInt(2, limit);
                List<StatEntry> entries = new ArrayList<>();
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        entries.add(new StatEntry(UUID.fromString(rows.getString(1)),
                                Objects.requireNonNullElse(rows.getString(2), "?"), rows.getLong(3)));
                    }
                }
                return entries;
            }
        });
    }

    /** Keeps the top {@code size} of the stat in memory for {@link #cachedTop}. */
    public void trackTop(String stat, int size) {
        checkName(stat);
        trackedTops.merge(stat, size, Math::max);
        refreshTop(stat, trackedTops.get(stat));
    }

    /** The last loaded leaderboard of a tracked stat, best first. Empty until the first load. */
    public List<StatEntry> cachedTop(String stat) {
        return tops.getOrDefault(stat, List.of());
    }

    /** Reloads tracked leaderboards now, e.g. right after an event handed out its results. */
    public void refreshTops() {
        trackedTops.forEach(this::refreshTop);
    }

    private void refreshTop(String stat, int size) {
        report(top(stat, size).thenAccept(entries -> tops.put(stat, List.copyOf(entries))), "load top of " + stat);
    }

    private void load(Player player) {
        UUID id = player.getUniqueId();
        String name = player.getName();
        // Counters added while the query runs land in this map and are kept: the query was queued
        // before them, so the loaded values do not include them yet.
        Map<String, Long> cache = new ConcurrentHashMap<>();
        online.put(id, cache);
        Dialect dialect = database.dialect();
        String upsert = "INSERT INTO " + players + " (uuid, name, last_seen) VALUES (?, ?, ?) "
                + dialect.upsert("uuid", "name = " + dialect.incoming("name")
                + ", last_seen = " + dialect.incoming("last_seen"));
        report(database.submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(upsert)) {
                statement.setString(1, id.toString());
                statement.setString(2, name);
                statement.setLong(3, System.currentTimeMillis());
                statement.executeUpdate();
            }
            Map<String, Long> values = new HashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT stat, value FROM " + stats + " WHERE uuid = ?")) {
                statement.setString(1, id.toString());
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        values.put(rows.getString(1), rows.getLong(2));
                    }
                }
            }
            return values;
        }).thenAcceptAsync(values -> {
            // A player who left, or left and came back, has another map or none; this result is stale.
            if (online.get(id) == cache) {
                values.forEach((stat, value) -> cache.merge(stat, value, Long::sum));
            }
        }, mainThread), "load stats of " + name);
    }

    private <T> CompletableFuture<T> report(CompletableFuture<T> future, String what) {
        return future.whenComplete((result, error) -> {
            if (error != null) {
                Throwable cause = error.getCause() instanceof SQLException sql ? sql : error;
                plugin.getSLF4JLogger().error("Database: could not {}: {}", what, cause.getMessage());
            }
        });
    }

    private static void checkName(String stat) {
        if (!STAT_NAME.matcher(stat).matches()) {
            throw new IllegalArgumentException("Stat name '" + stat + "' may contain only a-z, 0-9, _ . - (up to 64)");
        }
    }
}
