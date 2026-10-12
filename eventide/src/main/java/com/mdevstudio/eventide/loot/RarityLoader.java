package com.mdevstudio.eventide.loot;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.mdevstudio.core.item.ItemSpec;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class RarityLoader {

    private static final String FILE = "rarities.yml";
    // The id ends up in commands (/eventide givekey <player> <rarity>), so it must be typeable.
    private static final Pattern ID = Pattern.compile("[a-z0-9_-]+");

    private RarityLoader() {
    }

    // A broken rarity is reported and skipped; the rest still load. Not a versioned config on purpose:
    // merging bundled defaults would bring back rarities the admin deleted.
    public static Map<String, Rarity> load(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), FILE);
        if (!file.exists()) {
            plugin.saveResource(FILE, false);
        }
        ConfigurationSection section = YamlConfiguration.loadConfiguration(file).getConfigurationSection("rarities");
        Map<String, Rarity> rarities = new LinkedHashMap<>();
        if (section == null) {
            plugin.getSLF4JLogger().error("{}: 'rarities' section is missing, no caches or keys will work", FILE);
            return rarities;
        }
        for (String id : section.getKeys(false)) {
            try {
                if (!ID.matcher(id).matches()) {
                    throw new IllegalArgumentException("the id may contain only a-z, 0-9, _ and -");
                }
                rarities.put(id, parse(id, section.getConfigurationSection(id)));
            } catch (IllegalArgumentException e) {
                plugin.getSLF4JLogger().error("{}: {}: {}", FILE, id, e.getMessage());
            }
        }
        return Collections.unmodifiableMap(rarities);
    }

    private static Rarity parse(String id, ConfigurationSection section) {
        if (section == null) {
            throw new IllegalArgumentException("must be a section with name, block and key");
        }
        return new Rarity(
                id,
                section.getString("name", id),
                block(section.getString("block", "barrel")),
                section.getInt("points", 1),
                section.getString("effects.idle"),
                section.getString("effects.open"),
                parseKey(section));
    }

    // "key: false" or no key section: caches of this rarity open with a click.
    private static Rarity.KeySpec parseKey(ConfigurationSection rarity) {
        ConfigurationSection key = rarity.getConfigurationSection("key");
        if (key == null) {
            return null;
        }
        if (!key.contains("item")) {
            throw new IllegalArgumentException("key.item is required, e.g. {material: tripwire_hook, name: ...}");
        }
        Set<String> alsoOpens = key.getStringList("also-opens").stream()
                .map(other -> other.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        return new Rarity.KeySpec(
                ItemSpec.parse(key, "item"),
                Math.max(0, key.getDouble("price", 0)),
                key.getBoolean("consume", true),
                alsoOpens);
    }

    static Material block(String name) {
        Material block = Material.matchMaterial(name);
        if (block == null || !block.isBlock() || block.isAir()) {
            throw new IllegalArgumentException("block '" + name + "' is not a placeable block");
        }
        return block;
    }
}
