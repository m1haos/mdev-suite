package com.mdevstudio.eventide.definition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.configuration.ConfigurationSection;

// openSky: only spots with nothing above the ground, so a falling crate or a zone is not hidden under trees.
// fit: present for events with a build, which evens out the ground instead of needing it flat.
public record LocationRule(String world, int centerX, int centerZ, int minRadius, int maxRadius, int attempts,
        boolean openSky, SurfaceRule surface, Optional<GroundFit> fit) {

    // Every second column is enough to notice a pond, a ravine or a forest, at a quarter of the lookups.
    private static final int SAMPLE_STEP = 2;
    private static final int COLUMN_STEP = 4;

    /** With a build, its whole area gets checked, by the build's own ground limits. */
    public static LocationRule parse(ConfigurationSection section, SurfaceRule defaults,
            Optional<StructureSpec> build) {
        if (section == null) {
            throw new IllegalArgumentException("'location' is required");
        }
        List<Integer> center = section.getIntegerList("center");
        List<Integer> radius = section.getIntegerList("radius");
        int min = radius.isEmpty() ? 0 : radius.getFirst();
        int max = radius.size() > 1 ? radius.get(1) : Math.max(min, 500);
        if (max < min) {
            throw new IllegalArgumentException("location.radius: max is smaller than min");
        }
        SurfaceRule surface;
        try {
            surface = SurfaceRule.parse(section.getConfigurationSection("surface"), defaults)
                    .withRadiusAtLeast(build.map(StructureSpec::reach).orElse(0));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("location.surface." + e.getMessage());
        }
        return new LocationRule(
                section.getString("world", "world"),
                center.isEmpty() ? 0 : center.get(0),
                center.size() > 1 ? center.get(1) : 0,
                min,
                max,
                Math.max(1, section.getInt("attempts", 30)),
                section.getBoolean("open-sky", false),
                surface,
                build.map(StructureSpec::fit));
    }

    /** {@code veto} turns a spot down for reasons outside the ground, e.g. claimed land; it returns why, or null. */
    public CompletableFuture<Location> find(Function<Location, String> veto) {
        World target = Bukkit.getWorld(world);
        if (target == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("World '" + world + "' is not loaded"));
        }
        return attempt(target, attempts, veto, new HashMap<>());
    }

    private CompletableFuture<Location> attempt(World target, int left, Function<Location, String> veto,
            Map<String, Integer> rejected) {
        if (left <= 0) {
            int columns = rejected.values().stream().mapToInt(Integer::intValue).sum();
            return CompletableFuture.failedFuture(new IllegalStateException("No suitable spot in " + attempts
                    + " chunks (" + columns + " columns) around " + centerX + ", " + centerZ + ": "
                    + summary(rejected)));
        }
        var random = ThreadLocalRandom.current();
        double angle = random.nextDouble(Math.PI * 2);
        double distance = minRadius + random.nextDouble() * (maxRadius - minRadius);
        int x = centerX + (int) Math.round(Math.cos(angle) * distance);
        int z = centerZ + (int) Math.round(Math.sin(angle) * distance);

        return target.getChunkAtAsync(x >> 4, z >> 4)
                .thenCompose(chunk -> tryColumns(target, columnsOf(x, z), 0, veto, rejected))
                .thenCompose(spot -> spot != null
                        ? CompletableFuture.completedFuture(spot)
                        : attempt(target, left - 1, veto, rejected));
    }

    // Loading a chunk is the expensive part of the search, so every loaded chunk is tried on a grid of
    // columns instead of one: a chunk half in a pond or a forest usually still has a good spot.
    private static List<int[]> columnsOf(int x, int z) {
        List<int[]> columns = new ArrayList<>();
        int baseX = x & ~15;
        int baseZ = z & ~15;
        for (int dx = 2; dx < 16; dx += COLUMN_STEP) {
            for (int dz = 2; dz < 16; dz += COLUMN_STEP) {
                columns.add(new int[] {baseX + dx, baseZ + dz});
            }
        }
        Collections.shuffle(columns);
        columns.addFirst(new int[] {x, z});
        return columns;
    }

    private CompletableFuture<Location> tryColumns(World target, List<int[]> columns, int index,
            Function<Location, String> veto, Map<String, Integer> rejected) {
        if (index >= columns.size()) {
            return CompletableFuture.completedFuture(null);
        }
        int x = columns.get(index)[0];
        int z = columns.get(index)[1];
        Location spot = surface(target, x, z);
        String problem = spotProblem(target, x, z, spot, veto);
        if (problem != null) {
            rejected.merge(problem, 1, Integer::sum);
            return tryColumns(target, columns, index + 1, veto, rejected);
        }
        if (!surface.checked()) {
            return CompletableFuture.completedFuture(spot);
        }
        return loadArea(target, x, z).thenCompose(loaded -> {
            AreaVerdict verdict = fit.map(build -> fitProblem(target, x, z, build))
                    .orElseGet(() -> new AreaVerdict(areaProblem(target, x, z), spot.getBlockY() - 1));
            if (verdict.problem() == null) {
                spot.setY(verdict.floor() + 1);
                return CompletableFuture.completedFuture(spot);
            }
            rejected.merge(verdict.problem(), 1, Integer::sum);
            return tryColumns(target, columns, index + 1, veto, rejected);
        });
    }

    private String spotProblem(World target, int x, int z, Location spot, Function<Location, String> veto) {
        if (spot == null) {
            return target.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES).isLiquid()
                    ? "water or lava" : "no solid ground";
        }
        if (openSky && target.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING) >= spot.getBlockY()) {
            return "under trees or a roof";
        }
        String vetoed = veto.apply(spot);
        if (vetoed != null) {
            return vetoed;
        }
        if (surface.checked() && !surface.ground().contains(groundOf(spot.getBlock().getRelative(BlockFace.DOWN)))) {
            return "not natural ground";
        }
        return null;
    }

    // Heights are compared only between natural ground blocks: a tree trunk or a house in the square
    // counts against min-ground, but does not make the spot look like a cliff.
    private String areaProblem(World target, int centerX, int centerZ) {
        int radius = surface.radius();
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        int natural = 0;
        int total = 0;
        for (int dx : offsets(radius)) {
            for (int dz : offsets(radius)) {
                Block top = target.getHighestBlockAt(centerX + dx, centerZ + dz, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                total++;
                if (top.isLiquid() || top.getBlockData() instanceof Waterlogged wet && wet.isWaterlogged()) {
                    return "water or lava nearby";
                }
                if (surface.ground().contains(groundOf(top))) {
                    natural++;
                    lowest = Math.min(lowest, top.getY());
                    highest = Math.max(highest, top.getY());
                }
            }
        }
        if (natural < total * surface.minGround()) {
            return "trees, rock or builds instead of ground";
        }
        if (highest - lowest > surface.maxHeightDifference()) {
            return "slope or cliff";
        }
        return null;
    }

    // Water under a build is covered by it, and the floor is put at the middle height, so only
    // how far the ground strays from it matters.
    private AreaVerdict fitProblem(World target, int centerX, int centerZ, GroundFit build) {
        int radius = surface.radius();
        List<Integer> heights = new ArrayList<>();
        int natural = 0;
        int wet = 0;
        int total = 0;
        for (int dx : offsets(radius)) {
            for (int dz : offsets(radius)) {
                Block top = target.getHighestBlockAt(centerX + dx, centerZ + dz, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                total++;
                if (top.isLiquid() || top.getBlockData() instanceof Waterlogged water && water.isWaterlogged()) {
                    wet++;
                } else if (surface.ground().contains(groundOf(top))) {
                    natural++;
                    heights.add(top.getY());
                }
            }
        }
        if (wet > total * build.maxWater()) {
            return new AreaVerdict("too much water", 0);
        }
        if (natural < (total - wet) * surface.minGround() || heights.isEmpty()) {
            return new AreaVerdict("trees, rock or builds instead of ground", 0);
        }
        Collections.sort(heights);
        int skip = (int) (heights.size() * GroundFit.OUTLIERS);
        int floor = heights.get(heights.size() / 2);
        if (floor - heights.get(skip) > build.fillDepth()) {
            return new AreaVerdict("ground too far below the build", 0);
        }
        if (heights.get(heights.size() - 1 - skip) - floor > build.cutHeight()) {
            return new AreaVerdict("hill too high inside the build", 0);
        }
        return new AreaVerdict(null, floor);
    }

    private static List<Integer> offsets(int radius) {
        List<Integer> offsets = new ArrayList<>();
        for (int d = -radius; d < radius; d += SAMPLE_STEP) {
            offsets.add(d);
        }
        offsets.add(radius);
        return offsets;
    }

    private CompletableFuture<Void> loadArea(World target, int x, int z) {
        int radius = surface.radius();
        List<CompletableFuture<?>> loads = new ArrayList<>();
        for (int chunkX = (x - radius) >> 4; chunkX <= (x + radius) >> 4; chunkX++) {
            for (int chunkZ = (z - radius) >> 4; chunkZ <= (z + radius) >> 4; chunkZ++) {
                loads.add(target.getChunkAtAsync(chunkX, chunkZ));
            }
        }
        return CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new));
    }

    private static String summary(Map<String, Integer> rejected) {
        return rejected.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(entry -> entry.getKey() + " " + entry.getValue())
                .collect(Collectors.joining(", "));
    }

    private record AreaVerdict(String problem, int floor) {
    }

    // A thin snow layer lies on top of the real ground in snowy biomes.
    private static Material groundOf(Block top) {
        return top.getType() == Material.SNOW ? top.getRelative(BlockFace.DOWN).getType() : top.getType();
    }

    // Null over water, lava, the void or outside the world border.
    public static Location surface(World world, int x, int z) {
        Block ground = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        boolean standable = ground.getType().isSolid() || ground.getType() == Material.SNOW;
        if (ground.isLiquid() || !standable || ground.getY() <= world.getMinHeight()) {
            return null;
        }
        Location spot = ground.getLocation().add(0.5, 1, 0.5);
        return world.getWorldBorder().isInside(spot) ? spot : null;
    }
}
