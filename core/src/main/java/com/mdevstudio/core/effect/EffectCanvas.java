package com.mdevstudio.core.effect;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

/**
 * Where an effect is being drawn, how dense it should be and how wide. {@code scale} stretches
 * the radius of rings, shockwaves, spheres and spirals, so one effect can outline zones of any size.
 */
public record EffectCanvas(Plugin plugin, Location origin, double density, double scale) {

    public World world() {
        return origin.getWorld();
    }

    public void particle(ParticleSpec spec, double dx, double dy, double dz) {
        spec.spawn(origin.getWorld(), origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
    }

    /** Point counts follow the global density setting but never drop to zero. */
    public int points(int configured) {
        return Math.max(1, (int) Math.round(configured * density));
    }

    /** Like {@link #points(int)}, but also grows with the scale so a wider ring is not sparser. */
    public int scaledPoints(int configured) {
        return points((int) Math.round(configured * Math.max(1, scale)));
    }
}
