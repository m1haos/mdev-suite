package com.mdevstudio.eventide.running;

import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Running events written into the world's own data. Plugins built from this code under another name
 * keep separate event lists; without this, one could build right over another's event and later roll
 * its ground back over the other's build.
 */
final class TakenAreas {

    private static final String PREFIX = "event_area_";

    private final Plugin plugin;
    private final String namespace;

    TakenAreas(Plugin plugin) {
        this.plugin = plugin;
        this.namespace = plugin.getName().toLowerCase(Locale.ROOT);
    }

    void mark(String eventId, Location spot, int reach) {
        spot.getWorld().getPersistentDataContainer().set(key(eventId), PersistentDataType.INTEGER_ARRAY,
                new int[] {spot.getBlockX(), spot.getBlockZ(), reach});
    }

    void unmark(String eventId, World world) {
        world.getPersistentDataContainer().remove(key(eventId));
    }

    // World data is saved with the world, so marks left by a crash would block the spot forever.
    void clearOwn() {
        for (World world : Bukkit.getWorlds()) {
            PersistentDataContainer worldData = world.getPersistentDataContainer();
            worldData.getKeys().stream()
                    .filter(key -> key.getNamespace().equals(namespace) && key.getKey().startsWith(PREFIX))
                    .toList()
                    .forEach(worldData::remove);
        }
    }

    boolean takenByOthers(Location spot, int reach, int gap) {
        PersistentDataContainer worldData = spot.getWorld().getPersistentDataContainer();
        for (NamespacedKey key : worldData.getKeys()) {
            if (key.getNamespace().equals(namespace) || !key.getKey().startsWith(PREFIX)) {
                continue;
            }
            int[] other = worldData.get(key, PersistentDataType.INTEGER_ARRAY);
            if (other == null || other.length != 3) {
                continue;
            }
            int distance = Math.max(Math.abs(other[0] - spot.getBlockX()), Math.abs(other[1] - spot.getBlockZ()));
            if (distance < reach + other[2] + gap) {
                return true;
            }
        }
        return false;
    }

    private NamespacedKey key(String eventId) {
        return new NamespacedKey(plugin, PREFIX + eventId);
    }
}
