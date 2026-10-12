package com.mdevstudio.core.scenario;

import org.bukkit.configuration.ConfigurationSection;

@FunctionalInterface
public interface ActionFactory {

    /** Reads the action's settings once, when the scenario is loaded, and fails fast on bad config. */
    ScenarioAction create(ConfigurationSection settings);
}
