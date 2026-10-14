package com.mdevstudio.core.hook;

import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.item.BukkitItemDefinition;
import org.bukkit.inventory.ItemStack;

// Loaded only when CraftEngine is installed and switched on in custom-items.
public final class CraftEngineHook {

    private CraftEngineHook() {
    }

    public static ItemStack create(String id) {
        BukkitItemDefinition definition = CraftEngineItems.byId(id);
        return definition == null ? null : definition.buildBukkitItem();
    }
}
