package com.mdevstudio.eventide.running;

import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;

/** What a mob spawned by an event does when hit or killed. */
public interface EntityRole {

    /** Damage dealt by a player, directly or with a projectile, after armor. */
    default void damaged(Player attacker, double damage) {
    }

    /** Whether a right click on the mob goes to {@link #interacted} instead of the vanilla screen. */
    default boolean interactive() {
        return false;
    }

    default void interacted(Player player) {
    }

    /** The role decides what drops: {@code event.getDrops()} still holds the vanilla loot. */
    void died(EntityDeathEvent event, Player killer);
}
