package com.mdevstudio.eventide.running;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.entity.Player;

/** Points of everyone who took part in one event. */
public final class EventScore {

    // Insertion order breaks ties: whoever scored first ranks higher.
    private final Map<UUID, Integer> points = new LinkedHashMap<>();

    public void add(Player player, int amount) {
        if (amount != 0) {
            points.merge(player.getUniqueId(), amount, Integer::sum);
        }
    }

    public int of(UUID player) {
        return points.getOrDefault(player, 0);
    }

    public boolean isEmpty() {
        return points.isEmpty();
    }

    /** Best first. */
    public List<Map.Entry<UUID, Integer>> ranking() {
        return points.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue(Comparator.reverseOrder()))
                .map(entry -> Map.entry(entry.getKey(), entry.getValue()))
                .toList();
    }

    public Optional<Map.Entry<UUID, Integer>> leader() {
        return ranking().stream().findFirst();
    }
}
