package com.mdevstudio.eventide.action;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mdevstudio.core.scenario.ActionFactory;
import com.mdevstudio.core.scenario.ScenarioAction;
import com.mdevstudio.core.util.Durations;
import com.mdevstudio.core.util.Sections;
import com.mdevstudio.eventide.EventidePlugin;
import com.mdevstudio.eventide.running.ActiveEvent;
import com.mdevstudio.eventide.running.EntityRole;
import com.mdevstudio.eventide.running.MobGlow;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Waves of mobs around the spot. The next wave comes after the previous one is cleared and a short
 * pause. Sends {@code wave-cleared} after each wave and {@code waves-cleared} after the last.
 */
final class WavesAction implements ActionFactory {

    static final String WAVE_SIGNAL = "wave-cleared";
    static final String ALL_SIGNAL = "waves-cleared";
    private static final String SLOT = "waves";

    private final EventidePlugin plugin;

    private record Wave(Map<EntityType, Integer> mobs, MobSpec spec) {

        int size() {
            return mobs.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    private record Settings(
            List<Wave> waves,
            int spread,
            long pauseTicks,
            int pointsPerKill,
            boolean vanillaDrops,
            boolean signals,
            boolean messages,
            int glowWhenLeft,
            BarStyle bar,
            String startEffect,
            String spawnEffect,
            String deathEffect) {
    }

    WavesAction(EventidePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public ScenarioAction create(ConfigurationSection section) {
        List<Map<?, ?>> rawWaves = section.getMapList("list");
        if (rawWaves.isEmpty()) {
            throw new IllegalArgumentException("waves: 'list' needs at least one wave");
        }
        // A colour for every wave, which a wave can still change with its own "glow".
        NamedTextColor glow = MobSpec.parseGlow(section, NamedTextColor.RED);
        List<Wave> waves = new ArrayList<>();
        for (int i = 0; i < rawWaves.size(); i++) {
            try {
                waves.add(parseWave(Sections.fromMap(rawWaves.get(i)), glow));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("waves: wave " + (i + 1) + ": " + e.getMessage(), e);
            }
        }
        Settings settings = new Settings(
                List.copyOf(waves),
                Math.max(2, section.getInt("spread", 12)),
                Durations.toTicks(Durations.parse(section.getString("pause", "8s"))),
                section.getInt("points-per-kill", 1),
                section.getBoolean("vanilla-drops", false),
                section.getBoolean("signals", true),
                section.getBoolean("messages", true),
                Math.max(0, section.getInt("glow-when-left", 0)),
                BarStyle.parse(section, "red", 96),
                section.getString("effects.wave-start"),
                section.getString("effects.spawn"),
                section.getString("effects.death"));

        return EventActions.forEvent(event -> new Siege(event, settings).nextWave());
    }

    private static Wave parseWave(ConfigurationSection wave, NamedTextColor glow) {
        ConfigurationSection mobs = wave.getConfigurationSection("mobs");
        if (mobs == null || mobs.getKeys(false).isEmpty()) {
            throw new IllegalArgumentException("'mobs' is required, e.g. {zombie: 6, skeleton: 2}");
        }
        Map<EntityType, Integer> counts = new LinkedHashMap<>();
        for (String type : mobs.getKeys(false)) {
            counts.put(MobSpec.entityType(type), Math.max(0, mobs.getInt(type)));
        }
        return new Wave(counts, MobSpec.parse(wave, EntityType.ZOMBIE, glow));
    }

    private final class Siege implements EntityRole {

        private final ActiveEvent event;
        private final Settings settings;
        private final Set<UUID> alive = new HashSet<>();
        private BossBar bar;
        private int wave;
        private int waveSize;
        // Set while a wave waits for glow-when-left before lighting up its last mobs.
        private NamedTextColor delayedGlow;

        Siege(ActiveEvent event, Settings settings) {
            this.event = event;
            this.settings = settings;
        }

        void nextWave() {
            Wave current = settings.waves().get(wave++);
            Location center = event.location();
            boolean delayGlow = settings.glowWhenLeft() > 0 && current.spec().glow() != null;
            delayedGlow = delayGlow ? current.spec().glow() : null;
            plugin.playEffect(settings.startEffect(), center);
            broadcast("waves.started");
            current.mobs().forEach((type, count) -> {
                MobSpec spec = delayGlow ? current.spec().withType(type).withoutGlow()
                        : current.spec().withType(type);
                for (int i = 0; i < count; i++) {
                    Location spot = Spots.randomSurface(center, settings.spread());
                    if (spot != null) {
                        LivingEntity mob = spec.spawn(event, spot, plugin.text());
                        alive.add(mob.getUniqueId());
                        event.claim(mob, this);
                        plugin.playEffect(settings.spawnEffect(), spot);
                    }
                }
            });
            waveSize = Math.max(1, alive.size());
            if (alive.isEmpty()) {
                // Nowhere to stand around the spot; move on instead of waiting for a wave that never came.
                event.later(1, this::afterLoss);
            }
            if (bar == null) {
                bar = settings.bar().create(title(), 1f);
                event.bars().show(SLOT, bar, settings.bar().radius());
                // Mobs can vanish without dying: pushed into the void, removed by another plugin.
                event.every(20, this::dropVanished);
            }
            afterCountChanged();
        }

        @Override
        public void died(EntityDeathEvent death, Player killer) {
            event.release(death.getEntity());
            plugin.playEffect(settings.deathEffect(), death.getEntity().getLocation());
            if (!settings.vanillaDrops()) {
                death.getDrops().clear();
                death.setDroppedExp(0);
            }
            if (killer != null) {
                event.score().add(killer, settings.pointsPerKill());
            }
            alive.remove(death.getEntity().getUniqueId());
            afterLoss();
        }

        private void dropVanished() {
            boolean changed = alive.removeIf(id -> {
                Entity mob = Bukkit.getEntity(id);
                return mob == null || !mob.isValid();
            });
            if (changed) {
                afterLoss();
            }
        }

        private void afterLoss() {
            afterCountChanged();
            if (!alive.isEmpty() || event.isFinished()) {
                return;
            }
            signal(WAVE_SIGNAL);
            if (wave >= settings.waves().size()) {
                event.bars().hide(SLOT, bar);
                broadcast("waves.cleared");
                signal(ALL_SIGNAL);
                return;
            }
            broadcast("waves.next", Placeholder.unparsed("seconds", Long.toString(settings.pauseTicks() / 20)));
            event.later(settings.pauseTicks(), this::nextWave);
        }

        private void afterCountChanged() {
            updateBar();
            if (delayedGlow != null && !alive.isEmpty() && alive.size() <= settings.glowWhenLeft()
                    && plugin.settings().modules().mobGlow()) {
                alive.forEach(id -> {
                    Entity mob = Bukkit.getEntity(id);
                    if (mob != null) {
                        MobGlow.apply(mob, delayedGlow);
                    }
                });
                delayedGlow = null;
            }
        }

        private void signal(String name) {
            if (settings.signals()) {
                event.signal(name);
            }
        }

        private void broadcast(String key, TagResolver... extra) {
            if (settings.messages()) {
                plugin.messages().broadcast(key, event.placeholders(), counters(), TagResolver.resolver(extra));
            }
        }

        private void updateBar() {
            bar.name(title());
            bar.progress(Math.clamp(alive.size() / (float) waveSize, 0f, 1f));
        }

        private Component title() {
            return plugin.messages().get(Bukkit.getConsoleSender(), "waves.bar",
                    TagResolver.resolver(event.placeholders(), counters()));
        }

        private TagResolver counters() {
            return TagResolver.resolver(
                    Placeholder.unparsed("wave", Integer.toString(wave)),
                    Placeholder.unparsed("waves", Integer.toString(settings.waves().size())),
                    Placeholder.unparsed("left", Integer.toString(alive.size())));
        }
    }
}
