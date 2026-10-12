package com.mdevstudio.core.scenario;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;

public final class ActionRegistry {

    private final Map<String, ActionFactory> factories = new HashMap<>();

    public void register(String type, ActionFactory factory) {
        factories.put(type.toLowerCase(Locale.ROOT), factory);
    }

    public ScenarioAction create(ConfigurationSection settings) {
        String type = settings.getString("type", "").toLowerCase(Locale.ROOT);
        ActionFactory factory = factories.get(type);
        if (factory == null) {
            throw new IllegalArgumentException("Unknown action type '" + type + "'. Known: " + factories.keySet());
        }
        return factory.create(settings);
    }
}
