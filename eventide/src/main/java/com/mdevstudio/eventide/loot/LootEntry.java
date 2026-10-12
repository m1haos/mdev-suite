package com.mdevstudio.eventide.loot;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.mdevstudio.core.item.ItemSpec;
import com.mdevstudio.core.reward.Reward;
import com.mdevstudio.core.text.Text;
import org.bukkit.inventory.ItemStack;

/**
 * One line of a loot tier. The item is either written by hand ({@code item}) or saved whole from
 * the in-game editor ({@code item-data}), with enchantments, model and other plugins' data intact.
 */
public record LootEntry(ItemSpec item, ItemStack stack, int minAmount, int maxAmount, double weight,
        List<Reward> rewards) {

    public boolean hasItem() {
        return item != null || stack != null;
    }

    public int rollAmount() {
        return minAmount >= maxAmount ? minAmount : ThreadLocalRandom.current().nextInt(minAmount, maxAmount + 1);
    }

    /** May exceed the item's stack size; {@link LootTier#dropLoot} splits it. */
    public ItemStack createItem(Text text, int amount) {
        if (stack != null) {
            ItemStack copy = stack.clone();
            copy.setAmount(amount);
            return copy;
        }
        return item.create(text, amount);
    }
}
