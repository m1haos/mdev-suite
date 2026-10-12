package com.mdevstudio.core.effect;

import org.bukkit.configuration.ConfigurationSection;

/** A ring that expands from {@code start-radius} to {@code radius} over the duration. */
final class ShockwaveElement extends ShapeElement {

    private final double startRadius;
    private final double radius;
    private final int points;

    ShockwaveElement(ConfigurationSection section) {
        super(section, 15);
        this.startRadius = section.getDouble("start-radius", 0.5);
        this.radius = section.getDouble("radius", 6);
        this.points = section.getInt("points", 48);
    }

    @Override
    public void draw(EffectCanvas canvas, int tick) {
        double current = (startRadius + (radius - startRadius) * progress(tick)) * canvas.scale();
        // Keep the spacing between particles roughly constant as the ring gets wider.
        int count = canvas.points((int) Math.max(12, points * current / radius));
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2 * i / count;
            canvas.particle(particle, Math.cos(angle) * current, offsetY, Math.sin(angle) * current);
        }
    }
}
