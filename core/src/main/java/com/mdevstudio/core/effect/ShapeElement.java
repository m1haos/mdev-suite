package com.mdevstudio.core.effect;

import org.bukkit.configuration.ConfigurationSection;

/** Shared settings of particle shapes: the particle, timing and a vertical offset. */
abstract class ShapeElement implements EffectElement {

    protected final ParticleSpec particle;
    protected final Timing timing;
    protected final double offsetY;

    ShapeElement(ConfigurationSection section, int defaultDuration) {
        this.particle = ParticleSpec.parse(section);
        this.timing = Timing.parse(section, defaultDuration);
        this.offsetY = section.getDouble("y", 0);
    }

    @Override
    public Timing timing() {
        return timing;
    }

    /** 0 on the first drawn tick, 1 on the last. */
    protected double progress(int tick) {
        return timing.duration() <= 1 ? 1 : tick / (double) (timing.duration() - 1);
    }
}
