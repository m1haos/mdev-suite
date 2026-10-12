package com.mdevstudio.eventide.loot;

import java.util.Optional;

import com.mdevstudio.core.text.Text;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

// Keys are ordinary items tagged with their rarity, so they survive restarts, trades and chests.
// Changing the key item in rarities.yml does not break keys already handed out: only the tag counts.
public final class KeyItems {

    private final NamespacedKey rarityKey;
    private final Text text;

    public KeyItems(Plugin plugin, Text text) {
        // Stays "key_tier" so keys given out before rarities existed keep working.
        this.rarityKey = new NamespacedKey(plugin, "key_tier");
        this.text = text;
    }

    /** Null-safe only for locked rarities; check {@link Rarity#locked()} first. */
    public ItemStack create(Rarity rarity, int amount) {
        TagResolver name = TagResolver.resolver(
                Placeholder.parsed("rarity", rarity.displayName()),
                Placeholder.parsed("tier", rarity.displayName()));
        ItemStack key = rarity.key().item().create(text, amount, name);
        key.editPersistentDataContainer(data -> data.set(rarityKey, PersistentDataType.STRING, rarity.id()));
        return key;
    }

    public Optional<String> rarityOf(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(item.getPersistentDataContainer().get(rarityKey, PersistentDataType.STRING));
    }
}
