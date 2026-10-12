package com.mdevstudio.core.effect;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Firework;
import org.bukkit.persistence.PersistentDataType;

/** A firework burst at the origin plus {@code y}. It explodes at once and never hurts anyone. */
final class FireworkElement implements EffectElement {

    static final String HARMLESS_TAG = "harmless_firework";

    private final Timing timing;
    private final FireworkEffect effect;
    private final double offsetY;

    FireworkElement(ConfigurationSection section) {
        this.timing = Timing.parse(section, 1);
        this.offsetY = section.getDouble("y", 2);
        String shape = section.getString("shape", "ball").toUpperCase(Locale.ROOT).replace('-', '_');
        FireworkEffect.Type type;
        try {
            type = FireworkEffect.Type.valueOf(shape);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("shape: '" + shape.toLowerCase(Locale.ROOT)
                    + "' is not one of ball, ball_large, star, burst, creeper");
        }
        this.effect = FireworkEffect.builder()
                .with(type)
                .withColor(colors(section.getStringList("colors"), "#FFFFFF"))
                .withFade(colors(section.getStringList("fade"), null))
                .flicker(section.getBoolean("flicker", false))
                .trail(section.getBoolean("trail", false))
                .build();
    }

    @Override
    public Timing timing() {
        return timing;
    }

    @Override
    public void draw(EffectCanvas canvas, int tick) {
        NamespacedKey tag = new NamespacedKey(canvas.plugin(), HARMLESS_TAG);
        canvas.world().spawn(canvas.origin().clone().add(0, offsetY, 0), Firework.class, firework -> {
            var meta = firework.getFireworkMeta();
            meta.addEffect(effect);
            meta.setPower(0);
            firework.setFireworkMeta(meta);
            firework.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
            firework.setPersistent(false);
        }).detonate();
    }

    private static List<Color> colors(List<String> hexes, String fallback) {
        List<Color> colors = new ArrayList<>();
        for (String hex : hexes) {
            colors.add(ParticleSpec.color(hex, "colors"));
        }
        if (colors.isEmpty() && fallback != null) {
            colors.add(ParticleSpec.color(fallback, "colors"));
        }
        return colors;
    }
}
