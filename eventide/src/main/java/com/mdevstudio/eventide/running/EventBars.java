package com.mdevstudio.eventide.running;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Boss bars of one event, one per slot ("countdown", "airdrop", "boss"...). A slot shows its bar to
 * everyone or only to players within a radius of the event; a new bar in a slot replaces the old one.
 */
public final class EventBars implements EventPart {

    private record Slot(BossBar bar, double radius, Set<UUID> viewers) {
    }

    private final Location center;
    private final Map<String, Slot> slots = new HashMap<>();

    EventBars(Location center) {
        this.center = center;
    }

    /** {@code radius} 0 shows the bar to the whole server. */
    public void show(String slot, BossBar bar, double radius) {
        hide(slot);
        Slot shown = new Slot(bar, radius, new HashSet<>());
        slots.put(slot, shown);
        Bukkit.getOnlinePlayers().forEach(player -> update(shown, player));
    }

    public void hide(String slot) {
        Slot removed = slots.remove(slot);
        if (removed != null) {
            hideAll(removed);
        }
    }

    // An older countdown ending must not hide the newer one that replaced it.
    public void hide(String slot, BossBar bar) {
        Slot current = slots.get(slot);
        if (current != null && current.bar() == bar) {
            hide(slot);
        }
    }

    public boolean shows(String slot, BossBar bar) {
        Slot current = slots.get(slot);
        return current != null && current.bar() == bar;
    }

    /** Re-checks who is close enough to see each bar. The event calls it every second. */
    void refresh() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            slots.values().forEach(slot -> update(slot, player));
        }
    }

    // The client forgets every bar on disconnect, so a returning player is shown them again.
    void joined(Player player) {
        slots.values().forEach(slot -> {
            slot.viewers().remove(player.getUniqueId());
            update(slot, player);
        });
    }

    @Override
    public void stop() {
        slots.values().forEach(this::hideAll);
        slots.clear();
    }

    private void update(Slot slot, Player player) {
        boolean sees = slot.radius() <= 0 || (player.getWorld().equals(center.getWorld())
                && player.getLocation().distanceSquared(center) <= slot.radius() * slot.radius());
        if (sees && slot.viewers().add(player.getUniqueId())) {
            player.showBossBar(slot.bar());
        } else if (!sees && slot.viewers().remove(player.getUniqueId())) {
            player.hideBossBar(slot.bar());
        }
    }

    private void hideAll(Slot slot) {
        slot.viewers().forEach(id -> {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.hideBossBar(slot.bar());
            }
        });
        slot.viewers().clear();
    }
}
