package com.mdevstudio.eventide.running;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;

/** A box of blocks in one world, both corners included. */
public record Area(UUID world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    /** A square of {@code radius} blocks around the spot, from {@code below} under it to {@code above} over it. */
    public static Area around(Location spot, int radius, int below, int above) {
        World world = spot.getWorld();
        int minY = Math.max(world.getMinHeight(), spot.getBlockY() - below);
        int maxY = Math.min(world.getMaxHeight() - 1, spot.getBlockY() + above);
        return new Area(world.getUID(), spot.getBlockX() - radius, minY, spot.getBlockZ() - radius,
                spot.getBlockX() + radius, maxY, spot.getBlockZ() + radius);
    }

    public static Area of(Location corner, int sizeX, int sizeY, int sizeZ) {
        return new Area(corner.getWorld().getUID(), corner.getBlockX(), corner.getBlockY(), corner.getBlockZ(),
                corner.getBlockX() + sizeX - 1, corner.getBlockY() + sizeY - 1, corner.getBlockZ() + sizeZ - 1);
    }

    public boolean contains(Location at) {
        return contains(at, 0);
    }

    public boolean contains(Location at, int margin) {
        return at.getWorld().getUID().equals(world)
                && at.getBlockX() >= minX - margin && at.getBlockX() <= maxX + margin
                && at.getBlockY() >= minY - margin && at.getBlockY() <= maxY + margin
                && at.getBlockZ() >= minZ - margin && at.getBlockZ() <= maxZ + margin;
    }
}
