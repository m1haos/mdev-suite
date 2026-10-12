package com.mdevstudio.core.hook;

import java.util.Optional;

import com.mdevstudio.core.economy.Money;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.plugin.RegisteredServiceProvider;

// Loaded only when Vault is installed; nothing else in the core touches Vault classes.
public final class VaultMoney implements Money {

    private final Economy economy;

    private VaultMoney(Economy economy) {
        this.economy = economy;
    }

    public static Optional<Money> find(Server server) {
        RegisteredServiceProvider<Economy> provider = server.getServicesManager().getRegistration(Economy.class);
        return provider == null ? Optional.empty() : Optional.of(new VaultMoney(provider.getProvider()));
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return economy.has(player, amount);
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        return economy.has(player, amount) && economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    @Override
    public void deposit(OfflinePlayer player, double amount) {
        economy.depositPlayer(player, amount);
    }

    @Override
    public String format(double amount) {
        return economy.format(amount);
    }
}
