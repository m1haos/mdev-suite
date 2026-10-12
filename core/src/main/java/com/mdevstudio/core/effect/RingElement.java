package com.mdevstudio.core.effect;

import org.bukkit.configuration.ConfigurationSection;

/** A horizontal circle; {@code rotate} spins it by that many degrees per tick. */
final class RingElement extends ShapeElement {

    private final double radius;
    private final int points;
    private final double rotate;

    RingElement(ConfigurationSection section) {
        super(section, 1);
        this.radius = section.getDouble("radius", 1.5);
        this.points = section.getInt("points", 32);
        this.rotate = Math.toRadians(section.getDouble("rotate", 0));
    }

    @Override
    public void draw(EffectCanvas canvas, int tick) {
        int count = canvas.scaledPoints(points);
        double start = rotate * tick;
        for (int i = 0; i < count; i++) {
            double angle = start + Math.PI * 2 * i / count;
            canvas.particle(particle, Math.cos(angle) * radius * canvas.scale(), offsetY,
                    Math.sin(angle) * radius * canvas.scale());
        }
    }
}
