package com.mdevstudio.eventide.hook;

import java.util.Set;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.World;

final class WorldGuardClaims implements ClaimSource {

    // Never stored: only used to ask WorldGuard which regions overlap the square.
    private static final String QUERY_ID = "eventide-spot-check";

    private final Set<String> ignored;

    WorldGuardClaims(Set<String> ignored) {
        this.ignored = ignored;
    }

    @Override
    public String plugin() {
        return "WorldGuard";
    }

    @Override
    public boolean claimed(World world, int minX, int minZ, int maxX, int maxZ) {
        RegionManager regions = WorldGuard.getInstance().getPlatform().getRegionContainer()
                .get(BukkitAdapter.adapt(world));
        if (regions == null) {
            return false;
        }
        ProtectedRegion square = new ProtectedCuboidRegion(QUERY_ID,
                BlockVector3.at(minX, world.getMinHeight(), minZ),
                BlockVector3.at(maxX, world.getMaxHeight() - 1, maxZ));
        for (ProtectedRegion region : regions.getApplicableRegions(square)) {
            if (!region.getId().equals(ProtectedRegion.GLOBAL_REGION) && !ignored.contains(region.getId())) {
                return true;
            }
        }
        return false;
    }
}
