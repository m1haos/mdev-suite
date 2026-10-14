package com.mdevstudio.eventide.action;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.mdevstudio.core.scenario.ActionFactory;
import com.mdevstudio.core.scenario.ScenarioAction;
import com.mdevstudio.core.util.WeightedPool;
import com.mdevstudio.eventide.EventidePlugin;
import com.mdevstudio.eventide.EventideSettings;
import com.mdevstudio.eventide.loot.Rarity;
import com.mdevstudio.eventide.running.ActiveEvent;
import com.mdevstudio.eventide.running.EntityRole;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;

/** Mobs that guard the caches and drop keys. Sends {@code guards-killed} when the last one dies. */
final class SpawnGuardsAction implements ActionFactory {

    static final String SIGNAL = "guards-killed";

    private final EventidePlugin plugin;

    SpawnGuardsAction(EventidePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public ScenarioAction create(ConfigurationSection settings) {
        MobSpec mob = MobSpec.parse(settings, MobKind.parse(plugin, settings.getString("entity", "zombie")),
                NamedTextColor.GOLD);
        int count = Math.max(1, settings.getInt("count", 5));
        int spread = Math.max(1, settings.getInt("spread", 8));
        int points = settings.getInt("points", 2);
        ConfigurationSection drops = settings.getConfigurationSection("key-drops");
        String spawnEffect = settings.getString("effects.spawn");
        String deathEffect = settings.getString("effects.death");
        String keyDropEffect = settings.getString("effects.key-drop");

        return EventActions.forEvent(event -> {
            if (!plugin.settings().modules().guards()) {
                return;
            }
            // Looked up at spawn time, so a reload of rarities.yml reaches the next event.
            WeightedPool<Rarity> keyDrops = new WeightedPool<>();
            if (drops != null) {
                for (String rarityId : drops.getKeys(false)) {
                    plugin.rarity(rarityId).filter(Rarity::locked)
                            .ifPresent(rarity -> keyDrops.add(rarity, drops.getDouble(rarityId)));
                }
            }
            Squad squad = new Squad(event, keyDrops, points, deathEffect, keyDropEffect);
            for (int i = 0; i < count; i++) {
                Location spot = Spots.randomSurface(event.location(), spread);
                if (spot != null) {
                    squad.add(mob.spawn(event, spot, plugin.text()));
                    plugin.playEffect(spawnEffect, spot);
                }
            }
        });
    }

    private final class Squad implements EntityRole {

        private final ActiveEvent event;
        private final WeightedPool<Rarity> keyDrops;
        private final int points;
        private final String deathEffect;
        private final String keyDropEffect;
        private final Set<UUID> guards = new HashSet<>();

        Squad(ActiveEvent event, WeightedPool<Rarity> keyDrops, int points, String deathEffect, String keyDropEffect) {
            this.event = event;
            this.keyDrops = keyDrops;
            this.points = points;
            this.deathEffect = deathEffect;
            this.keyDropEffect = keyDropEffect;
        }

        void add(LivingEntity guard) {
            if (guards.isEmpty()) {
                // Guards can vanish without dying: peaceful difficulty, a cleanup plugin, a cancelled spawn.
                event.every(20, this::dropVanished);
            }
            guards.add(guard.getUniqueId());
            event.claim(guard, this);
        }

        private void dropVanished() {
            if (guards.removeIf(id -> {
                Entity guard = Bukkit.getEntity(id);
                return guard == null || !guard.isValid();
            }) && guards.isEmpty()) {
                event.signal(SIGNAL);
            }
        }

        @Override
        public void died(EntityDeathEvent death, Player killer) {
            Location at = death.getEntity().getLocation();
            event.release(death.getEntity());
            death.getDrops().clear();
            guards.remove(death.getEntity().getUniqueId());
            plugin.playEffect(deathEffect, at);
            if (killer != null) {
                event.score().add(killer, points);
            }
            EventideSettings.Keys keys = plugin.settings().modules().keys();
            if (keys.enabled() && keys.fromGuards() && !keyDrops.isEmpty()) {
                death.getDrops().add(plugin.keys().create(keyDrops.pick(), 1));
                plugin.playEffect(keyDropEffect, at);
            }
            if (guards.isEmpty()) {
                event.signal(SIGNAL);
            }
        }
    }
}
