package com.mdevstudio.core.effect;

import org.bukkit.configuration.ConfigurationSection;

/** A vertical beam that rises to {@code height} over the duration, then stays until it ends. */
final class ColumnElement extends ShapeElement {

    private final double height;
    private final int points;
    private final double riseShare;

    ColumnElement(ConfigurationSection section) {
        super(section, 1);
        this.height = section.getDouble("height", 10);
        this.points = section.getInt("points", 30);
        this.riseShare = Math.clamp(section.getDouble("rise", 0.3), 0.01, 1);
    }

    @Override
    public void draw(EffectCanvas canvas, int tick) {
        double reached = Math.min(1, progress(tick) / riseShare);
        int count = canvas.points(points);
        for (int i = 0; i < count; i++) {
            double along = i / (double) count;
            if (along > reached) {
                break;
            }
            canvas.particle(particle, 0, offsetY + along * height, 0);
        }
    }
}
