package com.mdevstudio.core.effect;

import org.bukkit.configuration.ConfigurationSection;

/** Evenly spread points on a sphere; {@code grow: true} inflates it over the duration. */
final class SphereElement extends ShapeElement {

    private static final double GOLDEN_ANGLE = Math.PI * (3 - Math.sqrt(5));

    private final double radius;
    private final int points;
    private final boolean grow;

    SphereElement(ConfigurationSection section) {
        super(section, 1);
        this.radius = section.getDouble("radius", 2);
        this.points = section.getInt("points", 60);
        this.grow = section.getBoolean("grow", false);
    }

    @Override
    public void draw(EffectCanvas canvas, int tick) {
        double current = (grow ? radius * progress(tick) : radius) * canvas.scale();
        int count = canvas.scaledPoints(points);
        for (int i = 0; i < count; i++) {
            double y = 1 - 2 * (i + 0.5) / count;
            double ring = Math.sqrt(1 - y * y);
            double angle = GOLDEN_ANGLE * i;
            canvas.particle(particle, Math.cos(angle) * ring * current, offsetY + y * current, Math.sin(angle) * ring * current);
        }
    }
}
