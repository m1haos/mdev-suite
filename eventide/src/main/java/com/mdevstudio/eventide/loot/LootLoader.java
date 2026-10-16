package com.mdevstudio.eventide.loot;

import java.io.File;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mdevstudio.core.item.ItemSpec;
import com.mdevstudio.core.reward.RewardRegistry;
import com.mdevstudio.core.util.Sections;
import com.mdevstudio.core.util.WeightedPool;
import com.mdevstudio.eventide.EventidePlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

public final class LootLoader {

    private static final List<String> EXAMPLES = List.of("loot/default.yml");

    private LootLoader() {
    }

    public static Map<String, LootTable> loadAll(EventidePlugin plugin, Map<String, Rarity> rarities) {
        File folder = new File(plugin.getDataFolder(), "loot");
        if (!folder.exists()) {
            EXAMPLES.forEach(path -> plugin.saveResource(path, false));
        }

        Map<String, LootTable> tables = new LinkedHashMap<>();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return tables;
        }
        for (File file : files) {
            String id = file.getName().substring(0, file.getName().length() - 4).toLowerCase(Locale.ROOT);
            try {
                tables.put(id, parse(id, file, YamlConfiguration.loadConfiguration(file), rarities,
                        plugin.rewards()));
            } catch (IllegalArgumentException e) {
                plugin.getSLF4JLogger().error("loot/{}: {}", file.getName(), e.getMessage());
            }
        }
        return tables;
    }

    private static LootTable parse(String id, File file, YamlConfiguration yaml, Map<String, Rarity> rarities,
            RewardRegistry rewardTypes) {
        ConfigurationSection tiersSection = yaml.getConfigurationSection("tiers");
        if (tiersSection == null) {
            throw new IllegalArgumentException("'tiers' is required");
        }
        Map<String, LootTier> tiers = new LinkedHashMap<>();
        for (String tierId : tiersSection.getKeys(false)) {
            try {
                Rarity rarity = rarities.get(tierId);
                if (rarity == null) {
                    throw new IllegalArgumentException("there is no rarity '" + tierId + "' in rarities.yml");
                }
                tiers.put(tierId, parseTier(rarity, tiersSection.getConfigurationSection(tierId), rewardTypes));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("tiers." + tierId + ": " + e.getMessage(), e);
            }
        }
        return new LootTable(id, file, Collections.unmodifiableMap(tiers));
    }

    // Name, block, points and effects may be overridden per table; the key always belongs to the rarity.
    private static LootTier parseTier(Rarity rarity, ConfigurationSection section, RewardRegistry rewardTypes) {
        Material block = section.contains("block") ? RarityLoader.block(section.getString("block")) : rarity.block();
        int[] rolls = range(section.getString("rolls", "1"));

        WeightedPool<LootEntry> pool = new WeightedPool<>();
        List<LootEntry> entries = new ArrayList<>();
        List<Map<?, ?>> rawEntries = section.getMapList("loot");
        for (int i = 0; i < rawEntries.size(); i++) {
            ConfigurationSection entry = Sections.fromMap(rawEntries.get(i));
            try {
                ItemSpec item = entry.contains("item") ? ItemSpec.parse(entry, "item") : null;
                ItemStack stack = entry.contains("item-data") ? decode(entry.getString("item-data")) : null;
                int[] amount = range(entry.getString("amount", "1"));
                var rewards = rewardTypes.parseAll(entry.getStringList("rewards"));
                if (item == null && stack == null && rewards.isEmpty()) {
                    throw new IllegalArgumentException("needs 'item', 'item-data' or 'rewards'");
                }
                double weight = entry.getDouble("weight", 1);
                // Every line is kept, even with weight 0: the editor addresses entries by their place in the file.
                LootEntry parsed = new LootEntry(item, stack, amount[0], amount[1], weight, List.copyOf(rewards));
                entries.add(parsed);
                pool.add(parsed, weight);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("loot entry " + (i + 1) + ": " + e.getMessage(), e);
            }
        }
        if (pool.isEmpty()) {
            throw new IllegalArgumentException("'loot' has no entries with a positive weight");
        }

        return new LootTier(
                rarity.id(),
                rarity,
                section.getString("name", rarity.displayName()),
                block,
                rolls[0],
                rolls[1],
                section.getInt("points", rarity.points()),
                pool,
                List.copyOf(entries),
                section.getString("effects.idle", rarity.idleEffect()),
                section.getString("effects.open", rarity.openEffect()));
    }

    // Written by the editor from ItemStack#serializeAsBytes; the server upgrades old data on load.
    private static ItemStack decode(String data) {
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(data));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("item-data is damaged; add the item again with /eventide loot");
        }
    }

    // "3" or "2-5".
    static int[] range(String value) {
        String[] parts = value.trim().split("\\s*-\\s*");
        try {
            int min = Integer.parseInt(parts[0]);
            int max = parts.length > 1 ? Integer.parseInt(parts[1]) : min;
            if (min < 0 || max < min) {
                throw new IllegalArgumentException("range '" + value + "' is invalid");
            }
            return new int[] {min, max};
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + value + "' is not a number or range like 2-5");
        }
    }
}
