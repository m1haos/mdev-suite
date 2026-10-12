package com.mdevstudio.eventide.definition;

import java.io.File;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.mdevstudio.core.MDevCore;
import com.mdevstudio.core.reward.Reward;
import com.mdevstudio.core.scenario.Scenario;
import com.mdevstudio.eventide.EventideSettings;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class EventLoader {

    private static final List<String> EXAMPLES = List.of(
            "events/meteor.yml", "events/airdrop.yml", "events/koth.yml", "events/siege.yml");
    private static final List<String> STRUCTURE_EXAMPLES = List.of("structures/siege-fortress.nbt");

    private EventLoader() {
    }

    // A broken file is reported and skipped; the other events still load.
    public static Map<String, EventDefinition> loadAll(JavaPlugin plugin, EventideSettings settings) {
        File folder = new File(plugin.getDataFolder(), "events");
        if (!folder.exists()) {
            EXAMPLES.forEach(path -> plugin.saveResource(path, false));
        }
        File structureFolder = new File(plugin.getDataFolder(), "structures");
        if (!structureFolder.exists()) {
            STRUCTURE_EXAMPLES.forEach(path -> plugin.saveResource(path, false));
        }

        Map<String, EventDefinition> definitions = new LinkedHashMap<>();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return definitions;
        }
        for (File file : files) {
            String id = file.getName().substring(0, file.getName().length() - 4).toLowerCase(Locale.ROOT);
            try {
                definitions.put(id, parse(id, YamlConfiguration.loadConfiguration(file), settings,
                        settings.modules().structures() ? structureFolder : null));
            } catch (IllegalArgumentException e) {
                plugin.getSLF4JLogger().error("events/{}: {}. The event is disabled until fixed.",
                        file.getName(), e.getMessage());
            }
        }
        return definitions;
    }

    // structures is null when the module is off: builds in event files are then ignored.
    private static EventDefinition parse(String id, YamlConfiguration yaml, EventideSettings settings,
            File structures) {
        List<LocalTime> schedule = new ArrayList<>();
        for (String time : yaml.getStringList("schedule")) {
            try {
                schedule.add(LocalTime.parse(time.length() == 4 ? "0" + time : time));
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("schedule: '" + time + "' is not a time like 18:30");
            }
        }

        var rewards = MDevCore.rewards();
        Map<Integer, List<Reward>> places = new TreeMap<>();
        ConfigurationSection placeSection = yaml.getConfigurationSection("rewards.places");
        if (placeSection != null) {
            for (String place : placeSection.getKeys(false)) {
                try {
                    places.put(Integer.parseInt(place), rewards.parseAll(placeSection.getStringList(place)));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("rewards.places: '" + place + "' is not a place number");
                }
            }
        }

        ConfigurationSection buildSection = yaml.getConfigurationSection("structure");
        Optional<StructureSpec> build = structures == null || buildSection == null
                ? Optional.empty()
                : Optional.of(StructureSpec.parse(buildSection, structures));

        LocationRule location = LocationRule.parse(yaml.getConfigurationSection("location"), settings.spotCheck(),
                build);
        RegionRule region;
        try {
            region = RegionRule.parse(yaml.getConfigurationSection("region"), settings.eventRegion())
                    .resolve(location.surface().radius());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("region." + e.getMessage());
        }

        return new EventDefinition(
                id,
                yaml.getString("name", id),
                List.copyOf(schedule),
                Math.max(0, yaml.getInt("min-online", 1)),
                location,
                settings.modules().eventRegion() ? region : region.permissive(),
                build,
                yaml.getString("loot-table", "default"),
                places,
                rewards.parseAll(yaml.getStringList("rewards.participation")),
                yaml.getString("effects.finish"),
                yaml.getStringList("finish-on").stream()
                        .map(signal -> signal.trim().toLowerCase(Locale.ROOT))
                        .collect(Collectors.toUnmodifiableSet()),
                Scenario.parse(yaml.getMapList("stages"), MDevCore.actions()));
    }
}
