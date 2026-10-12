package com.mdevstudio.eventide;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.mdevstudio.core.MDevCore;
import com.mdevstudio.core.config.Messages;
import com.mdevstudio.core.config.YamlFile;
import com.mdevstudio.core.text.Text;
import com.mdevstudio.eventide.action.EventActions;
import com.mdevstudio.eventide.command.EventideCommand;
import com.mdevstudio.eventide.definition.EventDefinition;
import com.mdevstudio.eventide.definition.EventLoader;
import com.mdevstudio.eventide.hook.Claims;
import com.mdevstudio.eventide.hook.EventidePlaceholders;
import com.mdevstudio.eventide.listener.EventListener;
import com.mdevstudio.eventide.listener.RegionListener;
import com.mdevstudio.eventide.loot.KeyItems;
import com.mdevstudio.eventide.loot.KeyReward;
import com.mdevstudio.eventide.loot.LootLoader;
import com.mdevstudio.eventide.loot.LootTable;
import com.mdevstudio.eventide.loot.Rarity;
import com.mdevstudio.eventide.loot.RarityLoader;
import com.mdevstudio.eventide.running.EventManager;
import com.mdevstudio.eventide.running.MobGlow;
import com.mdevstudio.eventide.running.PlacedStructure;
import com.mdevstudio.eventide.running.EventScheduler;
import com.mdevstudio.eventide.running.EventStats;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

public final class EventidePlugin extends JavaPlugin {

    // How many places of each leaderboard placeholders can show.
    private static final int TOP_SIZE = 10;

    private EventideSettings settings;
    private Messages messages;
    private Map<String, Rarity> rarities = Map.of();
    private Map<String, LootTable> lootTables = Map.of();
    private Map<String, EventDefinition> definitions = Map.of();
    private EventManager manager;
    private EventScheduler scheduler;
    private KeyItems keys;
    private Claims claims = Claims.none();

    @Override
    public void onEnable() {
        // Actions and the key reward are parsed from event and loot files, so they come before reload().
        EventActions.registerAll(MDevCore.actions(), this);
        MDevCore.rewards().register("key", value -> KeyReward.parse(this, value));
        manager = new EventManager(this);
        // Before anything can start: an area left from a server stop during an event goes back first.
        PlacedStructure.restoreLeftovers(this);
        // Glow teams live in the world's scoreboard file and outlast a crash.
        MobGlow.clearAll();
        scheduler = new EventScheduler(this);
        keys = new KeyItems(this, text());
        reload();

        getServer().getPluginManager().registerEvents(new EventListener(this), this);
        getServer().getPluginManager().registerEvents(new RegionListener(this), this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar()
                .register(new EventideCommand(this).build(), "Server events", settings.commands().aliases()));
        MDevCore.stats().ifPresent(stats -> {
            stats.trackTop(EventStats.WINS, TOP_SIZE);
            stats.trackTop(EventStats.POINTS, TOP_SIZE);
        });
        // Toggling placeholders needs a restart: PlaceholderAPI keeps expansions until it reloads itself.
        if (settings.modules().placeholders() && getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            EventidePlaceholders.register(this);
        }
        getSLF4JLogger().info("Loaded {} events and {} loot tables; claims checked with: {}", definitions.size(),
                lootTables.size(), claims.plugins().isEmpty() ? "no protection plugin found" : claims.plugins());
    }

    @Override
    public void onDisable() {
        if (scheduler != null) {
            scheduler.stop();
        }
        if (manager != null) {
            manager.stopAll();
        }
        MobGlow.clearAll();
    }

    // Events already running keep the definitions they started with.
    public void reload() {
        settings = EventideSettings.from(YamlFile.load(this, "config.yml"), getSLF4JLogger());
        messages = Messages.load(this, text(), settings.language(), settings.useClientLanguage());
        rarities = RarityLoader.load(this);
        reloadLoot();
        claims = settings.modules().claims() ? Claims.detect(this, settings.ignoredWorldGuardRegions()) : Claims.none();
        definitions = EventLoader.loadAll(this, settings);
        definitions.values().stream()
                .filter(definition -> !lootTables.containsKey(definition.lootTable()))
                .forEach(definition -> getSLF4JLogger().warn("events/{}.yml uses loot table '{}' that does not exist",
                        definition.id(), definition.lootTable()));
        if (settings.modules().scheduler()) {
            scheduler.start();
        } else {
            scheduler.stop();
        }
    }

    /** Rereads the loot folder. Running events keep the tables they started with. */
    public void reloadLoot() {
        lootTables = LootLoader.loadAll(this, rarities);
    }

    // Effects tied to caches, guards and keys follow the plugin's own switch as well as the core one.
    public void playEffect(String id, Location at) {
        playEffect(id, at, 1);
    }

    public void playEffect(String id, Location at, double scale) {
        if (settings.modules().effects()) {
            MDevCore.effects().play(id, at, scale);
        }
    }

    public Optional<Rarity> rarity(String id) {
        return Optional.ofNullable(rarities.get(id.toLowerCase(Locale.ROOT)));
    }

    public Map<String, Rarity> rarities() {
        return rarities;
    }

    public Text text() {
        return MDevCore.text();
    }

    public EventideSettings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public Map<String, LootTable> lootTables() {
        return lootTables;
    }

    public Map<String, EventDefinition> definitions() {
        return definitions;
    }

    public EventManager manager() {
        return manager;
    }

    public EventScheduler scheduler() {
        return scheduler;
    }

    public Claims claims() {
        return claims;
    }

    public KeyItems keys() {
        return keys;
    }
}
