package com.mdevstudio.eventide.loot;

import java.util.Locale;

import com.mdevstudio.core.reward.BuiltinRewards;
import com.mdevstudio.core.reward.Reward;
import com.mdevstudio.eventide.EventidePlugin;
import com.mdevstudio.eventide.EventideSettings;

/**
 * The {@code key: <rarity> [amount]} reward, available to every plugin that uses MDevCore rewards,
 * e.g. {@code "key: rare 2"} in a quest or a daily bonus.
 */
public final class KeyReward {

    private KeyReward() {
    }

    public static Reward parse(EventidePlugin plugin, String value) {
        String[] parts = value.trim().split("\\s+");
        String rarityId = parts[0].toLowerCase(Locale.ROOT);
        int amount;
        try {
            amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("key: '" + parts[1] + "' is not an amount");
        }
        if (rarityId.isEmpty() || amount < 1) {
            throw new IllegalArgumentException("key: write it as 'key: <rarity> [amount]', e.g. 'key: rare 2'");
        }
        // The rarity is looked up when the reward is given: rarities.yml can be reloaded in between.
        return player -> {
            EventideSettings.Keys keys = plugin.settings().modules().keys();
            if (!keys.enabled() || !keys.asReward()) {
                return;
            }
            plugin.rarity(rarityId).filter(Rarity::locked).ifPresentOrElse(
                    rarity -> BuiltinRewards.give(player, plugin.keys().create(rarity, amount)),
                    () -> plugin.getSLF4JLogger().warn(
                            "Reward 'key: {}' names a rarity that is missing from rarities.yml or has no key", rarityId));
        };
    }
}
