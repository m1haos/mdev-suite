package com.mdevstudio.core.reward;

import java.util.Map;

import com.mdevstudio.core.item.ItemSpec;
import com.mdevstudio.core.text.Text;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class BuiltinRewards {

    private BuiltinRewards() {
    }

    public static void registerAll(RewardRegistry registry, Text text) {
        registry.register("command", command -> player ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), withPlayer(command, player)));

        registry.register("player-command", command -> player ->
                player.performCommand(withPlayer(command, player)));

        registry.register("message", message -> player ->
                player.sendMessage(text.parse(message, Placeholder.unparsed("player", player.getName()))));

        registry.register("broadcast", message -> player ->
                Bukkit.broadcast(text.parse(message, Placeholder.unparsed("player", player.getName()))));

        registry.register("xp", value -> {
            int amount = Integer.parseInt(value);
            return player -> player.giveExp(amount);
        });

        registry.register("item", value -> {
            ItemSpec spec = ItemSpec.parse(value);
            return player -> give(player, spec.create(text));
        });

        registry.register("sound", value -> {
            String[] parts = value.split("\\s+");
            Key key = Key.key(parts[0]);
            float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1f;
            float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1f;
            Sound sound = Sound.sound(key, Sound.Source.MASTER, volume, pitch);
            return player -> player.playSound(sound);
        });
    }

    /** Puts the item in the inventory and drops whatever does not fit at the player's feet. */
    public static void give(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        leftover.values().forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
    }

    private static String withPlayer(String command, Player player) {
        return command.replace("<player>", player.getName());
    }
}
