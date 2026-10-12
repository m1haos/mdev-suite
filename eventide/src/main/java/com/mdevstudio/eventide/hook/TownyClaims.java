package com.mdevstudio.eventide.hook;

import java.util.ArrayList;
import java.util.List;

import com.palmergames.bukkit.towny.TownyAPI;
import org.bukkit.Location;
import org.bukkit.World;

final class TownyClaims implements ClaimSource {

    // Town blocks are 16 wide by default; checking every 4th block still finds them if a server shrinks them to 4.
    private static final int STEP = 4;

    @Override
    public String plugin() {
        return "Towny";
    }

    @Override
    public boolean claimed(World world, int minX, int minZ, int maxX, int maxZ) {
        TownyAPI towny = TownyAPI.getInstance();
        for (int x : points(minX, maxX)) {
            for (int z : points(minZ, maxZ)) {
                if (!towny.isWilderness(new Location(world, x, world.getMinHeight(), z))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<Integer> points(int min, int max) {
        List<Integer> points = new ArrayList<>();
        for (int value = min; value < max; value += STEP) {
            points.add(value);
        }
        points.add(max);
        return points;
    }
}
