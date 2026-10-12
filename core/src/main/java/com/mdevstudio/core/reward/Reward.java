package com.mdevstudio.core.reward;

import org.bukkit.entity.Player;

@FunctionalInterface
public interface Reward {

    void grant(Player player);
}
