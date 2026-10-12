package com.mdevstudio.core.effect;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.mdevstudio.core.util.Sections;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Every effect from {@code plugins/MDevCore/effects/*.yml}, shared by all studio plugins.
 * Plugins refer to effects by id, so admins restyle everything in one place.
 */
public final class EffectLibrary {

    private static final List<String> BUNDLED = List.of("effects/default.yml");

    private final JavaPlugin plugin;
    private final Map<String, Effect> effects;
    private final boolean enabled;
    private final double density;
    private final Set<String> reportedMissing = new HashSet<>();

    private EffectLibrary(JavaPlugin plugin, Map<String, Effect> effects, boolean enabled, double density) {
        this.plugin = plugin;
        this.effects = effects;
        this.enabled = enabled;
        this.density = density;
    }

    public static EffectLibrary load(JavaPlugin plugin, ConfigurationSection settings) {
        File folder = new File(plugin.getDataFolder(), "effects");
        if (!folder.exists()) {
            BUNDLED.forEach(path -> plugin.saveResource(path, false));
        }

        // Presets from the jar come first and the files on disk override them by name. This way an
        // update brings its new presets to servers whose effects folder was saved long ago.
        Map<String, Effect> effects = new TreeMap<>();
        for (String path : BUNDLED) {
            try (var reader = new InputStreamReader(plugin.getResource(path), StandardCharsets.UTF_8)) {
                readInto(effects, YamlConfiguration.loadConfiguration(reader), path, plugin);
            } catch (IOException e) {
                plugin.getSLF4JLogger().error("Could not read bundled {}", path, e);
            }
        }
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                readInto(effects, YamlConfiguration.loadConfiguration(file), "effects/" + file.getName(), plugin);
            }
        }
        boolean enabled = settings == null || settings.getBoolean("enabled", true);
        double density = settings == null ? 1 : Math.clamp(settings.getDouble("density", 1), 0.1, 3);
        return new EffectLibrary(plugin, effects, enabled, density);
    }

    private static void readInto(Map<String, Effect> effects, YamlConfiguration yaml, String source, JavaPlugin plugin) {
        for (String id : yaml.getKeys(false)) {
            try {
                effects.put(id.toLowerCase(Locale.ROOT), parse(id, yaml.getMapList(id)));
            } catch (IllegalArgumentException e) {
                plugin.getSLF4JLogger().error("{}: {}: {}", source, id, e.getMessage());
            }
        }
    }

    private static Effect parse(String id, List<Map<?, ?>> rawElements) {
        if (rawElements.isEmpty()) {
            throw new IllegalArgumentException("must be a list of elements");
        }
        List<EffectElement> elements = new ArrayList<>();
        for (int i = 0; i < rawElements.size(); i++) {
            try {
                elements.add(EffectElement.parse(Sections.fromMap(rawElements.get(i))));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("element " + (i + 1) + ": " + e.getMessage(), e);
            }
        }
        return new Effect(id, elements);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean has(String id) {
        return effects.containsKey(id.toLowerCase(Locale.ROOT));
    }

    public Set<String> ids() {
        return Collections.unmodifiableSet(effects.keySet());
    }

    /** Plays the effect at {@code at}. Does nothing when effects are switched off or the id is empty. */
    public void play(String id, Location at) {
        play(id, at, 1);
    }

    /** Plays the effect with its shapes stretched {@code scale} times, e.g. to outline a zone of that radius. */
    public void play(String id, Location at, double scale) {
        if (!enabled || id == null || id.isBlank() || at.getWorld() == null) {
            return;
        }
        Effect effect = effects.get(id.toLowerCase(Locale.ROOT));
        if (effect == null) {
            if (reportedMissing.add(id)) {
                plugin.getSLF4JLogger().warn("Effect '{}' is used but not defined in MDevCore/effects", id);
            }
            return;
        }
        EffectCanvas canvas = new EffectCanvas(plugin, at.clone(), density, Math.max(0.1, scale));
        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                for (EffectElement element : effect.elements()) {
                    int local = element.timing().localTick(tick);
                    if (local >= 0) {
                        element.draw(canvas, local);
                    }
                }
                if (++tick >= effect.length()) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0, 1);
    }
}
