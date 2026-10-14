package com.mdevstudio.core.hook;

import com.nexomc.nexo.api.NexoItems;
import com.nexomc.nexo.items.ItemBuilder;
import org.bukkit.inventory.ItemStack;

// Loaded only when Nexo is installed and switched on in custom-items.
public final class NexoHook {

    private NexoHook() {
    }

    public static ItemStack create(String id) {
        ItemBuilder builder = NexoItems.itemFromId(id);
        return builder == null ? null : builder.build();
    }
}
