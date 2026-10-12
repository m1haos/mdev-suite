package com.mdevstudio.eventide.definition;

import org.bukkit.configuration.ConfigurationSection;

/**
 * How a build evens out the ground under it: its floor goes to the usual ground height of the area,
 * lower ground is filled up to the build and higher ground inside it is cut away. The few highest and
 * lowest columns are left out of the limits, so one tree stump or pit does not rule a spot out.
 */
public record GroundFit(int fillDepth, int cutHeight, double maxWater) {

    /** Share of columns left out at each end when the limits are checked. */
    public static final double OUTLIERS = 0.05;

    static GroundFit parse(ConfigurationSection section) {
        int fill = section.getInt("fill-depth", 6);
        int cut = section.getInt("cut-height", 6);
        if (fill < 0 || cut < 0) {
            throw new IllegalArgumentException("structure.fill-depth and cut-height cannot be negative");
        }
        String water = section.getString("max-water", "20%");
        double share;
        try {
            share = Double.parseDouble(water.replace("%", "").trim()) / 100;
        } catch (NumberFormatException e) {
            share = -1;
        }
        if (share < 0 || share > 1) {
            throw new IllegalArgumentException("structure.max-water: '" + water + "' must be between 0% and 100%");
        }
        return new GroundFit(fill, cut, share);
    }
}
