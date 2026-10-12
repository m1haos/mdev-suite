package com.mdevstudio.eventide.loot;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.mdevstudio.core.text.Text;
import com.mdevstudio.core.util.WeightedPool;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * What drops for one rarity in one loot table. Name, block, points and effects come from the
 * rarity unless the table overrides them.
 */
public record LootTier(
        String id,
        Rarity rarity,
        String displayName,
        Material block,
        int minRolls,
        int maxRolls,
        int points,
        WeightedPool<LootEntry> pool,
        List<LootEntry> entries,
        String idleEffect,
        String openEffect) {

    public int rollCount() {
        return minRolls >= maxRolls ? minRolls : ThreadLocalRandom.current().nextInt(minRolls, maxRolls + 1);
    }

    /** Rolls the loot: items fall out at {@code center}, rewards go to the player who opened it. */
    public void dropLoot(Location center, Player opener, Text text) {
        for (int i = rollCount(); i > 0; i--) {
            LootEntry entry = pool.pick();
            if (entry.hasItem()) {
                ItemStack loot = entry.createItem(text, entry.rollAmount());
                // Three swords are three items on the ground, not one stack of three.
                int max = loot.getMaxStackSize();
                while (loot.getAmount() > max) {
                    center.getWorld().dropItemNaturally(center, loot.asQuantity(max));
                    loot.setAmount(loot.getAmount() - max);
                }
                center.getWorld().dropItemNaturally(center, loot);
            }
            entry.rewards().forEach(reward -> reward.grant(opener));
        }
    }

    /** Sum of positive weights, for showing chances. */
    public double totalWeight() {
        return entries.stream().mapToDouble(LootEntry::weight).filter(weight -> weight > 0).sum();
    }
}
