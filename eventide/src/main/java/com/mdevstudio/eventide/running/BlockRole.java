package com.mdevstudio.eventide.running;

import org.bukkit.entity.Player;

/** What a block placed by an event does when used. Claimed blocks cannot be broken, blown up or pushed. */
@FunctionalInterface
public interface BlockRole {

    /** A right click with the main hand. The vanilla interaction is already cancelled. */
    void use(Player player);

    /** Whether a punch uses the block too, for things players expect to grab rather than open. */
    default boolean usedByPunch() {
        return false;
    }
}
