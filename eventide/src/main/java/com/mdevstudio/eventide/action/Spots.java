package com.mdevstudio.eventide.action;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import com.mdevstudio.eventide.definition.LocationRule;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/** Picking places around the event spot and finding who stands there. */
final class Spots {

    private static final int FREE_SPOT_ATTEMPTS = 12;

    private Spots() {
    }

    static Location randomSurface(Location center, int spread) {
        var random = ThreadLocalRandom.current();
        int x = center.getBlockX() + random.nextInt(-spread, spread + 1);
        int z = center.getBlockZ() + random.nextInt(-spread, spread + 1);
        return LocationRule.surface(center.getWorld(), x, z);
    }

    /** An air block on the surface at least 2 blocks away from every block in {@code used}. Null if none found. */
    static Block freeBlock(Location center, int spread, Set<Block> used) {
        for (int attempt = 0; attempt < FREE_SPOT_ATTEMPTS; attempt++) {
            Location spot = randomSurface(center, spread);
            if (spot == null) {
                continue;
            }
            Block block = spot.getBlock();
            boolean touchesAnother = used.stream().anyMatch(other ->
                    other.getLocation().distanceSquared(block.getLocation()) < 4);
            if (block.getType().isAir() && !touchesAnother) {
                return block;
            }
        }
        return null;
    }

    /**
     * Players who take part in the fight inside a cylinder around {@code center}. Spectators and
     * players in creative are left out, so an admin watching does not block a capture.
     */
    static List<Player> playersIn(Location center, double radius, double height) {
        return center.getWorld().getPlayers().stream()
                .filter(player -> !player.isDead())
                .filter(player -> player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE)
                .filter(player -> {
                    Location at = player.getLocation();
                    double dx = at.getX() - center.getX();
                    double dz = at.getZ() - center.getZ();
                    return dx * dx + dz * dz <= radius * radius && Math.abs(at.getY() - center.getY()) <= height;
                })
                .toList();
    }
}
