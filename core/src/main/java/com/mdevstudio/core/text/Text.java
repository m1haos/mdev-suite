package com.mdevstudio.core.text;

import java.util.Map;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.ConfigurationSection;

/**
 * MiniMessage parsing with the studio palette available as tags:
 * {@code <accent>}, {@code <success>}, {@code <warning>}, {@code <error>}.
 */
public final class Text {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Map<String, String> DEFAULT_PALETTE = Map.of(
            "accent", "#7FB3FF",
            "success", "#8BD17C",
            "warning", "#F2C14E",
            "error", "#FF6B6B");

    private final TagResolver palette;

    private Text(TagResolver palette) {
        this.palette = palette;
    }

    public static Text fromConfig(ConfigurationSection section) {
        var builder = TagResolver.builder();
        DEFAULT_PALETTE.forEach((name, fallback) -> {
            String hex = section == null ? fallback : section.getString(name, fallback);
            TextColor color = TextColor.fromHexString(hex);
            builder.resolver(Placeholder.styling(name, color == null ? TextColor.fromHexString(fallback) : color));
        });
        return new Text(builder.build());
    }

    public Component parse(String input, TagResolver... placeholders) {
        return MINI_MESSAGE.deserialize(input, TagResolver.builder().resolver(palette).resolvers(placeholders).build());
    }

    /** Item names and lore are italic by default in vanilla; studio items never are. */
    public Component parseItemLine(String input, TagResolver... placeholders) {
        return parse(input, placeholders).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
