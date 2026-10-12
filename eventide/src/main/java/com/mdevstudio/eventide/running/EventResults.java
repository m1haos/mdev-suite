package com.mdevstudio.eventide.running;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mdevstudio.core.MDevCore;
import com.mdevstudio.core.reward.Reward;
import com.mdevstudio.eventide.EventidePlugin;
import com.mdevstudio.eventide.EventideSettings;
import com.mdevstudio.eventide.api.EventideFinishEvent;
import com.mdevstudio.eventide.definition.EventDefinition;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Places, rewards and statistics at the end of an event. */
final class EventResults {

    private static final int TOP_PLACES_SHOWN = 3;

    private final EventidePlugin plugin;
    private final ActiveEvent event;
    private final List<Map.Entry<UUID, Integer>> ranking;

    EventResults(EventidePlugin plugin, ActiveEvent event) {
        this.plugin = plugin;
        this.event = event;
        this.ranking = event.score().ranking();
    }

    void announce(boolean completed) {
        var messages = plugin.messages();
        EventideSettings.Modules modules = plugin.settings().modules();
        if (!completed) {
            messages.broadcast("event.stopped", event.placeholders());
            Bukkit.getPluginManager().callEvent(
                    new EventideFinishEvent(event.definition().id(), event.location(), ranking, false));
            return;
        }
        if (ranking.isEmpty()) {
            messages.broadcast("event.finished-empty", event.placeholders());
        } else if (modules.leaderboard()) {
            messages.broadcast("event.finished", event.placeholders());
            showTop();
        } else {
            messages.broadcast("event.finished-plain", event.placeholders());
        }
        if (modules.rewards()) {
            grantRewards(modules.leaderboard());
        }
        recordStats();
        Bukkit.getPluginManager().callEvent(
                new EventideFinishEvent(event.definition().id(), event.location(), ranking, true));
    }

    private void showTop() {
        for (int i = 0; i < Math.min(TOP_PLACES_SHOWN, ranking.size()); i++) {
            var entry = ranking.get(i);
            plugin.messages().broadcast("event.top-line",
                    Placeholder.unparsed("place", Integer.toString(i + 1)),
                    Placeholder.unparsed("player", nameOf(entry.getKey())),
                    Placeholder.unparsed("points", Integer.toString(entry.getValue())));
        }
    }

    private void grantRewards(boolean byPlace) {
        EventDefinition definition = event.definition();
        for (int i = 0; i < ranking.size(); i++) {
            Player player = Bukkit.getPlayer(ranking.get(i).getKey());
            if (player == null) {
                continue;
            }
            grant(player, definition.participationRewards());
            if (byPlace) {
                grant(player, definition.placeRewards().getOrDefault(i + 1, List.of()));
            }
        }
    }

    private void recordStats() {
        for (int i = 0; i < ranking.size(); i++) {
            UUID id = ranking.get(i).getKey();
            EventStats.add(plugin, id, EventStats.PLAYED, 1);
            EventStats.add(plugin, id, EventStats.POINTS, ranking.get(i).getValue());
            if (i == 0) {
                EventStats.add(plugin, id, EventStats.WINS, 1);
            }
        }
        // Queued after the writes above on the same database thread, so the new numbers are included.
        MDevCore.stats().ifPresent(stats -> stats.refreshTops());
    }

    private static void grant(Player player, List<Reward> rewards) {
        rewards.forEach(reward -> reward.grant(player));
    }

    private static String nameOf(UUID id) {
        return Optional.ofNullable(Bukkit.getOfflinePlayer(id).getName()).orElse("?");
    }
}
