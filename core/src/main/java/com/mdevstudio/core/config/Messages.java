package com.mdevstudio.core.config;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.mdevstudio.core.text.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Player-facing strings from {@code lang/<language>.yml}. Each player gets the file matching
 * their client language when one exists.
 */
public final class Messages {

    private static final List<String> BUNDLED = List.of("ru", "en");

    private final Text text;
    private final Map<String, YamlConfiguration> languages;
    private final String fallback;
    private final boolean useClientLanguage;

    private Messages(Text text, Map<String, YamlConfiguration> languages, String fallback, boolean useClientLanguage) {
        this.text = text;
        this.languages = languages;
        this.fallback = fallback;
        this.useClientLanguage = useClientLanguage;
    }

    public static Messages load(JavaPlugin plugin, Text text, String language, boolean useClientLanguage) {
        Map<String, YamlConfiguration> languages = new HashMap<>();
        for (String bundled : BUNDLED) {
            languages.put(bundled, YamlFile.load(plugin, "lang/" + bundled + ".yml"));
        }

        File[] custom = new File(plugin.getDataFolder(), "lang").listFiles((dir, name) -> name.endsWith(".yml"));
        if (custom != null) {
            for (File file : custom) {
                String name = file.getName().substring(0, file.getName().length() - 4).toLowerCase(Locale.ROOT);
                languages.computeIfAbsent(name, key -> YamlConfiguration.loadConfiguration(file));
            }
        }

        String fallback = language.toLowerCase(Locale.ROOT);
        if (!languages.containsKey(fallback)) {
            plugin.getSLF4JLogger().warn("lang/{}.yml not found, falling back to ru", fallback);
            fallback = "ru";
        }
        return new Messages(text, languages, fallback, useClientLanguage);
    }

    public Component get(CommandSender recipient, String key, TagResolver... placeholders) {
        YamlConfiguration yaml = languages.get(languageOf(recipient));
        // A missing key is shown as-is so it stands out in game instead of failing silently.
        String template = Objects.requireNonNullElse(yaml.getString(key), key);
        String prefix = Objects.requireNonNullElse(yaml.getString("prefix"), "");
        return text.parse(template, TagResolver.resolver(Placeholder.parsed("prefix", prefix),
                TagResolver.resolver(placeholders)));
    }

    public void send(CommandSender recipient, String key, TagResolver... placeholders) {
        recipient.sendMessage(get(recipient, key, placeholders));
    }

    public void broadcast(String key, TagResolver... placeholders) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            send(player, key, placeholders);
        }
        send(Bukkit.getConsoleSender(), key, placeholders);
    }

    private String languageOf(CommandSender recipient) {
        if (useClientLanguage && recipient instanceof Player player) {
            String language = player.locale().getLanguage();
            if (languages.containsKey(language)) {
                return language;
            }
        }
        return fallback;
    }
}
