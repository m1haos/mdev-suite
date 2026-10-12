package com.mdevstudio.core.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The core database: SQLite in the plugin folder or MySQL/MariaDB. All work runs on one thread
 * with one connection, which is plenty for counters and keeps SQLite free of lock errors.
 * The JDBC drivers are the ones Paper already ships.
 */
public final class Database {

    private static final int VALIDATION_TIMEOUT_SECONDS = 2;

    private final ExecutorService thread;
    private final Dialect dialect;
    private final String url;
    private final String user;
    private final String password;
    private final String tablePrefix;
    private Connection connection;

    private Database(Dialect dialect, String url, String user, String password, String tablePrefix) {
        this.dialect = dialect;
        this.url = url;
        this.user = user;
        this.password = password;
        this.tablePrefix = tablePrefix;
        this.thread = Executors.newSingleThreadExecutor(runnable -> {
            Thread worker = new Thread(runnable, "MDevCore-DB");
            worker.setDaemon(true);
            return worker;
        });
    }

    public static Database fromConfig(JavaPlugin plugin, ConfigurationSection section) {
        String type = section == null ? "sqlite" : section.getString("type", "sqlite").toLowerCase(Locale.ROOT);
        String prefix = section == null ? "mdev_" : section.getString("table-prefix", "mdev_");
        if (!prefix.matches("[A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("storage.table-prefix may contain only letters, digits and _");
        }
        return switch (type) {
            case "sqlite" -> {
                loadDriver("org.sqlite.JDBC");
                File file = new File(plugin.getDataFolder(), "data.db");
                yield new Database(Dialect.SQLITE, "jdbc:sqlite:" + file.getAbsolutePath(), null, null, prefix);
            }
            case "mysql", "mariadb" -> {
                loadDriver("com.mysql.cj.jdbc.Driver");
                ConfigurationSection mysql = section.getConfigurationSection("mysql");
                if (mysql == null) {
                    throw new IllegalArgumentException("storage.mysql is required for type mysql");
                }
                String options = mysql.getString("options", "");
                String url = "jdbc:mysql://" + mysql.getString("host", "localhost") + ":" + mysql.getInt("port", 3306)
                        + "/" + mysql.getString("database", "minecraft")
                        + "?connectTimeout=5000" + (options.isBlank() ? "" : "&" + options);
                yield new Database(Dialect.MYSQL, url, mysql.getString("user", "root"),
                        mysql.getString("password", ""), prefix);
            }
            default -> throw new IllegalArgumentException("storage.type must be sqlite or mysql, not '" + type + "'");
        };
    }

    public Dialect dialect() {
        return dialect;
    }

    /** Table name with the configured prefix, e.g. {@code table("stats")} gives {@code mdev_stats}. */
    public String table(String name) {
        return tablePrefix + name;
    }

    /** Runs the work on the database thread. The future fails with the {@link SQLException} if it throws. */
    public <T> CompletableFuture<T> submit(SqlWork<T> work) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return work.run(connection());
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, thread);
    }

    /** Waits for queued writes, then closes the connection. Call from onDisable. */
    public void close() {
        thread.shutdown();
        try {
            thread.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // Closing on shutdown; there is nobody left to report to.
            }
        }
    }

    // MySQL drops idle connections after wait_timeout, so a connection that sat unused for a
    // night is checked and reopened instead of failing the next query.
    private Connection connection() throws SQLException {
        if (connection == null || !connection.isValid(VALIDATION_TIMEOUT_SECONDS)) {
            connection = user == null ? DriverManager.getConnection(url) : DriverManager.getConnection(url, user, password);
        }
        return connection;
    }

    private static void loadDriver(String className) {
        try {
            Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("JDBC driver " + className + " is missing from the server", e);
        }
    }
}
