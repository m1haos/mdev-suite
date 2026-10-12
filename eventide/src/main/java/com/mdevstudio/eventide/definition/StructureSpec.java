package com.mdevstudio.eventide.definition;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.structure.Structure;
import org.bukkit.util.BlockVector;

/**
 * A build that stands on the event spot while the event runs. {@code origin} is the block of the
 * build that lands on the spot: the first air block above its floor.
 */
public record StructureSpec(String file, Structure structure, BlockVector origin, GroundFit fit) {

    public static StructureSpec parse(ConfigurationSection section, File folder) {
        String file = section.getString("file", "");
        if (file.isBlank()) {
            throw new IllegalArgumentException("structure.file is required");
        }
        File source = new File(folder, file);
        if (!source.isFile()) {
            throw new IllegalArgumentException("structure.file: " + source.getPath() + " does not exist");
        }
        Structure structure;
        try {
            structure = Bukkit.getStructureManager().loadStructure(source);
        } catch (IOException e) {
            throw new IllegalArgumentException("structure.file: cannot read " + file + ": " + e.getMessage());
        }

        BlockVector size = structure.getSize();
        List<Integer> origin = section.getIntegerList("origin");
        if (!origin.isEmpty() && origin.size() != 3) {
            throw new IllegalArgumentException("structure.origin must be [x, y, z]");
        }
        BlockVector at = origin.isEmpty()
                ? new BlockVector(size.getBlockX() / 2, 0, size.getBlockZ() / 2)
                : new BlockVector(origin.get(0), origin.get(1), origin.get(2));
        if (at.getBlockX() < 0 || at.getBlockY() < 0 || at.getBlockZ() < 0
                || at.getBlockX() >= size.getBlockX() || at.getBlockY() >= size.getBlockY()
                || at.getBlockZ() >= size.getBlockZ()) {
            throw new IllegalArgumentException("structure.origin " + origin + " is outside the build, which is "
                    + size.getBlockX() + "x" + size.getBlockY() + "x" + size.getBlockZ());
        }
        return new StructureSpec(file, structure, at, GroundFit.parse(section));
    }

    /** How far the build reaches from the spot sideways, in blocks. */
    public int reach() {
        BlockVector size = structure.getSize();
        return Math.max(
                Math.max(origin.getBlockX(), size.getBlockX() - 1 - origin.getBlockX()),
                Math.max(origin.getBlockZ(), size.getBlockZ() - 1 - origin.getBlockZ()));
    }
}
