package com.mdevstudio.eventide.definition;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Something an event region can forbid. Names in config files are the constant names in kebab-case. */
public enum RegionFlag {
    BREAK,
    PLACE,
    BUCKETS,
    CONTAINERS,
    USE,
    EXPLOSIONS,
    FIRE,
    PISTONS,
    FLUIDS,
    MOB_GRIEFING,
    MOB_SPAWNING,
    PVP,
    ENDER_PEARLS;

    /** Always forbidden inside an event build: it is rolled back at the end, so changes there are dupes or losses. */
    public static final Set<RegionFlag> BUILD = EnumSet.of(BREAK, PLACE, BUCKETS, CONTAINERS, EXPLOSIONS, FIRE,
            PISTONS, FLUIDS, MOB_GRIEFING);

    public String configName() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static RegionFlag parse(String name) {
        String clean = name.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return valueOf(clean);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("'" + name + "' is not one of " + Arrays.stream(values())
                    .map(RegionFlag::configName)
                    .collect(Collectors.joining(", ")));
        }
    }
}
