package com.mdevstudio.core.hook;

import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.inventory.ItemStack;

// Loaded only when ItemsAdder is installed and switched on in custom-items.
public final class ItemsAdderHook {

    private ItemsAdderHook() {
    }

    public static ItemStack create(String id) {
        CustomStack stack = CustomStack.getInstance(id);
        return stack == null ? null : stack.getItemStack();
    }
}
