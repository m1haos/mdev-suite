package com.mdevstudio.core.effect;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Firework;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

// Vanilla fireworks with effects hurt everyone nearby; decorative ones must not.
public final class HarmlessFireworks implements Listener {

    private final NamespacedKey tag;

    public HarmlessFireworks(Plugin plugin) {
        this.tag = new NamespacedKey(plugin, FireworkElement.HARMLESS_TAG);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Firework firework
                && firework.getPersistentDataContainer().has(tag, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }
}
