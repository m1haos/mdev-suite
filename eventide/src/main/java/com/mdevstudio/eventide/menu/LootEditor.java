package com.mdevstudio.eventide.menu;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import com.mdevstudio.eventide.EventidePlugin;
import com.mdevstudio.eventide.command.EventideCommand;
import com.mdevstudio.eventide.loot.LootEntry;
import com.mdevstudio.eventide.loot.LootTable;
import com.mdevstudio.eventide.loot.LootTier;
import com.mdevstudio.eventide.loot.LootWriter;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.body.PlainMessageDialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * {@code /eventide loot}: loot tables edited in dialog windows. Tables → tiers → one tier's items,
 * where an item is added from the hand and its weight and amount are set with sliders. Every
 * change is written to the loot file at once; running events keep the loot they started with.
 */
public final class LootEditor {

    // Buttons stay clickable while the window is plausibly still open, then stop doing anything.
    private static final ClickCallback.Options CLICKS = ClickCallback.Options.builder()
            .uses(ClickCallback.UNLIMITED_USES)
            .lifetime(Duration.ofMinutes(15))
            .build();
    private static final int FULL_WIDTH = 310;
    private static final int HALF_WIDTH = 150;
    private static final double NEW_ENTRY_WEIGHT = 10;

    private record Target(LootTable table, LootTier tier) {
    }

    private final EventidePlugin plugin;

    public LootEditor(EventidePlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        if (plugin.lootTables().size() == 1) {
            showTiers(player, plugin.lootTables().keySet().iterator().next());
        } else {
            showTables(player);
        }
    }

    private void showTables(Player player) {
        List<ActionButton> buttons = new ArrayList<>();
        plugin.lootTables().values().forEach(table -> buttons.add(button(
                text(player, "editor.table-button", Placeholder.unparsed("table", table.id()),
                        Placeholder.unparsed("tiers", Integer.toString(table.tiers().size()))),
                FULL_WIDTH, (viewer, response) -> showTiers(viewer, table.id()))));
        List<DialogBody> body = buttons.isEmpty() ? List.of(line(player, "editor.empty")) : List.of();
        if (buttons.isEmpty()) {
            buttons.add(closeButton(player));
        }
        show(player, text(player, "editor.tables-title"), body, List.of(), buttons, closeButton(player), 1);
    }

    private void showTiers(Player player, String tableId) {
        LootTable table = plugin.lootTables().get(tableId);
        if (table == null) {
            gone(player);
            return;
        }
        List<ActionButton> buttons = new ArrayList<>();
        table.tiers().values().forEach(tier -> buttons.add(button(
                text(player, "editor.tier-button", Placeholder.parsed("tier", tier.displayName()),
                        Placeholder.unparsed("count", Integer.toString(tier.entries().size()))),
                FULL_WIDTH, (viewer, response) -> showTier(viewer, tableId, tier.id()))));
        // A multi-action window needs at least one button.
        if (buttons.isEmpty()) {
            buttons.add(closeButton(player));
        }
        ActionButton back = plugin.lootTables().size() > 1
                ? button(text(player, "editor.back"), HALF_WIDTH, (viewer, response) -> showTables(viewer))
                : closeButton(player);
        show(player, text(player, "editor.tiers-title", Placeholder.unparsed("table", tableId)),
                List.of(), List.of(), buttons, back, 1);
    }

    private void showTier(Player player, String tableId, String tierId) {
        Optional<Target> found = find(tableId, tierId);
        if (found.isEmpty()) {
            gone(player);
            return;
        }
        LootTier tier = found.get().tier();
        List<DialogBody> body = new ArrayList<>();
        body.add(line(player, "editor.tier-info", tierInfo(tier)));
        List<ActionButton> buttons = new ArrayList<>();
        for (int i = 0; i < tier.entries().size(); i++) {
            LootEntry entry = tier.entries().get(i);
            int index = i;
            TagResolver details = entryInfo(player, tier, entry, index);
            body.add(DialogBody.item(displayItem(player, entry))
                    .description(line(player, "editor.entry-line", details))
                    .showTooltip(true)
                    .build());
            buttons.add(button(text(player, "editor.entry-button", details), HALF_WIDTH,
                    (viewer, response) -> showEntry(viewer, tableId, tierId, index)));
        }
        buttons.add(button(text(player, "editor.add-hand"), HALF_WIDTH,
                (viewer, response) -> addFromHand(viewer, tableId, tierId)));
        buttons.add(button(text(player, "editor.tier-settings"), HALF_WIDTH,
                (viewer, response) -> showTierSettings(viewer, tableId, tierId)));
        show(player, text(player, "editor.tier-title", Placeholder.parsed("tier", tier.displayName())), body,
                List.of(), buttons,
                button(text(player, "editor.back"), HALF_WIDTH, (viewer, response) -> showTiers(viewer, tableId)), 2);
    }

