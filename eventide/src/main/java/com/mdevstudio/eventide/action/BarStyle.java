package com.mdevstudio.eventide.action;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.ConfigurationSection;

/** The {@code bar} section of an action: color, overlay and who sees it. */
record BarStyle(BossBar.Color color, BossBar.Overlay overlay, double radius) {

    static BarStyle parse(ConfigurationSection action, String defaultColor, double defaultRadius) {
        ConfigurationSection bar = action.getConfigurationSection("bar");
        if (bar == null) {
            return new BarStyle(EventActions.enumValue(BossBar.Color.class, defaultColor), BossBar.Overlay.PROGRESS,
                    defaultRadius);
        }
        return new BarStyle(
                EventActions.enumValue(BossBar.Color.class, bar.getString("color", defaultColor)),
                EventActions.enumValue(BossBar.Overlay.class, bar.getString("overlay", "progress")),
                Math.max(0, bar.getDouble("radius", defaultRadius)));
    }

    BossBar create(Component name, float progress) {
        return BossBar.bossBar(name, Math.clamp(progress, 0f, 1f), color, overlay);
    }
}
