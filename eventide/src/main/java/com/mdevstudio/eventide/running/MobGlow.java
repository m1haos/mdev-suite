package com.mdevstudio.eventide.running;

import java.util.Locale;
import java.util.UUID;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * Coloured glow for event mobs. The outline colour comes from a team on the main scoreboard, so
 * players whose scoreboard is replaced by another plugin (TAB and the like) see a white outline.
 */
public final class MobGlow {

    // The main scoreboard is saved with the world, so teams left by a crash are cleared on startup.
    private static final String TEAM_PREFIX = "eventide_glow_";

    private MobGlow() {
    }

    public static NamedTextColor parseColor(String name) {
        NamedTextColor color = NamedTextColor.NAMES.value(name.trim().toLowerCase(Locale.ROOT));
        if (color == null) {
            throw new IllegalArgumentException("'" + name + "' is not a colour; use one of "
                    + NamedTextColor.NAMES.keys() + ", true or false");
        }
        return color;
    }

    public static void apply(Entity entity, NamedTextColor color) {
        entity.setGlowing(true);
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        String name = TEAM_PREFIX + NamedTextColor.NAMES.key(color);
        Team team = board.getTeam(name);
        if (team == null) {
            team = board.registerNewTeam(name);
            team.color(color);
        }
        team.addEntry(entity.getUniqueId().toString());
    }

    static void clear(UUID entity) {
        String entry = entity.toString();
        Team team = Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(entry);
        if (team != null && team.getName().startsWith(TEAM_PREFIX)) {
            team.removeEntry(entry);
        }
    }

    public static void clearAll() {
        Bukkit.getScoreboardManager().getMainScoreboard().getTeams().stream()
                .filter(team -> team.getName().startsWith(TEAM_PREFIX))
                .toList()
                .forEach(Team::unregister);
    }
}