    private void showEntry(Player player, String tableId, String tierId, int index) {
        Optional<Target> found = find(tableId, tierId).filter(target -> index < target.tier().entries().size());
        if (found.isEmpty()) {
            gone(player);
            return;
        }
        LootTier tier = found.get().tier();
        LootEntry entry = tier.entries().get(index);
        TagResolver details = entryInfo(player, tier, entry, index);
        List<DialogBody> body = List.of(
                DialogBody.item(displayItem(player, entry)).showTooltip(true).build(),
                line(player, "editor.entry-info", details));
        float maxWeight = (float) Math.max(100, Math.ceil(entry.weight()));
        float maxAmount = Math.max(64, entry.maxAmount());
        List<DialogInput> inputs = List.of(
                slider("weight", text(player, "editor.weight-label"), 0, maxWeight, (float) entry.weight()),
                slider("min", text(player, "editor.amount-min-label"), 1, maxAmount, entry.minAmount()),
                slider("max", text(player, "editor.amount-max-label"), 1, maxAmount, entry.maxAmount()));
        List<ActionButton> buttons = List.of(
                button(text(player, "editor.save"), HALF_WIDTH, (viewer, response) -> {
                    int min = Math.round(response.getFloat("min"));
                    int max = Math.round(response.getFloat("max"));
                    write(viewer, tableId, tierId, writer -> writer.update(index,
                            Math.round(response.getFloat("weight")), Math.min(min, max), Math.max(min, max)));
                    showTier(viewer, tableId, tierId);
                }),
                button(text(player, "editor.remove"), HALF_WIDTH,
                        (viewer, response) -> confirmRemove(viewer, tableId, tierId, index)));
        show(player, text(player, "editor.entry-title", details), body, inputs, buttons,
                button(text(player, "editor.back"), HALF_WIDTH, (viewer, response) -> showTier(viewer, tableId, tierId)),
                2);
    }

    private void confirmRemove(Player player, String tableId, String tierId, int index) {
        Optional<Target> found = find(tableId, tierId).filter(target -> index < target.tier().entries().size());
        if (found.isEmpty()) {
            gone(player);
            return;
        }
        LootTier tier = found.get().tier();
        // A tier with no loot fails to load, and every event using the table would go with it.
        if (tier.entries().size() == 1) {
            plugin.messages().send(player, "editor.last-entry");
            showTier(player, tableId, tierId);
            return;
        }
        TagResolver details = entryInfo(player, tier, tier.entries().get(index), index);
        ActionButton yes = button(text(player, "editor.remove-yes"), HALF_WIDTH, (viewer, response) -> {
            if (write(viewer, tableId, tierId, writer -> writer.remove(index))) {
                plugin.messages().send(viewer, "editor.removed");
            }
            showTier(viewer, tableId, tierId);
        });
        ActionButton no = button(text(player, "editor.remove-no"), HALF_WIDTH,
                (viewer, response) -> showEntry(viewer, tableId, tierId, index));
        showConfirmation(player, text(player, "editor.remove-title", details),
                List.of(line(player, "editor.remove-question", details)), yes, no);
    }

    private void showTierSettings(Player player, String tableId, String tierId) {
        Optional<Target> found = find(tableId, tierId);
        if (found.isEmpty()) {
            gone(player);
            return;
        }
        LootTier tier = found.get().tier();
        float maxRolls = Math.max(20, tier.maxRolls());
        List<DialogInput> inputs = List.of(
                slider("rolls-min", text(player, "editor.rolls-min-label"), 1, maxRolls, tier.minRolls()),
                slider("rolls-max", text(player, "editor.rolls-max-label"), 1, maxRolls, tier.maxRolls()),
                slider("points", text(player, "editor.points-label"), 0, Math.max(100, tier.points()), tier.points()));
        ActionButton save = button(text(player, "editor.save"), HALF_WIDTH, (viewer, response) -> {
            int min = Math.round(response.getFloat("rolls-min"));
            int max = Math.round(response.getFloat("rolls-max"));
            write(viewer, tableId, tierId, writer -> writer.updateTier(Math.min(min, max), Math.max(min, max),
                    Math.round(response.getFloat("points"))));
            showTier(viewer, tableId, tierId);
        });
        show(player, text(player, "editor.settings-title", Placeholder.parsed("tier", tier.displayName())),
                List.of(line(player, "editor.settings-info")), inputs, List.of(save),
                button(text(player, "editor.back"), HALF_WIDTH, (viewer, response) -> showTier(viewer, tableId, tierId)),
                1);
    }

