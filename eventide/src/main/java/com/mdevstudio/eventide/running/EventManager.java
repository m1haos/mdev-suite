package com.mdevstudio.eventide.running;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.mdevstudio.eventide.EventidePlugin;
import com.mdevstudio.eventide.api.EventideStartEvent;
import com.mdevstudio.eventide.definition.EventDefinition;
import com.mdevstudio.eventide.definition.RegionFlag;
import com.mdevstudio.eventide.loot.LootTable;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public final class EventManager {

    public enum StartResult { STARTING, ALREADY_RUNNING, TOO_MANY_ACTIVE, UNKNOWN_LOOT_TABLE }

    // In chunks, each direction from the event spot. Covers caches spread up to about 24 blocks;
    // events with a larger area hold as many chunks as their area needs.
    private static final int HELD_CHUNK_RADIUS = 2;
    // Blocks of free ground between the areas of two events, so their mobs and builds do not mix.
    private static final int AREA_GAP = 8;

    private final EventidePlugin plugin;
    private final Map<String, ActiveEvent> active = new LinkedHashMap<>();
    private final Set<String> starting = new HashSet<>();
    private final Map<ChunkKey, Integer> heldChunks = new HashMap<>();
    private final List<PvpProtection> pvpAfterEnd = new ArrayList<>();
    private final TakenAreas takenAreas;

    public EventManager(EventidePlugin plugin) {
        this.plugin = plugin;
        this.takenAreas = new TakenAreas(plugin);
        takenAreas.clearOwn();
    }

    /** The region of an ended event, where players still cannot hurt each other until {@code until}. */
    public record PvpProtection(Area region, TagResolver placeholders, long until) {

        public long secondsLeft() {
            return Math.max(1, (until - System.currentTimeMillis() + 999) / 1000);
        }
    }

    // The spot is found asynchronously; onFailure tells a waiting command sender why nothing happened.
    public StartResult start(EventDefinition definition, Consumer<String> onFailure) {
        String id = definition.id();
        if (active.containsKey(id) || starting.contains(id)) {
            return StartResult.ALREADY_RUNNING;
        }
        if (active.size() + starting.size() >= plugin.settings().maxActive()) {
            return StartResult.TOO_MANY_ACTIVE;
        }
        LootTable lootTable = plugin.lootTables().get(definition.lootTable());
        if (lootTable == null) {
            return StartResult.UNKNOWN_LOOT_TABLE;
        }

        starting.add(id);
        definition.location().find(spot -> problemWith(spot, definition))
                .thenCompose(location -> holdChunks(location, definition).thenApply(loaded -> location))
                .whenComplete((location, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    starting.remove(id);
                    if (error != null || !plugin.isEnabled()) {
                        String reason = error == null ? "plugin disabled" : rootMessage(error);
                        plugin.getSLF4JLogger().warn("Event {} did not start: {}", id, reason);
                        onFailure.accept(reason);
                        return;
                    }
                    // Another event may have started there, or land got claimed, while the chunks were loading.
                    String problem = problemWith(location, definition);
                    if (problem != null) {
                        releaseChunks(location, definition);
                        onFailure.accept("the spot is no longer free (" + problem + "), try again");
                        return;
                    }
                    if (!new EventideStartEvent(id, location).callEvent()) {
                        releaseChunks(location, definition);
                        onFailure.accept("cancelled by another plugin");
                        return;
                    }
                    PlacedStructure build;
                    try {
                        build = definition.structure().isPresent()
                                ? PlacedStructure.place(plugin, id, definition.structure().get(), location)
                                : null;
                    } catch (IOException e) {
                        releaseChunks(location, definition);
                        plugin.getSLF4JLogger().error("Event {} did not start: could not back up the area for "
                                + "its build", id, e);
                        onFailure.accept("could not back up the area for the build: " + e.getMessage());
                        return;
                    }
                    ActiveEvent event = new ActiveEvent(plugin, definition, lootTable, location, build);
                    active.put(id, event);
                    takenAreas.mark(id, location, event.reach());
                    plugin.randomEvents().eventStarted(definition);
                    event.begin();
                }));
        return StartResult.STARTING;
    }

    public boolean stop(String id) {
        ActiveEvent event = active.get(id);
        if (event == null) {
            return false;
        }
        event.abort();
        return true;
    }

    public void stopAll() {
        new ArrayList<>(active.values()).forEach(ActiveEvent::abort);
    }

    void remove(ActiveEvent event) {
        active.remove(event.definition().id(), event);
        takenAreas.unmark(event.definition().id(), event.location().getWorld());
        long now = System.currentTimeMillis();
        pvpAfterEnd.removeIf(protection -> protection.until() <= now);
        long protection = event.definition().region().pvpAfterEnd().toMillis();
        if (protection > 0) {
            pvpAfterEnd.add(new PvpProtection(event.region(), event.placeholders(), now + protection));
        }
        releaseChunks(event.location(), event.definition());
    }

    Set<Block> claimedBlocks() {
        Set<Block> claimed = new HashSet<>();
        active.values().forEach(event -> claimed.addAll(event.claimedBlocks()));
        return claimed;
    }

    public Collection<ActiveEvent> active() {
        return List.copyOf(active.values());
    }

    public Optional<ActiveEvent> get(String id) {
        return Optional.ofNullable(active.get(id));
    }

    public Optional<BlockRole> roleOf(Block block) {
        return active.values().stream().flatMap(event -> event.roleOf(block).stream()).findFirst();
    }

    public Optional<EntityRole> roleOf(Entity entity) {
        return active.values().stream().flatMap(event -> event.roleOf(entity).stream()).findFirst();
    }

    public boolean isProtected(Block block) {
        return roleOf(block).isPresent();
    }

    public boolean inBuild(Location at, int margin) {
        return active.values().stream().anyMatch(event -> event.inBuild(at, margin));
    }

    public Optional<ActiveEvent> forbidding(Location at, RegionFlag flag) {
        return active.values().stream().filter(event -> event.forbids(at, flag)).findFirst();
    }

    public Optional<PvpProtection> pvpAfterEnd(Location at) {
        long now = System.currentTimeMillis();
        return pvpAfterEnd.stream()
                .filter(protection -> protection.until() > now && protection.region().contains(at))
                .findFirst();
    }

    public boolean keepsInventory(Location at) {
        return active.values().stream().anyMatch(event -> event.keepsInventory(at));
    }

    public Optional<ActiveEvent> blockingCommand(Location at, String command) {
        return active.values().stream().filter(event -> event.blocksCommand(at, command)).findFirst();
    }

    // The region must stay off claimed land: it forbids building there, owners included.
    private String problemWith(Location spot, EventDefinition definition) {
        if (overlapsActive(spot, definition)) {
            return "too close to another event";
        }
        int reach = definition.region().radius() + plugin.settings().claimMargin();
        String claimedBy = plugin.claims().claimedBy(spot, reach);
        return claimedBy == null ? null : "claimed land (" + claimedBy + ")";
    }

    private boolean overlapsActive(Location spot, EventDefinition definition) {
        int reach = definition.location().surface().radius();
        return active.values().stream().anyMatch(event -> {
            Location other = event.location();
            int distance = Math.max(Math.abs(other.getBlockX() - spot.getBlockX()),
                    Math.abs(other.getBlockZ() - spot.getBlockZ()));
            return other.getWorld().equals(spot.getWorld()) && distance < reach + event.reach() + AREA_GAP;
        }) || takenAreas.takenByOthers(spot, reach, AREA_GAP);
    }

    public void playerJoined(Player player) {
        active.values().forEach(event -> event.playerJoined(player));
    }

    // Keeps the area loaded for the whole event. Otherwise it unloads while players are still on
    // their way, and the guards are discarded along with it. Holds are counted per chunk, so two
    // events with overlapping areas do not unload each other's chunks.
    private CompletableFuture<Void> holdChunks(Location center, EventDefinition definition) {
        World world = center.getWorld();
        List<CompletableFuture<?>> loads = new ArrayList<>();
        forEachHeldChunk(center, definition, (x, z) -> loads.add(world.getChunkAtAsync(x, z).thenAccept(chunk -> {
            if (heldChunks.merge(new ChunkKey(world.getUID(), x, z), 1, Integer::sum) == 1) {
                chunk.addPluginChunkTicket(plugin);
            }
        })));
        return CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new));
    }

    private void releaseChunks(Location center, EventDefinition definition) {
        World world = center.getWorld();
        forEachHeldChunk(center, definition, (x, z) -> {
            ChunkKey key = new ChunkKey(world.getUID(), x, z);
            Integer left = heldChunks.computeIfPresent(key, (chunk, holds) -> holds > 1 ? holds - 1 : null);
            if (left == null) {
                world.removePluginChunkTicket(x, z, plugin);
            }
        });
    }

    private static void forEachHeldChunk(Location center, EventDefinition definition, ChunkConsumer action) {
        int chunkX = center.getBlockX() >> 4;
        int chunkZ = center.getBlockZ() >> 4;
        int radius = Math.max(HELD_CHUNK_RADIUS, (definition.location().surface().radius() >> 4) + 1);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                action.accept(chunkX + dx, chunkZ + dz);
            }
        }
    }

    @FunctionalInterface
    private interface ChunkConsumer {
        void accept(int x, int z);
    }

    private record ChunkKey(UUID world, int x, int z) {
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }
}
