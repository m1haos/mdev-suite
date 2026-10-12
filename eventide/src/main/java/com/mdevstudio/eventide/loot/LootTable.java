package com.mdevstudio.eventide.loot;

import java.io.File;
import java.util.Map;
import java.util.Optional;

/** Tiers from one file in the loot folder. {@code file} is where the editor writes changes. */
public record LootTable(String id, File file, Map<String, LootTier> tiers) {

    public Optional<LootTier> tier(String id) {
        return Optional.ofNullable(tiers.get(id));
    }
}
