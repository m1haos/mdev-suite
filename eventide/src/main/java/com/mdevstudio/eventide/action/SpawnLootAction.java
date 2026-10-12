package com.mdevstudio.eventide.action;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.mdevstudio.core.scenario.ActionFactory;
import com.mdevstudio.core.scenario.ScenarioAction;
import com.mdevstudio.eventide.EventidePlugin;
import com.mdevstudio.eventide.loot.LootTier;
import com.mdevstudio.eventide.loot.Rarity;
import com.mdevstudio.eventide.running.ActiveEvent;
import com.mdevstudio.eventide.running.EventStats;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Caches of the loot table's tiers around the spot. Sends {@code caches-opened} when the last one is opened. */
final class SpawnLootAction implements ActionFactory {

    static final String SIGNAL = "caches-opened";
    private static final Sound OPEN_SOUND = Sound.sound(Key.key("block.barrel.open"), Sound.Source.BLOCK, 1f, 0.8f);

    private final EventidePlugin plugin;

    SpawnLootAction(EventidePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public ScenarioAction create(ConfigurationSection settings) {
        ConfigurationSection section = settings.getConfigurationSection("containers");
        if (section == null) {
            throw new IllegalArgumentException("spawn-loot: 'containers' is required, e.g. {common: 5, rare: 2}");
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        section.getKeys(false).forEach(tier -> counts.put(tier, section.getInt(tier)));
        int spread = Math.max(2, settings.getInt("spread", 10));

        return EventActions.forEvent(event -> {
            Map<Block, LootTier> placed = new HashMap<>();
            counts.forEach((tierId, count) -> {
                LootTier tier = event.lootTable().tier(tierId).orElseThrow(() -> new IllegalStateException(
                        "Loot table '" + event.lootTable().id() + "' has no tier '" + tierId + "'"));
                for (int i = 0; i < count; i++) {
                    Block block = Spots.freeBlock(event.location(), spread, placed.keySet());
                    if (block != null) {
                        placed.put(block, tier);
                    }
                }
            });
            if (!placed.isEmpty()) {
                new CacheField(event, placed).place();
            }
        });
    }

    private final class CacheField {

        private final ActiveEvent event;
        private final Map<Block, LootTier> closed;

        CacheField(ActiveEvent event, Map<Block, LootTier> caches) {
            this.event = event;
            this.closed = caches;
        }

        void place() {
            closed.forEach((block, tier) -> {
                block.setType(tier.block());
                event.claim(block, player -> tryOpen(player, block));
            });
            if (plugin.settings().modules().cacheGlow()) {
                event.every(plugin.settings().glowIntervalTicks(), () -> closed.forEach((block, tier) ->
                        plugin.playEffect(tier.idleEffect(), block.getLocation().add(0.5, 0, 0.5))));
            }
        }

        private void tryOpen(Player player, Block block) {
            LootTier tier = closed.get(block);
            var tierName = Placeholder.parsed("tier", tier.displayName());
            boolean needsKey = plugin.settings().modules().keys().enabled() && tier.rarity().locked();
            if (needsKey && !takeKey(player, tier)) {
                return;
            }
            plugin.messages().send(player, "container.opened", tierName,
                    Placeholder.unparsed("points", Integer.toString(tier.points())));
            open(player, block, tier);
        }

        private boolean takeKey(Player player, LootTier tier) {
            var tierName = Placeholder.parsed("tier", tier.displayName());
            ItemStack hand = player.getInventory().getItemInMainHand();
            Optional<String> keyRarity = plugin.keys().rarityOf(hand);
            if (keyRarity.isEmpty()) {
                plugin.messages().send(player, "container.need-key", tierName);
                return false;
            }
            // A key whose rarity was removed from rarities.yml opens nothing, but it is not taken away either.
            Optional<Rarity> key = plugin.rarity(keyRarity.get()).filter(rarity -> rarity.keyOpens(tier.rarity()));
            if (key.isEmpty()) {
                plugin.messages().send(player, "container.wrong-key", tierName);
                return false;
            }
            if (key.get().key().consume()) {
                hand.subtract();
            }
            return true;
        }

        private void open(Player player, Block block, LootTier tier) {
            closed.remove(block);
            event.release(block);
            Location center = block.getLocation().add(0.5, 0.5, 0.5);
            block.setType(Material.AIR);
            if (tier.openEffect() == null) {
                block.getWorld().playSound(OPEN_SOUND, center.getX(), center.getY(), center.getZ());
            } else {
                plugin.playEffect(tier.openEffect(), center);
            }
            tier.dropLoot(center, player, plugin.text());
            event.score().add(player, tier.points());
            EventStats.add(plugin, player.getUniqueId(), EventStats.CACHES, 1);
            if (closed.isEmpty()) {
                event.signal(SIGNAL);
            }
        }
    }
}
