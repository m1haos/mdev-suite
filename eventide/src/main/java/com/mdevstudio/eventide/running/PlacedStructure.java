package com.mdevstudio.eventide.running;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

import com.mdevstudio.eventide.EventidePlugin;
import com.mdevstudio.eventide.definition.StructureSpec;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.structure.Structure;
import org.bukkit.util.BlockVector;

/**
 * A build standing on the event spot. The area under it is copied to a file first and put back when
 * the event ends; if the server stops before that, it is put back on the next start. The copy reaches
 * {@code fill-depth} blocks below the build, where the ground is filled up to it.
 */
public final class PlacedStructure {

    private static final String FOLDER = "restore";

    private final EventidePlugin plugin;
    private final World world;
    private final Location corner;
    private final BlockVector size;
    private final Area area;
    private final Structure original;
    private final File backup;
    private final File note;

    private PlacedStructure(EventidePlugin plugin, Location corner, Structure original, File backup, File note) {
        this.plugin = plugin;
        this.world = corner.getWorld();
        this.corner = corner;
        this.size = original.getSize();
        this.area = Area.of(corner, size.getBlockX(), size.getBlockY(), size.getBlockZ());
        this.original = original;
        this.backup = backup;
        this.note = note;
    }

    static PlacedStructure place(EventidePlugin plugin, String eventId, StructureSpec spec, Location spot)
            throws IOException {
        BlockVector origin = spec.origin();
        Location buildCorner = spot.getBlock().getLocation()
                .subtract(origin.getBlockX(), origin.getBlockY(), origin.getBlockZ());
        World world = spot.getWorld();
        int depth = Math.min(spec.fit().fillDepth(), buildCorner.getBlockY() - world.getMinHeight());
        Location corner = buildCorner.clone().subtract(0, depth, 0);
        BlockVector buildSize = spec.structure().getSize();
        Structure original = Bukkit.getStructureManager().createStructure();
        original.fill(corner, new BlockVector(buildSize.getBlockX(), buildSize.getBlockY() + depth,
                buildSize.getBlockZ()), false);

        File folder = new File(plugin.getDataFolder(), FOLDER);
        String name = eventId + "-" + System.currentTimeMillis();
        File backup = new File(folder, name + ".nbt");
        File note = new File(folder, name + ".yml");
        // The note goes last: a backup without a note means the build never went up, so nothing is restored.
        try {
            Files.createDirectories(folder.toPath());
            Bukkit.getStructureManager().saveStructure(backup, original);
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("event", eventId);
            yaml.set("world", corner.getWorld().getUID().toString());
            yaml.set("corner", List.of(corner.getBlockX(), corner.getBlockY(), corner.getBlockZ()));
            yaml.save(note);
        } catch (IOException e) {
            backup.delete();
            note.delete();
            throw e;
        }

        spec.structure().place(buildCorner, false, StructureRotation.NONE, Mirror.NONE, 0, 1,
                ThreadLocalRandom.current());
        fillUnder(spec.structure(), buildCorner, depth);
        PlacedStructure placed = new PlacedStructure(plugin, corner, original, backup, note);
        placed.freeTrappedPlayers();
        return placed;
    }

    // Each column of the bottom layer goes on down until it meets the ground, so the build does not
    // hang over a dip or a pond.
    private static void fillUnder(Structure structure, Location buildCorner, int depth) {
        if (structure.getPalettes().isEmpty()) {
            return;
        }
        for (BlockState bottom : structure.getPalettes().getFirst().getBlocks()) {
            if (bottom.getY() != 0 || bottom.getType().isAir()) {
                continue;
            }
            Block below = buildCorner.getBlock().getRelative(bottom.getX(), -1, bottom.getZ());
            for (int step = 0; step < depth && fillable(below); step++) {
                below.setBlockData(bottom.getBlockData(), false);
                below = below.getRelative(BlockFace.DOWN);
            }
        }
    }

    private static boolean fillable(Block block) {
        return block.isPassable() || Tag.LEAVES.isTagged(block.getType());
    }

    /** Puts back areas of events that were running when the server stopped without ending them. */
    public static void restoreLeftovers(EventidePlugin plugin) {
        File[] notes = new File(plugin.getDataFolder(), FOLDER).listFiles((dir, name) -> name.endsWith(".yml"));
        if (notes == null) {
            return;
        }
        for (File note : notes) {
            File backup = new File(note.getParentFile(), note.getName().replace(".yml", ".nbt"));
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(note);
            List<Integer> corner = yaml.getIntegerList("corner");
            World world = world(yaml.getString("world", ""));
            if (world == null || corner.size() != 3 || !backup.isFile()) {
                plugin.getSLF4JLogger().warn("{}/{}: the area of an event that was running when the server stopped "
                        + "is not restored: its world is not loaded or the backup is missing. "
                        + "Eventide tries again on the next start.", FOLDER, note.getName());
                continue;
            }
            Structure original;
            try {
                original = Bukkit.getStructureManager().loadStructure(backup);
            } catch (IOException e) {
                plugin.getSLF4JLogger().error("{}/{}: cannot read the backup of an event area", FOLDER,
                        backup.getName(), e);
                continue;
            }
            Location at = new Location(world, corner.get(0), corner.get(1), corner.get(2));
            PlacedStructure leftover = new PlacedStructure(plugin, at, original, backup, note);
            leftover.loadChunks().thenRun(() -> {
                leftover.restore();
                plugin.getSLF4JLogger().info("Restored the area of event {} at {} {} {} in {}: the server stopped "
                        + "while it was running", yaml.getString("event", "?"), corner.get(0), corner.get(1),
                        corner.get(2), world.getName());
            });
        }
    }

    void restore() {
        original.place(corner, false, StructureRotation.NONE, Mirror.NONE, 0, 1, ThreadLocalRandom.current());
        freeTrappedPlayers();
        // Without the note the copy is never placed again, so a later start cannot roll the area back twice.
        if (!note.delete() && note.exists()) {
            plugin.getSLF4JLogger().error("Could not delete {}/{}. Delete it by hand before the next start, "
                    + "or the area at {} {} {} will be rolled back again.", FOLDER, note.getName(),
                    corner.getBlockX(), corner.getBlockY(), corner.getBlockZ());
        }
        backup.delete();
    }

    /** Whether the place is inside the build, or within {@code margin} blocks of it. */
    public boolean contains(Location at, int margin) {
        return area.contains(at, margin);
    }

    // A wall going up, or the hill coming back, can end up where a player stands.
    private void freeTrappedPlayers() {
        for (Player player : world.getPlayers()) {
            Block feet = player.getLocation().getBlock();
            boolean free = feet.isPassable() && feet.getRelative(BlockFace.UP).isPassable();
            if (free || !area.contains(feet.getLocation())) {
                continue;
            }
            Location at = player.getLocation();
            at.setY(world.getHighestBlockYAt(feet.getX(), feet.getZ()) + 1);
            player.teleport(at);
        }
    }

    private CompletableFuture<Void> loadChunks() {
        List<CompletableFuture<?>> loads = new ArrayList<>();
        for (int x = corner.getBlockX() >> 4; x <= (corner.getBlockX() + size.getBlockX() - 1) >> 4; x++) {
            for (int z = corner.getBlockZ() >> 4; z <= (corner.getBlockZ() + size.getBlockZ() - 1) >> 4; z++) {
                loads.add(world.getChunkAtAsync(x, z));
            }
        }
        return CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new));
    }

    private static World world(String id) {
        try {
            return Bukkit.getWorld(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
