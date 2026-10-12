package com.mdevstudio.core.effect;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.configuration.ConfigurationSection;

/** A sound at the origin; how far it carries grows with {@code volume}, as in vanilla. */
final class SoundElement implements EffectElement {

    private final Timing timing;
    private final Sound sound;

    SoundElement(ConfigurationSection section) {
        this.timing = Timing.parse(section, 1);
        String key = section.getString("sound", "");
        if (!Key.parseable(key)) {
            throw new IllegalArgumentException("sound: '" + key + "' is not a sound key like entity.generic.explode");
        }
        this.sound = Sound.sound(Key.key(key), Sound.Source.MASTER,
                (float) section.getDouble("volume", 1), (float) section.getDouble("pitch", 1));
    }

    @Override
    public Timing timing() {
        return timing;
    }

    @Override
    public void draw(EffectCanvas canvas, int tick) {
        var at = canvas.origin();
        canvas.world().playSound(sound, at.getX(), at.getY(), at.getZ());
    }
}
