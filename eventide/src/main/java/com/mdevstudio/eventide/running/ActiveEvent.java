package com.mdevstudio.eventide.running;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.mdevstudio.core.scenario.ScenarioContext;
import com.mdevstudio.core.scenario.ScenarioRun;
import com.mdevstudio.eventide.EventidePlugin;
import com.mdevstudio.eventide.definition.EventDefinition;
import com.mdevstudio.eventide.definition.RegionFlag;
import com.mdevstudio.eventide.definition.RegionRule;
import com.mdevstudio.eventide.loot.LootTable;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * One running event. Actions do not keep their state here: they claim blocks and mobs with roles
 * and register parts to stop, so a new mechanic never has to change this class.
 */
public final class ActiveEvent implements ScenarioContext {

    private final EventidePlugin plugin;
    private final EventDefinition definition;
    private final LootTable lootTable;
    private final Location location;
    private final TagResolver placeholders;
    private final EventScore score = new EventScore();
    private final EventBars bars;
    private final PlacedStructure build;
    private final Area region;

    private final Map<Block, BlockRole> blocks = new HashMap<>();
    private final Map<UUID, EntityRole> entities = new HashMap<>();
    private final List<EventPart> parts = new ArrayList<>();
    private ScenarioRun run;
    private boolean finished;

    // build is null when the event has no structure to roll back.
    ActiveEvent(EventidePlugin plugin, EventDefinition definition, LootTable lootTable, Location location,
            PlacedStructure build) {
        this.plugin = plugin;
        this.definition = definition;
        this.lootTable = lootTable;
        this.location = location;
        this.build = build;
        RegionRule rule = definition.region();
        this.region = Area.around(location, rule.radius(), rule.below(), rule.above());
        this.bars = new EventBars(location);
        this.placeholders = TagResolver.resolver(
                Placeholder.parsed("event", definition.displayName()),
                Placeholder.unparsed("world", location.getWorld().getName()),
                Placeholder.unparsed("x", Integer.toString(location.getBlockX())),
                Placeholder.unparsed("y", Integer.toString(location.getBlockY())),
                Placeholder.unparsed("z", Integer.toString(location.getBlockZ())));
    }

    void begin() {
        parts.add(bars);
        every(20, bars::refresh);
        run = definition.scenario().start(this, this::finish);
    }

    @Override
    public Plugin plugin() {
        return plugin;
    }

    public EventidePlugin eventide() {
        return plugin;
    }

    @Override
    public Location location() {
        return location.clone();
    }

    @Override
    public TagResolver placeholders() {
        return placeholders;
    }

    public EventDefinition definition() {
        return definition;
    }

    public LootTable lootTable() {
        return lootTable;
    }

    public EventScore score() {
        return score;
    }

    public EventBars bars() {
        return bars;
    }

    public boolean isFinished() {
        return finished;
    }

    /** How far the event area reaches sideways from the spot. */
    public int reach() {
        return definition.location().surface().radius();
    }

    /** Whether the place belongs to this event's build, or lies within {@code margin} blocks of it. */
    public boolean inBuild(Location at, int margin) {
        return build != null && build.contains(at, margin);
    }

    /** Forbidden by the region from the event file, or by the build, which forbids every change to itself. */
    public boolean forbids(Location at, RegionFlag flag) {
        return definition.region().deny().contains(flag) && region.contains(at)
                || RegionFlag.BUILD.contains(flag) && inBuild(at, 0);
    }

    public boolean blocksCommand(Location at, String command) {
        return definition.region().blocks(command) && region.contains(at);
    }

    public void claim(Block block, BlockRole role) {
        blocks.put(block, role);
    }

    /** Stops protecting the block without touching it, e.g. after it was opened and removed. */
    public void release(Block block) {
        blocks.remove(block);
    }

    Set<Block> claimedBlocks() {
        return Set.copyOf(blocks.keySet());
    }

    public Optional<BlockRole> roleOf(Block block) {
        return Optional.ofNullable(blocks.get(block));
    }

    public void claim(Entity entity, EntityRole role) {
        entities.put(entity.getUniqueId(), role);
    }

    public void release(Entity entity) {
        entities.remove(entity.getUniqueId());
        MobGlow.clear(entity.getUniqueId());
    }

    public Optional<EntityRole> roleOf(Entity entity) {
        return Optional.ofNullable(entities.get(entity.getUniqueId()));
    }

    public void addPart(EventPart part) {
        if (finished) {
            part.stop();
        } else {
            parts.add(part);
        }
    }

    /** Runs the task every {@code periodTicks} until the event ends. */
    public BukkitTask every(long periodTicks, Runnable task) {
        BukkitTask scheduled = plugin.getServer().getScheduler().runTaskTimer(plugin, task, periodTicks, periodTicks);
        addPart(scheduled::cancel);
        return scheduled;
    }

    /** Runs the task once after {@code delayTicks}, unless the event ends first. */
    public BukkitTask later(long delayTicks, Runnable task) {
        BukkitTask scheduled = plugin.getServer().getScheduler().runTaskLater(plugin, task, delayTicks);
        addPart(scheduled::cancel);
        return scheduled;
    }

    /**
     * Tells the event that something happened, e.g. {@code boss-killed}. Ends the event when the
     * signal is listed in {@code finish-on}, otherwise lets a stage waiting for it start.
     */
    public void signal(String name) {
        if (finished) {
            return;
        }
        String signal = name.toLowerCase(Locale.ROOT);
        if (definition.finishOn().contains(signal)) {
            finish();
        } else {
            run.signal(signal);
        }
    }

    void playerJoined(Player player) {
        bars.joined(player);
    }

    /** Ends the event normally: places, rewards and statistics. */
    public void finish() {
        end(true);
    }

    /**
     * Ends the event without results, for {@code /eventide stop} and server shutdown. Otherwise a
     * restart in the middle of an event would hand the current leader a win.
     */
    public void abort() {
        end(false);
    }

    private void end(boolean completed) {
        if (finished) {
            return;
        }
        finished = true;
        if (run != null) {
            run.cancel();
        }
        parts.forEach(EventPart::stop);
        parts.clear();
        blocks.keySet().forEach(block -> block.setType(Material.AIR));
        blocks.clear();
        entities.keySet().forEach(id -> {
            MobGlow.clear(id);
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        });
        entities.clear();
        if (build != null) {
            build.restore();
        }

        if (completed && !score.isEmpty()) {
            plugin.playEffect(definition.finishEffect(), location);
        }
        new EventResults(plugin, this).announce(completed);
        plugin.manager().remove(this);
    }
}
