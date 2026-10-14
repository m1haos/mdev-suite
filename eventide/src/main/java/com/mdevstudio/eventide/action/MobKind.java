package com.mdevstudio.eventide.action;

import java.util.Locale;

import com.mdevstudio.eventide.EventidePlugin;
import com.mdevstudio.eventide.hook.MythicMobs;
import org.bukkit.entity.EntityType;

/** What an event file names as a mob: a vanilla type ({@code zombie}) or a MythicMobs id ({@code mythic:Id}). */
record MobKind(EntityType type, String mythic) {

    private static final String PREFIX = "mythic:";

    static MobKind vanilla(EntityType type) {
        return new MobKind(type, null);
    }

    static MobKind parse(EventidePlugin plugin, String name) {
        String trimmed = name.trim();
        if (!trimmed.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            return vanilla(MobSpec.entityType(trimmed));
        }
        String id = trimmed.substring(PREFIX.length()).trim();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("'" + name + "' has no MythicMobs id after 'mythic:'");
        }
        if (!plugin.settings().modules().mythicMobs()) {
            throw new IllegalArgumentException("'" + name + "' needs modules.mythic-mobs turned on in config.yml");
        }
        if (!plugin.getServer().getPluginManager().isPluginEnabled(MythicMobs.PLUGIN)) {
            throw new IllegalArgumentException("'" + name + "' needs the MythicMobs plugin");
        }
        if (!MythicMobs.exists(id)) {
            throw new IllegalArgumentException("MythicMobs has no mob '" + id + "'");
        }
        return new MobKind(null, id);
    }

    boolean isMythic() {
        return mythic != null;
    }

    @Override
    public String toString() {
        return isMythic() ? PREFIX + mythic : type.getKey().getKey().toLowerCase(Locale.ROOT);
    }
}
