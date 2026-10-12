package com.mdevstudio.core;

import java.util.Optional;

import com.mdevstudio.core.economy.Money;
import com.mdevstudio.core.effect.EffectLibrary;
import com.mdevstudio.core.reward.RewardRegistry;
import com.mdevstudio.core.scenario.ActionRegistry;
import com.mdevstudio.core.stats.PlayerStats;
import com.mdevstudio.core.text.Text;

/**
 * Entry point for plugins built on the core. Available from {@code onEnable} of any plugin
 * that declares {@code depend: [MDevCore]}.
 */
public final class MDevCore {

    private static MDevCorePlugin plugin;

    private MDevCore() {
    }

    static void attach(MDevCorePlugin instance) {
        plugin = instance;
    }

    public static Text text() {
        return instance().text();
    }

    public static RewardRegistry rewards() {
        return instance().rewards();
    }

    public static ActionRegistry actions() {
        return instance().actions();
    }

    /** Shared animations and particles. Replaced on {@code /mdevcore reload}, so do not keep a reference. */
    public static EffectLibrary effects() {
        return instance().effects();
    }

    /** Player counters in the core database. Empty when the database could not be opened at startup. */
    public static Optional<PlayerStats> stats() {
        return Optional.ofNullable(instance().stats());
    }

    /**
     * The server currency through Vault. Empty without Vault or an economy plugin. Economy plugins
     * may register after the core, so ask each time instead of keeping the result.
     */
    public static Optional<Money> money() {
        return instance().money();
    }

    private static MDevCorePlugin instance() {
        if (plugin == null) {
            throw new IllegalStateException("MDevCore is not enabled. Add 'depend: [MDevCore]' to plugin.yml");
        }
        return plugin;
    }
}
