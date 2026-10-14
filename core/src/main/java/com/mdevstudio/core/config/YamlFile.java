package com.mdevstudio.core.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class YamlFile {

    private static final String VERSION_KEY = "config-version";

    private YamlFile() {
    }

    /**
     * Loads a file from the plugin folder, creating it from the bundled copy on first run.
     * When the bundled copy has a higher {@code config-version}, the file is rebuilt from the bundled
     * copy with every value the admin set put back, so new keys arrive in their place with their
     * comments, and keys the admin changed or added are left alone.
     */
    public static YamlConfiguration load(JavaPlugin plugin, String path) {
        File file = new File(plugin.getDataFolder(), path);
        if (!file.exists() && plugin.getResource(path) != null) {
            plugin.saveResource(path, false);
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        YamlConfiguration bundled = readBundled(plugin, path);
        if (bundled == null) {
            return yaml;
        }

        int bundledVersion = bundled.getInt(VERSION_KEY, 0);
        if (bundledVersion > yaml.getInt(VERSION_KEY, 0)) {
            // The upgraded copy is returned, not the old one: reads with an explicit fallback, like
            // getString(path, ""), skip defaults, so a key that exists only in the bundled copy would
            // read as missing until the next start.
            YamlConfiguration upgraded = upgrade(yaml, readBundled(plugin, path), bundledVersion);
            try {
                upgraded.save(file);
            } catch (IOException e) {
                plugin.getSLF4JLogger().error("Could not update {} to version {}", path, bundledVersion, e);
            }
            yaml = upgraded;
        }
        yaml.setDefaults(bundled);
        return yaml;
    }

    static YamlConfiguration upgrade(YamlConfiguration admin, YamlConfiguration bundled, int version) {
        for (String key : admin.getKeys(true)) {
            if (admin.isConfigurationSection(key)) {
                continue;
            }
            boolean ownKey = !bundled.contains(key, true);
            bundled.set(key, admin.get(key));
            if (ownKey) {
                bundled.setComments(key, admin.getComments(key));
            }
            if (ownKey || !admin.getInlineComments(key).isEmpty()) {
                bundled.setInlineComments(key, admin.getInlineComments(key));
            }
        }
        bundled.set(VERSION_KEY, version);
        return bundled;
    }

    public static YamlConfiguration readBundled(JavaPlugin plugin, String path) {
        try (InputStream stream = plugin.getResource(path)) {
            if (stream == null) {
                return null;
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getSLF4JLogger().warn("Could not read bundled {}", path, e);
            return null;
        }
    }
}
