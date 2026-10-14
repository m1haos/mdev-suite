package com.mdevstudio.eventide.running;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import com.mdevstudio.core.util.WeightedPool;
import com.mdevstudio.eventide.EventidePlugin;
import com.mdevstudio.eventide.definition.EventDefinition;
import com.mdevstudio.eventide.definition.RandomSpec;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

/**
 * Starts events with a "random" block on a jittered timer: first a rarity by its chance, then an
 * event of that rarity by weight. The timer, the pause and the cooldowns are kept in a file, so a
 * restart neither resets cooldowns nor fires a roll the moment the server is up.
 */
public final class RandomEvents {

    public enum Skip { OUTSIDE_WINDOWS, FEW_PLAYERS, TOO_MANY_RUNNING, NOTHING_AVAILABLE, START_FAILED }

    /** What a roll did: the event it started, or why it started nothing. */
    public record Roll(EventDefinition event, String rarity, Skip skip, String reason) {

        static Roll skipped(Skip skip) {
            return new Roll(null, null, skip, null);
        }
    }

    private static final long CHECK_PERIOD_TICKS = 20 * 20;
    private static final String STATE_FILE = "random-state.yml";

    private final EventidePlugin plugin;
    private final File stateFile;
    private final Map<String, Instant> rarityCooldowns = new HashMap<>();
    private final Map<String, Instant> eventCooldowns = new HashMap<>();
    // Rarities of random starts still looking for a spot; their cooldown begins once the event is up.
    private final Map<String, String> pending = new HashMap<>();
    private Instant nextRoll;
    private boolean paused;
    private boolean loaded;
    private BukkitTask task;

    public RandomEvents(EventidePlugin plugin) {
        this.plugin = plugin;
        this.stateFile = new File(plugin.getDataFolder(), STATE_FILE);
    }

    public void start() {
        stop();
        if (!loaded) {
            loadState();
            // A roll missed while the server was down is not made up for: the first one waits first-delay.
            Instant earliest = Instant.now().plus(settings().firstDelay());
            if (nextRoll == null || nextRoll.isBefore(earliest)) {
                nextRoll = earliest;
            }
            loaded = true;
        }
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, CHECK_PERIOD_TICKS,
                CHECK_PERIOD_TICKS);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public boolean running() {
        return task != null;
    }

