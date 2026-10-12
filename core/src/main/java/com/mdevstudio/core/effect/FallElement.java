package com.mdevstudio.core.effect;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Something falling from the sky onto the origin: a head of {@code particle} and an optional
 * {@code trail} behind it. Put the impact in the same effect with {@code delay} equal to this duration.
 */
final class FallElement extends ShapeElement {

    private final double height;
    private final double drift;
    private final double angle;
    private final ParticleSpec trail;
    private final int trailLength;

    FallElement(ConfigurationSection section) {
        super(section, 40);
        this.height = section.getDouble("height", 40);
        this.drift = section.getDouble("drift", 15);
        this.angle = Math.toRadians(section.getDouble("angle", 45));
        ConfigurationSection trailSection = section.getConfigurationSection("trail");
        this.trail = trailSection == null ? null : ParticleSpec.parse(trailSection);
        this.trailLength = trailSection == null ? 0 : trailSection.getInt("length", 6);
    }

    @Override
    public void draw(EffectCanvas canvas, int tick) {
        drawAt(canvas, particle, 1 - progress(tick));
        if (trail == null) {
            return;
        }
        double step = 1.0 / Math.max(1, timing.duration());
        for (int i = 1; i <= canvas.points(trailLength); i++) {
            double left = 1 - progress(tick) + step * i * 0.5;
            if (left <= 1) {
                drawAt(canvas, trail, left);
            }
        }
    }

    // left = 1 at the start of the fall, 0 on impact.
    private void drawAt(EffectCanvas canvas, ParticleSpec spec, double left) {
        canvas.particle(spec, Math.cos(angle) * drift * left, offsetY + height * left, Math.sin(angle) * drift * left);
    }
}
