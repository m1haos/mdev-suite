package com.mdevstudio.eventide.hook;

import org.bukkit.World;

/** A protection plugin that can tell whether anyone has claimed land in a square of blocks. */
interface ClaimSource {

    String plugin();

    /** Corners included; the check covers the whole height of the world. */
    boolean claimed(World world, int minX, int minZ, int maxX, int maxZ);
}
