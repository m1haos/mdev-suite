package com.mdevstudio.eventide.hook;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Location;
import org.bukkit.World;

final class GriefPreventionClaims implements ClaimSource {

    @Override
    public String plugin() {
        return "GriefPrevention";
    }

    // GriefPrevention indexes claims by chunk, and the index does not separate worlds.
    @Override
    public boolean claimed(World world, int minX, int minZ, int maxX, int maxZ) {
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                for (Claim claim : GriefPrevention.instance.dataStore.getClaims(chunkX, chunkZ)) {
                    Location lesser = claim.getLesserBoundaryCorner();
                    Location greater = claim.getGreaterBoundaryCorner();
                    if (world.equals(lesser.getWorld())
                            && lesser.getBlockX() <= maxX && greater.getBlockX() >= minX
                            && lesser.getBlockZ() <= maxZ && greater.getBlockZ() >= minZ) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
