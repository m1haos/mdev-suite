package com.mdevstudio.eventide.hook;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

// Loaded only when MythicMobs is installed; MobKind checks the plugin before touching this class.
public final class MythicMobs {

    public static final String PLUGIN = "MythicMobs";

    private MythicMobs() {
    }

    public static boolean exists(String id) {
        return MythicBukkit.inst().getMobManager().getMythicMob(id).isPresent();
    }

    /** The spawned entity, or null if MythicMobs refused (unknown id, cancelled spawn). */
    public static Entity spawn(String id, Location at) {
        ActiveMob mob = MythicBukkit.inst().getMobManager().spawnMob(id, at);
        return mob == null ? null : mob.getEntity().getBukkitEntity();
    }
}
