package com.mdevstudio.core.util;

import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

public final class Sections {

    private Sections() {
    }

    /** Entries of a YAML list like {@code stages:} come back as maps; this gives them the usual section API. */
    public static ConfigurationSection fromMap(Map<?, ?> map) {
        MemoryConfiguration section = new MemoryConfiguration();
        map.forEach((key, value) -> {
            if (value instanceof Map<?, ?> nested) {
                section.set(String.valueOf(key), fromMap(nested));
            } else {
                section.set(String.valueOf(key), value);
            }
        });
        return section;
    }
}
