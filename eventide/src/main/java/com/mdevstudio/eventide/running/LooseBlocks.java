package com.mdevstudio.eventide.running;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.mdevstudio.eventide.EventidePlugin;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

/**
 * Keeps a list of the blocks that running events have placed (caches, crates, pickups), so that a server
 * which crashed in the middle of an event clears them on the next start. A clean stop removes them itself
 * and deletes the list. A block is only removed if it still has the type that was written down, so
 * something a player built there afterwards stays.
 */
public final class LooseBlocks {

    private static final String FILE = "loose-blocks.yml";
    private static final long SAVE_PERIOD_TICKS = 5 * 20;

    private final EventidePlugin plugin;
    private final File file;
    private BukkitTask task;

    public LooseBlocks(EventidePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), FILE);
    }

    /** Clears what a crashed run left behind, then starts writing the list of the current run. */
    public void start() {
        restoreLeftovers();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::save, SAVE_PERIOD_TICKS,
                SAVE_PERIOD_TICKS);
    }

    /** Call after every event has been stopped: nothing is left to clear, so the list goes away. */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        deleteFile();
    }

    private void save() {
        List<String> lines = new ArrayList<>();
        for (Block block : plugin.manager().claimedBlocks()) {
            if (!block.getType().isAir()) {
                lines.add(block.getWorld().getName() + ";" + block.getX() + ";" + block.getY() + ";" + block.getZ()
                        + ";" + block.getType().name());
            }
        }
        if (lines.isEmpty()) {
            deleteFile();
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("blocks", lines);
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getSLF4JLogger().error("Could not save {}", FILE, e);
        }
    }

    private void restoreLeftovers() {
        if (!file.isFile()) {
            return;
        }
        List<String> missingWorld = new ArrayList<>();
        int cleared = 0;
        for (String line : YamlConfiguration.loadConfiguration(file).getStringList("blocks")) {
            String[] parts = line.split(";");
            Material type = parts.length == 5 ? Material.getMaterial(parts[4]) : null;
            if (type == null) {
                continue;
            }
            World world = plugin.getServer().getWorld(parts[0]);
            if (world == null) {
                missingWorld.add(line);
                continue;
            }
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            world.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk -> {
                Block block = world.getBlockAt(x, y, z);
                if (block.getType() == type) {
                    block.setType(Material.AIR);
                }
            });
            cleared++;
        }
        deleteFile();
        if (!missingWorld.isEmpty()) {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("blocks", missingWorld);
            try {
                yaml.save(file);
            } catch (IOException e) {
                plugin.getSLF4JLogger().error("Could not save {}", FILE, e);
            }
            plugin.getSLF4JLogger().warn("{}: {} blocks of an event are in a world that is not loaded; "
                    + "Eventide tries again on the next start.", FILE, missingWorld.size());
        }
        if (cleared > 0) {
            plugin.getSLF4JLogger().info("Cleared {} blocks of an event that was running when the server stopped",
                    cleared);
        }
    }

    private void deleteFile() {
        if (file.exists() && !file.delete()) {
            plugin.getSLF4JLogger().error("Could not delete {}. Delete it by hand before the next start.", FILE);
        }
    }
}
