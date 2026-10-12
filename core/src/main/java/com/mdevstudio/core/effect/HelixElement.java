package com.mdevstudio.core.effect;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Spirals that climb from the ground to {@code height} over the duration. Each tick draws only
 * the newly reached part, so the spiral visibly grows instead of appearing at once.
 */
final class HelixElement extends ShapeElement {

    private final double radius;
    private final double height;
    private final double turns;
    private final int strands;
    private final int points;

    HelixElement(ConfigurationSection section) {
        super(section, 20);
        this.radius = section.getDouble("radius", 1);
        this.height = section.getDouble("height", 3);
        this.turns = section.getDouble("turns", 2);
        this.strands = Math.max(1, section.getInt("strands", 2));
        this.points = section.getInt("points", 60);
    }

    @Override
    public void draw(EffectCanvas canvas, int tick) {
        int total = canvas.points(points);
        int from = (int) Math.floor(total * (tick / (double) timing.duration()));
        int to = (int) Math.floor(total * ((tick + timing.interval()) / (double) timing.duration()));
        for (int i = from; i < Math.min(to, total); i++) {
            double along = i / (double) total;
            for (int strand = 0; strand < strands; strand++) {
                double angle = along * turns * Math.PI * 2 + Math.PI * 2 * strand / strands;
                canvas.particle(particle, Math.cos(angle) * radius * canvas.scale(), offsetY + along * height,
                        Math.sin(angle) * radius * canvas.scale());
            }
        }
    }
}
