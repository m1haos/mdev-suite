package com.mdevstudio.eventide.hook;

import me.angeschossen.lands.api.LandsIntegration;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

final class LandsClaims implements ClaimSource {

    private final LandsIntegration lands;

    LandsClaims(Plugin plugin) {
        this.lands = LandsIntegration.of(plugin);
    }

    @Override
    public String plugin() {
        return "Lands";
    }

    // Lands claims whole chunks; the unloaded-chunk lookup reads its cache and loads nothing.
    @Override
    public boolean claimed(World world, int minX, int minZ, int maxX, int maxZ) {
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                if (lands.getLandByUnloadedChunk(world, chunkX, chunkZ) != null) {
                    return true;
                }
            }
        }
        return false;
    }
}
