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
     * When the bundled copy has a higher {@code config-version}, missing keys are written into
     * the admin's file with their default values; keys the admin changed are left alone.
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
        yaml.setDefaults(bundled);

        int bundledVersion = bundled.getInt(VERSION_KEY, 0);
        if (bundledVersion > yaml.getInt(VERSION_KEY, 0)) {
            yaml.options().copyDefaults(true);
            yaml.set(VERSION_KEY, bundledVersion);
            try {
                yaml.save(file);
            } catch (IOException e) {
                plugin.getSLF4JLogger().error("Could not update {} to version {}", path, bundledVersion, e);
            }
        }
        return yaml;
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
