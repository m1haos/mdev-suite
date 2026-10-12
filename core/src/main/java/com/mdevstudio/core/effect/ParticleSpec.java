package com.mdevstudio.core.effect;

import java.util.Locale;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

/**
 * One particle as written in an effect file:
 * <pre>
 * particle: dust
 * color: "#FF8A3D"      # dust, dust_color_transition, entity_effect
 * to-color: "#FFD166"   # dust_color_transition
 * size: 1.5             # dust sizes
 * block: magma_block    # block, falling_dust, block_marker, dust_pillar
 * item: blaze_powder    # item
 * count: 1
 * spread: 0.1           # random offset on every axis
 * speed: 0
 * force: false          # true = visible from far away, for beams and sky effects
 * </pre>
 */
public record ParticleSpec(Particle particle, int count, double spread, double speed, Object data, boolean force) {

    public static ParticleSpec parse(ConfigurationSection section) {
        String name = section.getString("particle", "").toLowerCase(Locale.ROOT);
        NamespacedKey key = NamespacedKey.fromString(name);
        Particle particle = key == null ? null : Registry.PARTICLE_TYPE.get(key);
        if (particle == null) {
            throw new IllegalArgumentException("unknown particle '" + name + "'");
        }
        return new ParticleSpec(
                particle,
                Math.max(1, section.getInt("count", 1)),
                section.getDouble("spread", 0),
                section.getDouble("speed", 0),
                data(particle, section),
                section.getBoolean("force", false));
    }

    public void spawn(World world, double x, double y, double z) {
        world.spawnParticle(particle, x, y, z, count, spread, spread, spread, speed, data, force);
    }

    private static Object data(Particle particle, ConfigurationSection section) {
        Class<?> type = particle.getDataType();
        if (type == Void.class) {
            return null;
        }
        float size = (float) section.getDouble("size", 1);
        if (type == Particle.DustTransition.class) {
            return new Particle.DustTransition(color(section, "color"), color(section, "to-color"), size);
        }
        if (type == Particle.DustOptions.class) {
            return new Particle.DustOptions(color(section, "color"), size);
        }
        if (type == Color.class) {
            return color(section, "color");
        }
        if (type == BlockData.class) {
            return material(section, "block").createBlockData();
        }
        if (type == ItemStack.class) {
            return ItemStack.of(material(section, "item"));
        }
        if (type == Float.class) {
            return (float) section.getDouble("value", 0);
        }
        if (type == Integer.class) {
            return section.getInt("value", 0);
        }
        throw new IllegalArgumentException("particle '" + particle.getKey().getKey() + "' needs data of a kind effects do not support yet");
    }

    static Color color(ConfigurationSection section, String key) {
        return color(section.getString(key, "#FFFFFF"), key);
    }

    static Color color(String value, String key) {
        String hex = value.trim();
        try {
            return Color.fromRGB(Integer.parseInt(hex.startsWith("#") ? hex.substring(1) : hex, 16));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(key + ": '" + hex + "' is not a color like #FF8A3D");
        }
    }

    private static Material material(ConfigurationSection section, String key) {
        Material material = Material.matchMaterial(section.getString(key, ""));
        if (material == null) {
            throw new IllegalArgumentException(key + ": unknown material '" + section.getString(key) + "'");
        }
        return material;
    }
}
