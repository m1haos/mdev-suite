package com.mdevstudio.eventide.loot;

import java.util.Set;

import com.mdevstudio.core.item.ItemSpec;
import org.bukkit.Material;

/**
 * A rarity from {@code rarities.yml}: how its caches look and what key opens them. Loot tables
 * only say what drops for each rarity, so one key works for caches of every table.
 *
 * @param key null when caches of this rarity open without a key
 */
public record Rarity(
        String id,
        String displayName,
        Material block,
        int points,
        String idleEffect,
        String openEffect,
        KeySpec key) {

    /**
     * @param price    cost in {@code /eventide buykey}; 0 means the key is not sold
     * @param consume  false makes the key reusable
     * @param alsoOpens other rarities this key opens, e.g. a master key
     */
    public record KeySpec(ItemSpec item, double price, boolean consume, Set<String> alsoOpens) {
    }

    public boolean locked() {
        return key != null;
    }

    public boolean forSale() {
        return key != null && key.price() > 0;
    }

    /** Whether the key of this rarity opens a cache of {@code cache} rarity. */
    public boolean keyOpens(Rarity cache) {
        return key != null && (id.equals(cache.id()) || key.alsoOpens().contains(cache.id()));
    }
}
