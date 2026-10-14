package com.mdevstudio.eventide.action;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mdevstudio.core.MDevCore;
import com.mdevstudio.core.scenario.ActionFactory;
import com.mdevstudio.core.scenario.ScenarioAction;
import com.mdevstudio.core.util.Sections;
import com.mdevstudio.eventide.EventidePlugin;
import com.mdevstudio.eventide.running.ActiveEvent;
import com.mdevstudio.eventide.running.EntityRole;
import com.mdevstudio.eventide.running.EventStats;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * A boss with phases. Each phase starts when health drops to its {@code at} share, can change the
 * boss's stats and runs any stage actions. Points are split by damage dealt. Sends {@code boss-killed}.
 */
final class BossAction implements ActionFactory {

    static final String SIGNAL = "boss-killed";
    static final String LOST_SIGNAL = "boss-lost";
    private static final String SLOT = "boss";
    private static final int CHECK_TICKS = 5;

    private final EventidePlugin plugin;

    private record Phase(double at, double damage, double speed, String effect, List<ScenarioAction> actions) {
    }

    private record Settings(
            MobSpec mob,
            List<Phase> phases,
            int damagePoints,
            int killPoints,
            double leash,
            BarStyle bar,
            String spawnEffect,
            String deathEffect) {
    }

    BossAction(EventidePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public ScenarioAction create(ConfigurationSection section) {
        List<Phase> phases = new ArrayList<>();
        List<Map<?, ?>> rawPhases = section.getMapList("phases");
        for (int i = 0; i < rawPhases.size(); i++) {
            try {
                phases.add(parsePhase(Sections.fromMap(rawPhases.get(i))));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("boss: phase " + (i + 1) + ": " + e.getMessage(), e);
            }
        }
        // Phases fire from the highest share down, whatever order they were written in.
        phases.sort((a, b) -> Double.compare(b.at(), a.at()));
        Settings settings = new Settings(
                MobSpec.parse(section, MobKind.parse(plugin, section.getString("entity", "wither_skeleton")),
                        NamedTextColor.DARK_RED),
                List.copyOf(phases),
                section.getInt("points.damage", 50),
                section.getInt("points.kill", 10),
                section.getDouble("leash", 32),
                BarStyle.parse(section, "red", 96),
                section.getString("effects.spawn"),
                section.getString("effects.death"));

        return EventActions.forEvent(event -> new Boss(event, settings).spawn());
    }

    private static Phase parsePhase(ConfigurationSection phase) {
        String at = phase.getString("at", "");
        double share;
        try {
            share = Double.parseDouble(at.replace("%", "").trim()) / 100;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'at' must be a health share like 50%, not '" + at + "'");
        }
        if (share <= 0 || share >= 1) {
            throw new IllegalArgumentException("'at' must be between 0% and 100%");
        }
        List<ScenarioAction> actions = new ArrayList<>();
        for (Map<?, ?> raw : phase.getMapList("actions")) {
            actions.add(MDevCore.actions().create(Sections.fromMap(raw)));
        }
        return new Phase(share, phase.getDouble("damage", 0), phase.getDouble("speed", 0),
                phase.getString("effect"), List.copyOf(actions));
    }

    private final class Boss implements EntityRole {

        private final ActiveEvent event;
        private final Settings settings;
        private final Map<UUID, Double> damage = new HashMap<>();
        private LivingEntity entity;
        private BossBar bar;
        private int nextPhase;
        private boolean lost;

        Boss(ActiveEvent event, Settings settings) {
            this.event = event;
            this.settings = settings;
        }

        void spawn() {
            Location spot = event.location();
            entity = settings.mob().spawn(event, spot, plugin.text());
            event.claim(entity, this);
            plugin.playEffect(settings.spawnEffect(), spot);
            plugin.messages().broadcast("boss.spawned", event.placeholders(), name());
            bar = settings.bar().create(barTitle(), 1f);
            event.bars().show(SLOT, bar, settings.bar().radius());
            event.every(CHECK_TICKS, this::check);
        }

        @Override
        public void damaged(Player attacker, double amount) {
            damage.merge(attacker.getUniqueId(), amount, Double::sum);
        }

        private void check() {
            if (lost) {
                return;
            }
            // Removed without dying: peaceful difficulty, a cleanup plugin, a cancelled spawn.
            if (!entity.isValid() && !entity.isDead()) {
                lost = true;
                event.release(entity);
                event.bars().hide(SLOT, bar);
                event.signal(LOST_SIGNAL);
                return;
            }
            if (!entity.isValid()) {
                return;
            }
            // Players pull the boss away to fight it alone or trap it; it returns to its arena instead.
            if (settings.leash() > 0 && (!entity.getWorld().equals(event.location().getWorld())
                    || entity.getLocation().distanceSquared(event.location()) > settings.leash() * settings.leash())) {
                entity.teleport(event.location());
            }
            double share = entity.getHealth() / maxHealth();
            while (nextPhase < settings.phases().size() && share <= settings.phases().get(nextPhase).at()) {
                enter(settings.phases().get(nextPhase++));
            }
            bar.progress(Math.clamp((float) share, 0f, 1f));
        }

        private void enter(Phase phase) {
            setBase(Attribute.ATTACK_DAMAGE, phase.damage());
            setBase(Attribute.MOVEMENT_SPEED, phase.speed());
            plugin.playEffect(phase.effect(), entity.getLocation());
            plugin.messages().broadcast("boss.phase", event.placeholders(), name(),
                    Placeholder.unparsed("phase", Integer.toString(nextPhase + 1)));
            for (ScenarioAction action : phase.actions()) {
                try {
                    action.run(event);
                } catch (RuntimeException e) {
                    plugin.getSLF4JLogger().error("Boss phase action failed in {}", event.definition().id(), e);
                }
            }
            bar.name(barTitle());
        }

        @Override
        public void died(EntityDeathEvent death, Player killer) {
            lost = true;
            death.getDrops().clear();
            event.release(entity);
            event.bars().hide(SLOT, bar);
            plugin.playEffect(settings.deathEffect(), entity.getLocation());
            double total = damage.values().stream().mapToDouble(Double::doubleValue).sum();
            if (total > 0) {
                damage.forEach((id, dealt) -> {
                    Player player = Bukkit.getPlayer(id);
                    if (player != null) {
                        event.score().add(player, (int) Math.round(settings.damagePoints() * dealt / total));
                    }
                });
            }
            if (killer != null) {
                event.score().add(killer, settings.killPoints());
                EventStats.add(plugin, killer.getUniqueId(), EventStats.BOSSES, 1);
            }
            plugin.messages().broadcast("boss.killed", event.placeholders(), name(),
                    Placeholder.unparsed("player", killer == null ? "?" : killer.getName()));
            event.signal(SIGNAL);
        }

        private double maxHealth() {
            AttributeInstance max = entity.getAttribute(Attribute.MAX_HEALTH);
            return max == null ? entity.getHealth() : max.getValue();
        }

        private void setBase(Attribute attribute, double value) {
            AttributeInstance instance = entity.getAttribute(attribute);
            if (value > 0 && instance != null) {
                instance.setBaseValue(value);
            }
        }

        private TagResolver name() {
            Component shown = entity.customName();
            return Placeholder.component("boss", shown == null ? Component.translatable(entity.getType()) : shown);
        }

        private Component barTitle() {
            return plugin.messages().get(Bukkit.getConsoleSender(), "boss.bar",
                    TagResolver.resolver(event.placeholders(), name(),
                            Placeholder.unparsed("phase", Integer.toString(nextPhase + 1))));
        }
    }
}
