package com.mdevstudio.core.scenario;

import java.util.List;

import com.mdevstudio.core.MDevCore;
import com.mdevstudio.core.text.Text;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class BuiltinActions {

    private BuiltinActions() {
    }

    public static void registerAll(ActionRegistry registry, Text text) {
        registry.register("broadcast", settings -> {
            String message = required(settings.getString("message"), "message");
            return context -> Bukkit.broadcast(text.parse(message, context.placeholders()));
        });

        registry.register("title", settings -> {
            String title = settings.getString("title", "");
            String subtitle = settings.getString("subtitle", "");
            int radius = settings.getInt("radius", 0);
            return context -> {
                Title shown = Title.title(
                        text.parse(title, context.placeholders()),
                        text.parse(subtitle, context.placeholders()));
                audience(context.location(), radius).forEach(player -> player.showTitle(shown));
            };
        });

        registry.register("sound", settings -> {
            Key key = Key.key(required(settings.getString("sound"), "sound"));
            float volume = (float) settings.getDouble("volume", 1);
            float pitch = (float) settings.getDouble("pitch", 1);
            int radius = settings.getInt("radius", 0);
            Sound sound = Sound.sound(key, Sound.Source.MASTER, volume, pitch);
            return context -> audience(context.location(), radius).forEach(player -> player.playSound(sound));
        });

        registry.register("command", settings -> {
            String command = required(settings.getString("command"), "command");
            return context -> {
                Location at = context.location();
                String resolved = command
                        .replace("<world>", at.getWorld().getName())
                        .replace("<x>", Integer.toString(at.getBlockX()))
                        .replace("<y>", Integer.toString(at.getBlockY()))
                        .replace("<z>", Integer.toString(at.getBlockZ()));
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
            };
        });

        registry.register("effect", settings -> {
            String id = required(settings.getString("effect"), "effect");
            List<Double> offset = settings.getDoubleList("offset");
            double dx = offset.size() > 0 ? offset.get(0) : 0;
            double dy = offset.size() > 1 ? offset.get(1) : 0;
            double dz = offset.size() > 2 ? offset.get(2) : 0;
            return context -> MDevCore.effects().play(id, context.location().add(dx, dy, dz));
        });

        registry.register("actionbar", settings -> {
            String message = required(settings.getString("message"), "message");
            int radius = settings.getInt("radius", 0);
            return context -> {
                Component shown = text.parse(message, context.placeholders());
                audience(context.location(), radius).forEach(player -> player.sendActionBar(shown));
            };
        });
    }

    /** Everyone online when {@code radius} is 0, otherwise players within that many blocks. */
    public static List<Player> audience(Location center, int radius) {
        if (radius <= 0) {
            return List.copyOf(Bukkit.getOnlinePlayers());
        }
        return List.copyOf(center.getNearbyPlayers(radius));
    }

    public static String required(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("'" + key + "' is required");
        }
        return value;
    }
}
