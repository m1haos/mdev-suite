package com.mdevstudio.eventide.loot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

/**
 * Changes a loot file on behalf of the in-game editor. The file is read again before every change,
 * so edits made by hand in the meantime are kept rather than overwritten from memory.
 */
public final class LootWriter {

    private final LootTable table;
    private final LootTier tier;

    public LootWriter(LootTable table, LootTier tier) {
        this.table = table;
        this.tier = tier;
    }

    /** Adds the item as a new line and returns its index. The stored item has amount 1; {@code amount} goes to the line. */
    public int add(ItemStack item, int amount, double weight) throws IOException {
        ItemStack single = item.asOne();
        Map<String, Object> entry = new LinkedHashMap<>();
        // Only for people reading the file; the loader ignores it.
        entry.put("about", single.getType().getKey().getKey() + " "
                + PlainTextComponentSerializer.plainText().serialize(single.effectiveName()));
        entry.put("item-data", Base64.getEncoder().encodeToString(single.serializeAsBytes()));
        entry.put("amount", amount);
        entry.put("weight", weight);
        int[] index = new int[1];
        editEntries(entries -> {
            entries.add(entry);
            index[0] = entries.size() - 1;
        });
        return index[0];
    }

    public void update(int index, double weight, int minAmount, int maxAmount) throws IOException {
        editEntries(entries -> {
            Map<String, Object> entry = entries.get(index);
            entry.put("weight", weight);
            entry.put("amount", minAmount == maxAmount ? (Object) minAmount : minAmount + "-" + maxAmount);
        });
    }

    public void remove(int index) throws IOException {
        editEntries(entries -> entries.remove(index));
    }

    public void updateTier(int minRolls, int maxRolls, int points) throws IOException {
        YamlConfiguration yaml = load();
        String path = "tiers." + tier.id();
        yaml.set(path + ".rolls", minRolls == maxRolls ? (Object) minRolls : minRolls + "-" + maxRolls);
        yaml.set(path + ".points", points);
        yaml.save(table.file());
    }

    private void editEntries(Consumer<List<Map<String, Object>>> change) throws IOException {
        YamlConfiguration yaml = load();
        String path = "tiers." + tier.id() + ".loot";
        List<Map<String, Object>> entries = new ArrayList<>();
        for (Map<?, ?> raw : yaml.getMapList(path)) {
            Map<String, Object> copy = new LinkedHashMap<>();
            raw.forEach((key, value) -> copy.put(String.valueOf(key), value));
            entries.add(copy);
        }
        // Someone edited the file by hand after the last reload: line numbers no longer match what the editor shows.
        if (entries.size() != tier.entries().size()) {
            throw new StaleLootException();
        }
        change.accept(entries);
        yaml.set(path, entries);
        yaml.save(table.file());
    }

    private YamlConfiguration load() throws IOException {
        if (!table.file().isFile()) {
            throw new IOException(table.file().getName() + " no longer exists");
        }
        return YamlConfiguration.loadConfiguration(table.file());
    }

    /** The file changed on disk since the loot was loaded. */
    public static final class StaleLootException extends IOException {

        StaleLootException() {
            super("the loot file was changed by hand; reload and try again");
        }
    }
}
