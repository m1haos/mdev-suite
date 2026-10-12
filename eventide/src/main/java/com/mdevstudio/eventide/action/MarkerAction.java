package com.mdevstudio.eventide.action;

import com.mdevstudio.core.scenario.ActionFactory;
import com.mdevstudio.core.scenario.ScenarioAction;
import com.mdevstudio.core.util.Durations;
import com.mdevstudio.eventide.EventidePlugin;
import com.mdevstudio.eventide.running.ActiveEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Keeps the event spot visible until the event ends: an effect replayed over it, and for players
 * nearby the distance and direction in the action bar.
 */
final class MarkerAction implements ActionFactory {

    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private final EventidePlugin plugin;

    private record Settings(String effect, long everyTicks, double compassRadius, double hideWithin) {
    }

    MarkerAction(EventidePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public ScenarioAction create(ConfigurationSection section) {
        Settings settings = new Settings(
                section.getString("effect", "event-marker"),
                Math.max(10, Durations.toTicks(Durations.parse(section.getString("every", "2s")))),
                Math.max(0, section.getDouble("compass.radius", 160)),
                Math.max(0, section.getDouble("compass.hide-within", 8)));

        return EventActions.forEvent(event -> {
            if (!plugin.settings().modules().marker()) {
                return;
            }
            Location spot = event.location();
            if (!settings.effect().isBlank()) {
                plugin.playEffect(settings.effect(), spot);
                event.every(settings.everyTicks(), () -> plugin.playEffect(settings.effect(), spot));
            }
            if (settings.compassRadius() > 0) {
                event.every(20, () -> showCompass(event, spot, settings));
            }
        });
    }

    private void showCompass(ActiveEvent event, Location spot, Settings settings) {
        double max = settings.compassRadius() * settings.compassRadius();
        double min = settings.hideWithin() * settings.hideWithin();
        for (Player player : spot.getWorld().getPlayers()) {
            Location at = player.getLocation();
            double dx = spot.getX() + 0.5 - at.getX();
            double dz = spot.getZ() + 0.5 - at.getZ();
            double distanceSquared = dx * dx + dz * dz;
            if (distanceSquared > max || distanceSquared < min) {
                continue;
            }
            player.sendActionBar(plugin.messages().get(player, "marker.compass", event.placeholders(),
                    Placeholder.unparsed("distance", Long.toString(Math.round(Math.sqrt(distanceSquared)))),
                    Placeholder.unparsed("arrow", arrow(at.getYaw(), dx, dz))));
        }
    }

    private static String arrow(float playerYaw, double dx, double dz) {
        // Minecraft yaw: 0 faces +Z and grows clockwise, so a positive difference means "to the right".
        double target = Math.toDegrees(Math.atan2(-dx, dz));
        double relative = ((target - playerYaw) % 360 + 540) % 360 - 180;
        return ARROWS[Math.floorMod(Math.round(relative / 45), ARROWS.length)];
    }
}
