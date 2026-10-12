package com.mdevstudio.core.effect;

import java.util.Locale;

import org.bukkit.configuration.ConfigurationSection;

/** One layer of an effect: a shape of particles, a sound, a firework or a lightning flash. */
public interface EffectElement {

    Timing timing();

    /** Draws the element for its own {@code tick}, counted from 0 within its duration. */
    void draw(EffectCanvas canvas, int tick);

    static EffectElement parse(ConfigurationSection section) {
        String type = section.getString("type", "").toLowerCase(Locale.ROOT);
        return switch (type) {
            case "burst" -> new BurstElement(section);
            case "ring" -> new RingElement(section);
            case "sphere" -> new SphereElement(section);
            case "helix" -> new HelixElement(section);
            case "shockwave" -> new ShockwaveElement(section);
            case "column" -> new ColumnElement(section);
            case "fall" -> new FallElement(section);
            case "sound" -> new SoundElement(section);
            case "firework" -> new FireworkElement(section);
            case "lightning" -> new LightningElement(section);
            default -> throw new IllegalArgumentException("unknown element type '" + type
                    + "'. Known: burst, ring, sphere, helix, shockwave, column, fall, sound, firework, lightning");
        };
    }
}
