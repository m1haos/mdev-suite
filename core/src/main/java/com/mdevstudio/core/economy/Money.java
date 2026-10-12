package com.mdevstudio.core.economy;

import org.bukkit.OfflinePlayer;

/** The server currency. Available through {@code MDevCore.money()} when Vault and an economy plugin are installed. */
public interface Money {

    boolean has(OfflinePlayer player, double amount);

    /** Takes the amount if the player has it. Returns false and takes nothing otherwise. */
    boolean withdraw(OfflinePlayer player, double amount);

    void deposit(OfflinePlayer player, double amount);

    /** The amount as the economy plugin writes it, e.g. "1,500 coins". */
    String format(double amount);
}