    private void addFromHand(Player player, String tableId, String tierId) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.isEmpty()) {
            plugin.messages().send(player, "editor.empty-hand");
            showTier(player, tableId, tierId);
            return;
        }
        int[] added = {-1};
        boolean saved = write(player, tableId, tierId,
                writer -> added[0] = writer.add(hand, hand.getAmount(), NEW_ENTRY_WEIGHT));
        if (saved) {
            plugin.messages().send(player, "editor.added", Placeholder.component("name", hand.effectiveName()));
            showEntry(player, tableId, tierId, added[0]);
        } else {
            showTier(player, tableId, tierId);
        }
    }

    @FunctionalInterface
    private interface Change {
        void apply(LootWriter writer) throws IOException;
    }

    // Writes, then reloads the loot so the next window shows what is now in the file.
    private boolean write(Player player, String tableId, String tierId, Change change) {
        Optional<Target> found = find(tableId, tierId);
        if (found.isEmpty()) {
            gone(player);
            return false;
        }
        try {
            change.apply(new LootWriter(found.get().table(), found.get().tier()));
        } catch (IOException e) {
            plugin.getSLF4JLogger().warn("Loot editor could not save loot/{}: {}",
                    found.get().table().file().getName(), e.getMessage());
            plugin.messages().send(player, "editor.save-failed", Placeholder.unparsed("reason", e.getMessage()));
            return false;
        } finally {
            plugin.reloadLoot();
        }
        plugin.messages().send(player, "editor.saved",
                Placeholder.unparsed("file", found.get().table().file().getName()));
        return true;
    }

    private Optional<Target> find(String tableId, String tierId) {
        LootTable table = plugin.lootTables().get(tableId);
        if (table == null) {
            return Optional.empty();
        }
        return table.tier(tierId).map(tier -> new Target(table, tier));
    }

    private void gone(Player player) {
        plugin.messages().send(player, "editor.gone");
    }

    private ItemStack displayItem(Player player, LootEntry entry) {
        if (entry.hasItem()) {
            return entry.createItem(plugin.text(), 1);
        }
        ItemStack paper = ItemStack.of(Material.PAPER);
        paper.editMeta(meta -> meta.displayName(text(player, "editor.rewards-only")));
        return paper;
    }

    private TagResolver entryInfo(Player player, LootTier tier, LootEntry entry, int index) {
        double total = tier.totalWeight();
        double chance = total <= 0 || entry.weight() <= 0 ? 0 : entry.weight() * 100 / total;
        Component name = entry.hasItem() ? entry.createItem(plugin.text(), 1).effectiveName()
                : text(player, "editor.rewards-only");
        return TagResolver.resolver(
                Placeholder.unparsed("index", Integer.toString(index + 1)),
                Placeholder.component("name", name),
                Placeholder.parsed("tier", tier.displayName()),
                Placeholder.unparsed("weight", number(entry.weight())),
                Placeholder.unparsed("chance", String.format(Locale.ROOT, "%.1f", chance)),
                Placeholder.unparsed("amount", range(entry.minAmount(), entry.maxAmount())),
                Placeholder.unparsed("rolls", range(tier.minRolls(), tier.maxRolls())));
    }

    private TagResolver tierInfo(LootTier tier) {
        return TagResolver.resolver(
                Placeholder.unparsed("rolls", range(tier.minRolls(), tier.maxRolls())),
                Placeholder.unparsed("points", Integer.toString(tier.points())),
                Placeholder.unparsed("price", tier.rarity().forSale() ? number(tier.rarity().key().price()) : "—"),
                Placeholder.parsed("rarity", tier.rarity().displayName()));
    }

    private void show(Player player, Component title, List<DialogBody> body, List<DialogInput> inputs,
            List<ActionButton> buttons, ActionButton exit, int columns) {
        display(player, () -> Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(title).body(body).inputs(inputs).build())
                .type(DialogType.multiAction(buttons).exitAction(exit).columns(columns).build())));
    }

    private void showConfirmation(Player player, Component title, List<DialogBody> body, ActionButton yes,
            ActionButton no) {
        display(player, () -> Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(title).body(body).build())
                .type(DialogType.confirmation(yes, no))));
    }

    private void display(Player player, Supplier<Dialog> dialog) {
        try {
            player.showDialog(dialog.get());
        } catch (LinkageError e) {
            // Dialog API is still marked experimental by Paper; a server where it changed gets a message, not a crash.
            plugin.messages().send(player, "editor.unsupported");
        }
    }

    private ActionButton button(Component label, int width, BiConsumer<Player, DialogResponseView> action) {
        return ActionButton.builder(label)
                .width(width)
                .action(DialogAction.customClick((response, audience) -> {
                    if (audience instanceof Player player && player.hasPermission(EventideCommand.ADMIN_PERMISSION)) {
                        action.accept(player, response);
                    }
                }, CLICKS))
                .build();
    }

    private ActionButton closeButton(Player player) {
        return ActionButton.builder(text(player, "editor.close")).width(HALF_WIDTH).build();
    }

    private static DialogInput slider(String key, Component label, float min, float max, float initial) {
        return DialogInput.numberRange(key, label, min, max)
                .step(1f)
                .initial(Math.clamp(initial, min, max))
                .width(FULL_WIDTH)
                .build();
    }

    private PlainMessageDialogBody line(Player player, String key, TagResolver... placeholders) {
        return DialogBody.plainMessage(text(player, key, placeholders), FULL_WIDTH);
    }

    private Component text(Player player, String key, TagResolver... placeholders) {
        return plugin.messages().get(player, key, placeholders);
    }

    private static String range(int min, int max) {
        return min == max ? Integer.toString(min) : min + "–" + max;
    }

    private static String number(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%.2f", value);
    }
}