    public boolean paused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
        if (!paused && nextRoll != null && nextRoll.isBefore(Instant.now())) {
            nextRoll = Instant.now().plus(nextInterval());
        }
        saveState();
    }

    public Instant nextRoll() {
        return nextRoll;
    }

    /** Time left before the next roll, or empty while the module is off or paused. */
    public Optional<Duration> untilNextRoll() {
        if (!running() || paused || nextRoll == null) {
            return Optional.empty();
        }
        Duration left = Duration.between(Instant.now(), nextRoll);
        return Optional.of(left.isNegative() ? Duration.ZERO : left);
    }

    public Map<String, Duration> rarityCooldowns() {
        return remaining(rarityCooldowns);
    }

    public Map<String, Duration> eventCooldowns() {
        return remaining(eventCooldowns);
    }

    /**
     * Rolls now. A forced roll comes from an admin: it skips the windows and the online limits,
     * but still respects cooldowns and leaves the timer alone.
     */
    public Roll roll(boolean forced) {
        RandomSettings settings = settings();
        ZonedDateTime now = ZonedDateTime.now(plugin.settings().timezone());
        int online = plugin.getServer().getOnlinePlayers().size();
        if (!forced && !settings.allowsTime(now.toLocalTime())) {
            return Roll.skipped(Skip.OUTSIDE_WINDOWS);
        }
        if (!forced && online < settings.minOnline()) {
            return Roll.skipped(Skip.FEW_PLAYERS);
        }
        if (plugin.manager().active().size() + pending.size() >= settings.maxConcurrent()) {
            return Roll.skipped(Skip.TOO_MANY_RUNNING);
        }

        Map<String, WeightedPool<EventDefinition>> byRarity = new LinkedHashMap<>();
        for (EventDefinition definition : plugin.definitions().values()) {
            if (definition.random().isEmpty()) {
                continue;
            }
            RandomSpec spec = definition.random().get();
            RandomSettings.Tier tier = settings.tiers().get(spec.rarity());
            if (tier == null || onCooldown(rarityCooldowns, tier.id()) || onCooldown(eventCooldowns, definition.id())
                    || plugin.manager().get(definition.id()).isPresent() || pending.containsKey(definition.id())) {
                continue;
            }
            int needed = Math.max(definition.minOnline(), Math.max(spec.minOnline(), tier.minOnline()));
            if (!forced && online < needed) {
                continue;
            }
            byRarity.computeIfAbsent(tier.id(), id -> new WeightedPool<>()).add(definition, spec.weight());
        }
        if (byRarity.isEmpty()) {
            return Roll.skipped(Skip.NOTHING_AVAILABLE);
        }

        WeightedPool<String> rarities = new WeightedPool<>();
        byRarity.keySet().forEach(id -> rarities.add(id, settings.tiers().get(id).chance()));
        String rarity = rarities.pick();
        EventDefinition picked = byRarity.get(rarity).pick();

        String id = picked.id();
        pending.put(id, rarity);
        EventManager.StartResult started = plugin.manager().start(picked, reason -> {
            pending.remove(id);
            plugin.getSLF4JLogger().warn("Random event {} did not start: {}", id, reason);
        });
        if (started != EventManager.StartResult.STARTING) {
            pending.remove(id);
            return new Roll(picked, rarity, Skip.START_FAILED, started.name().toLowerCase(Locale.ROOT).replace('_', ' '));
        }
        return new Roll(picked, rarity, null, null);
    }

    // Any start counts, scheduled and manual too: a random roll should not repeat an event that just ended.
    void eventStarted(EventDefinition definition) {
        Instant now = Instant.now();
        definition.random()
                .filter(spec -> spec.cooldown().isPositive())
                .ifPresent(spec -> eventCooldowns.put(definition.id(), now.plus(spec.cooldown())));
        String rarity = pending.remove(definition.id());
        RandomSettings.Tier tier = rarity == null ? null : settings().tiers().get(rarity);
        if (tier != null && tier.cooldown().isPositive()) {
            rarityCooldowns.put(rarity, now.plus(tier.cooldown()));
        }
        saveState();
    }

    private void tick() {
        if (paused || Instant.now().isBefore(nextRoll)) {
            return;
        }
        nextRoll = Instant.now().plus(nextInterval());
        saveState();
        Roll roll = roll(false);
        if (roll.skip() == null) {
            plugin.getSLF4JLogger().info("Random roll: {} ({})", roll.event().id(), roll.rarity());
        } else if (roll.skip() == Skip.START_FAILED) {
            plugin.getSLF4JLogger().warn("Random roll picked {}, but it did not start: {}", roll.event().id(),
                    roll.reason());
        } else {
            plugin.getSLF4JLogger().info("Random roll skipped: {}", roll.skip().name().toLowerCase(Locale.ROOT).replace('_', ' '));
        }
    }

    private Duration nextInterval() {
        RandomSettings settings = settings();
        double spread = settings.jitter() * (ThreadLocalRandom.current().nextDouble() * 2 - 1);
        return Duration.ofSeconds(Math.round(settings.interval().toSeconds() * (1 + spread)));
    }

    private RandomSettings settings() {
        return plugin.settings().random();
    }

    private static boolean onCooldown(Map<String, Instant> cooldowns, String id) {
        Instant until = cooldowns.get(id);
        return until != null && until.isAfter(Instant.now());
    }

    private static Map<String, Duration> remaining(Map<String, Instant> cooldowns) {
        Instant now = Instant.now();
        Map<String, Duration> left = new LinkedHashMap<>();
        cooldowns.entrySet().stream()
                .filter(entry -> entry.getValue().isAfter(now))
                .sorted(Map.Entry.comparingByValue())
                .forEach(entry -> left.put(entry.getKey(), Duration.between(now, entry.getValue())));
        return left;
    }

    private void loadState() {
        if (!stateFile.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(stateFile);
        long next = yaml.getLong("next-roll", 0);
        nextRoll = next > 0 ? Instant.ofEpochMilli(next) : null;
        paused = yaml.getBoolean("paused", false);
        readCooldowns(yaml.getConfigurationSection("rarity-cooldowns"), rarityCooldowns);
        readCooldowns(yaml.getConfigurationSection("event-cooldowns"), eventCooldowns);
    }

    private void saveState() {
        Instant now = Instant.now();
        rarityCooldowns.values().removeIf(until -> !until.isAfter(now));
        eventCooldowns.values().removeIf(until -> !until.isAfter(now));
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of("Timer and cooldowns of random events. Delete the file to reset them."));
        yaml.set("next-roll", nextRoll == null ? 0 : nextRoll.toEpochMilli());
        yaml.set("paused", paused);
        rarityCooldowns.forEach((id, until) -> yaml.set("rarity-cooldowns." + id, until.toEpochMilli()));
        eventCooldowns.forEach((id, until) -> yaml.set("event-cooldowns." + id, until.toEpochMilli()));
        try {
            yaml.save(stateFile);
        } catch (IOException e) {
            plugin.getSLF4JLogger().error("Could not save {}", STATE_FILE, e);
        }
    }

    private static void readCooldowns(ConfigurationSection section, Map<String, Instant> into) {
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            into.put(id, Instant.ofEpochMilli(section.getLong(id)));
        }
    }
}
