package com.mdevstudio.core.effect;

import org.bukkit.configuration.ConfigurationSection;

/** Particles at one point; use {@code count} and {@code spread} for a cloud. */
final class BurstElement extends ShapeElement {

    BurstElement(ConfigurationSection section) {
        super(section, 1);
    }

    @Override
    public void draw(EffectCanvas canvas, int tick) {
        canvas.particle(particle, 0, offsetY, 0);
    }
}
