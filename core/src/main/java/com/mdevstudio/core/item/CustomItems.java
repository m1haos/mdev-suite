package com.mdevstudio.core.item;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.mdevstudio.core.hook.CraftEngineHook;
import com.mdevstudio.core.hook.ItemsAdderHook;
import com.mdevstudio.core.hook.NexoHook;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Items of other plugins, named in config as {@code itemsadder:namespace:id}, {@code nexo:id} or
 * {@code craftengine:namespace:id}. Each source is used only when its plugin is installed and
 * its switch in {@code custom-items} of the core config is on.
 */
public final class CustomItems {

    private enum Source {
        ITEMSADDER("ItemsAdder"), NEXO("Nexo"), CRAFTENGINE("CraftEngine");

        final String plugin;

        Source(String plugin) {
            this.plugin = plugin;
        }

        String prefix() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private static final Set<Source> ENABLED = new HashSet<>();
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private CustomItems() {
    }

    /** Reads the {@code custom-items} section of the core config. Sources are on unless the section says no. */
    public static void configure(ConfigurationSection section) {
        ENABLED.clear();
        for (Source source : Source.values()) {
            if (section == null || section.getBoolean(source.prefix(), true)) {
                ENABLED.add(source);
            }
        }
        REPORTED.clear();
    }

    /** Whether the text names an item of another plugin, by its prefix. */
    public static boolean isCustom(String reference) {
        return source(reference) != null;
    }

    /** Fails with a readable message when the reference cannot work on this server. */
    public static void validate(String reference) {
        Source source = source(reference);
        if (source == null) {
            throw new IllegalArgumentException("'" + reference + "' is not an item of another plugin");
        }
        if (reference.length() == source.prefix().length() + 1) {
            throw new IllegalArgumentException("'" + reference + "' has no item id after '" + source.prefix() + ":'");
        }
        if (!ENABLED.contains(source)) {
            throw new IllegalArgumentException("'" + reference + "' needs custom-items." + source.prefix()
                    + " turned on in the MDevCore config.yml");
        }
        if (!Bukkit.getPluginManager().isPluginEnabled(source.plugin)) {
            throw new IllegalArgumentException("'" + reference + "' needs the " + source.plugin + " plugin");
        }
    }

    /**
     * The item, or a barrier named after the missing id: plugins like ItemsAdder fill their registry
     * after the server has started, so a wrong id only shows up here and must not break a whole event.
     */
    public static ItemStack create(String reference, int amount) {
        Source source = source(reference);
        String id = reference.substring(source.prefix().length() + 1);
        ItemStack item = switch (source) {
            case ITEMSADDER -> ItemsAdderHook.create(id);
            case NEXO -> NexoHook.create(id);
            case CRAFTENGINE -> CraftEngineHook.create(id);
        };
        if (item == null) {
            if (REPORTED.add(reference)) {
                JavaPlugin.getProvidingPlugin(CustomItems.class).getSLF4JLogger()
                        .warn("{} has no item '{}'; a barrier is given instead", source.plugin, id);
            }
            item = ItemStack.of(Material.BARRIER);
            item.editMeta(meta -> meta.displayName(Component.text("Missing: " + reference)));
        }
        item.setAmount(Math.max(1, amount));
        return item;
    }

    private static Source source(String reference) {
        int colon = reference.indexOf(':');
        if (colon < 0) {
            return null;
        }
        String prefix = reference.substring(0, colon).toLowerCase(Locale.ROOT);
        for (Source source : Source.values()) {
            if (source.prefix().equals(prefix)) {
                return source;
            }
        }
        return null;
    }
}
