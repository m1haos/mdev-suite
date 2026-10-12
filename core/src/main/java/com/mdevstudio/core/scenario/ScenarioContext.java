package com.mdevstudio.core.scenario;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

/** What a running scenario knows about itself. Plugins pass their own implementation to actions. */
public interface ScenarioContext {

    Plugin plugin();

    Location location();

    /** Placeholders available to every message in the scenario, e.g. {@code <x>} or {@code <event>}. */
    TagResolver placeholders();
}
