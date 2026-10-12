package com.mdevstudio.eventide.action;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mdevstudio.core.scenario.ActionFactory;
import com.mdevstudio.core.scenario.ScenarioAction;
import com.mdevstudio.core.util.Durations;
import com.mdevstudio.eventide.EventidePlugin;
import com.mdevstudio.eventide.running.ActiveEvent;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * King of the hill: every second in the zone gives points, the leader is shown in a boss bar.
 * Sends {@code zone-won} when someone reaches {@code win-score}.
 */
final class CaptureZoneAction implements ActionFactory {

    static final String SIGNAL = "zone-won";
    private static final String SLOT = "zone";
    // Refreshed every second, so the glow fades on its own a moment after the holder leaves.
    private static final PotionEffect HOLDER_GLOW =
            new PotionEffect(PotionEffectType.GLOWING, 30, 0, false, false, false);

    private final EventidePlugin plugin;

    private enum Contest { NOBODY, EVERYONE }

    private record Settings(
            double radius,
            double height,
            int pointsPerSecond,
            int winScore,
            Contest contest,
            boolean holderGlow,
            BarStyle bar,
            String borderEffect,
            long borderEveryTicks) {
    }

    CaptureZoneAction(EventidePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public ScenarioAction create(ConfigurationSection section) {
        Settings settings = new Settings(
                Math.max(1, section.getDouble("radius", 6)),
                Math.max(1, section.getDouble("height", 4)),
                Math.max(1, section.getInt("points-per-second", 1)),
                Math.max(0, section.getInt("win-score", 0)),
                EventActions.enumValue(Contest.class, section.getString("contested", "nobody")),
                section.getBoolean("holder-glow", true),
                BarStyle.parse(section, "purple", 96),
                section.getString("effects.border"),
                Math.max(10, Durations.toTicks(Durations.parse(section.getString("effects.border-every", "2s")))));

        return EventActions.forEvent(event -> new Zone(event, settings).start());
    }

    private final class Zone {

        private final ActiveEvent event;
        private final Settings settings;
        private final Location center;
        private BossBar bar;
        private boolean won;

        Zone(ActiveEvent event, Settings settings) {
            this.event = event;
            this.settings = settings;
            this.center = event.location();
        }

        void start() {
            bar = settings.bar().create(title("zone.bar-empty"), 0);
            event.bars().show(SLOT, bar, settings.bar().radius());
            event.every(20, this::tick);
            if (settings.borderEffect() != null) {
                event.every(settings.borderEveryTicks(), () ->
                        plugin.playEffect(settings.borderEffect(), center, settings.radius()));
            }
        }

        private void tick() {
            if (won) {
                return;
            }
            List<Player> inside = Spots.playersIn(center, settings.radius(), settings.height());
            boolean contested = inside.size() > 1;
            if (!contested || settings.contest() == Contest.EVERYONE) {
                inside.forEach(player -> event.score().add(player, settings.pointsPerSecond()));
            }
            if (settings.holderGlow() && inside.size() == 1) {
                inside.getFirst().addPotionEffect(HOLDER_GLOW);
            }
            updateBar(inside, contested);

            Optional<Map.Entry<UUID, Integer>> leader = event.score().leader();
            if (settings.winScore() > 0 && leader.isPresent() && leader.get().getValue() >= settings.winScore()) {
                won = true;
                plugin.messages().broadcast("zone.won", event.placeholders(),
                        Placeholder.unparsed("player", nameOf(leader.get().getKey())));
                event.signal(SIGNAL);
            }
        }

        private void updateBar(List<Player> inside, boolean contested) {
            Optional<Map.Entry<UUID, Integer>> leader = event.score().leader();
            String key = inside.isEmpty() ? "zone.bar-empty" : contested ? "zone.bar-contested" : "zone.bar-held";
            bar.name(title(key,
                    Placeholder.unparsed("holder", inside.size() == 1 ? inside.getFirst().getName() : ""),
                    Placeholder.unparsed("leader", leader.map(entry -> nameOf(entry.getKey())).orElse("—")),
                    Placeholder.unparsed("points", Integer.toString(leader.map(Map.Entry::getValue).orElse(0))),
                    Placeholder.unparsed("goal", settings.winScore() > 0 ? Integer.toString(settings.winScore()) : "∞")));
            if (settings.winScore() > 0) {
                bar.progress(Math.clamp(leader.map(Map.Entry::getValue).orElse(0) / (float) settings.winScore(), 0f, 1f));
            } else {
                bar.progress(1f);
            }
        }

        private Component title(String key, TagResolver... extra) {
            return plugin.messages().get(Bukkit.getConsoleSender(), key,
                    TagResolver.resolver(event.placeholders(), TagResolver.resolver(extra)));
        }

        private String nameOf(UUID id) {
            return Optional.ofNullable(Bukkit.getOfflinePlayer(id).getName()).orElse("?");
        }
    }
}
