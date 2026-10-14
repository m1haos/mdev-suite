package com.mdevstudio.eventide.definition;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Ground an event may start on, checked in a square around the spot. The radius is kept even when
 * the check is off: it also sizes the area other events keep away from.
 */
public record SurfaceRule(boolean checked, int radius, int maxHeightDifference, double minGround,
        Set<Material> ground) {

    private static final Set<Material> LEGACY_DIRT = EnumSet.of(Material.GRASS_BLOCK, Material.PODZOL,
            Material.MYCELIUM, Material.MOSS_BLOCK, Material.PALE_MOSS_BLOCK, Material.MUD,
            Material.MUDDY_MANGROVE_ROOTS);

    public SurfaceRule {
        ground = Collections.unmodifiableSet(
                ground.isEmpty() ? EnumSet.noneOf(Material.class) : EnumSet.copyOf(ground));
    }

    /** Keys the same as {@code spot-check} in config.yml; missing keys keep the values of {@code base}. */
    public static SurfaceRule parse(ConfigurationSection section, SurfaceRule base) {
        if (section == null) {
            return base;
        }
        return new SurfaceRule(
                base.checked(),
                Math.max(0, section.getInt("radius", base.radius())),
                Math.max(0, section.getInt("max-height-difference", base.maxHeightDifference())),
                section.contains("min-ground") ? share(section.getString("min-ground")) : base.minGround(),
                section.contains("ground") ? ground(section.getStringList("ground")) : base.ground());
    }

    public SurfaceRule checked(boolean checked) {
        return new SurfaceRule(checked, radius, maxHeightDifference, minGround, ground);
    }

    public SurfaceRule withRadiusAtLeast(int reach) {
        return new SurfaceRule(checked, Math.max(radius, reach), maxHeightDifference, minGround, ground);
    }

    private static double share(String value) {
        try {
            double share = Double.parseDouble(value.replace("%", "").trim()) / 100;
            if (share >= 0 && share <= 1) {
                return share;
            }
        } catch (NumberFormatException ignored) {
            // falls through to the same message as an out-of-range value
        }
        throw new IllegalArgumentException("min-ground: '" + value + "' must be between 0% and 100%");
    }

    private static Set<Material> ground(List<String> names) {
        Set<Material> ground = EnumSet.noneOf(Material.class);
        for (String name : names) {
            String clean = name.trim().toLowerCase(Locale.ROOT);
            if (clean.startsWith("#")) {
                NamespacedKey key = NamespacedKey.fromString(clean.substring(1));
                Tag<Material> tag = key == null ? null : Bukkit.getTag(Tag.REGISTRY_BLOCKS, key, Material.class);
                if (tag == null) {
                    throw new IllegalArgumentException("ground: '" + name + "' is not a block tag");
                }
                ground.addAll(tag.getValues());
                // Since 26.1 #minecraft:dirt no longer holds grass and the like; configs written before
                // that still mean them.
                if (key.equals(NamespacedKey.minecraft("dirt"))) {
                    ground.addAll(LEGACY_DIRT);
                }
            } else {
                Material material = Material.matchMaterial(clean);
                if (material == null || !material.isBlock()) {
                    throw new IllegalArgumentException("ground: '" + name + "' is not a block");
                }
                ground.add(material);
            }
        }
        if (ground.isEmpty()) {
            throw new IllegalArgumentException("ground: the list is empty, no spot would ever fit");
        }
        return ground;
    }
}
