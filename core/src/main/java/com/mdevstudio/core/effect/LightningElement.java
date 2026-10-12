package com.mdevstudio.core.effect;

import org.bukkit.configuration.ConfigurationSection;

/** A lightning flash and thunder with no damage and no fire. */
final class LightningElement implements EffectElement {

    private final Timing timing;

    LightningElement(ConfigurationSection section) {
        this.timing = Timing.parse(section, 1);
    }

    @Override
    public Timing timing() {
        return timing;
    }

    @Override
    public void draw(EffectCanvas canvas, int tick) {
        canvas.world().strikeLightningEffect(canvas.origin());
    }
}
