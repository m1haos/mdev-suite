package com.mdevstudio.eventide.menu;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mdevstudio.eventide.EventideSettings;
import com.mdevstudio.eventide.EventidePlugin;
import com.mdevstudio.eventide.command.EventideCommand;
import com.mdevstudio.eventide.definition.EventDefinition;
import com.mdevstudio.eventide.running.ActiveEvent;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ScheduleDialog {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private ScheduleDialog() {
    }

    public static void show(EventidePlugin plugin, CommandSender sender) {
        EventideSettings.MenuMode mode = plugin.settings().menu();
        if (mode == EventideSettings.MenuMode.OFF && !sender.hasPermission(EventideCommand.ADMIN_PERMISSION)) {
            plugin.messages().send(sender, "schedule.disabled");
            return;
        }
        List<Component> lines = lines(plugin, sender);
        if (mode == EventideSettings.MenuMode.DIALOG && sender instanceof Player player) {
            try {
                List<DialogBody> body = new ArrayList<>();
                lines.forEach(line -> body.add(DialogBody.plainMessage(line, 300)));
                Dialog dialog = Dialog.create(builder -> builder.empty()
                        .base(DialogBase.builder(plugin.messages().get(player, "schedule.title")).body(body).build())
                        .type(DialogType.notice()));
                player.showDialog(dialog);
                return;
            } catch (LinkageError e) {
                // Dialog API is still marked experimental by Paper. If a future server version
                // changes it, players get the same list in chat instead of an error.
                plugin.getSLF4JLogger().debug("Dialogs unavailable, falling back to chat", e);
            }
        }
        sender.sendMessage(plugin.messages().get(sender, "schedule.title"));
        lines.forEach(sender::sendMessage);
    }

    private static List<Component> lines(EventidePlugin plugin, CommandSender sender) {
        var messages = plugin.messages();
        List<Component> lines = new ArrayList<>();
        for (EventDefinition definition : plugin.definitions().values()) {
            TagResolver name = Placeholder.parsed("event", definition.displayName());
            Optional<ActiveEvent> running = plugin.manager().get(definition.id());
            if (running.isPresent()) {
                ActiveEvent active = running.get();
                lines.add(messages.get(sender, "schedule.running", active.placeholders()));
                continue;
            }
            lines.add(plugin.scheduler().nextStart(definition)
                    .map(next -> messages.get(sender, "schedule.next", name,
                            Placeholder.unparsed("time", TIME.format(next))))
                    .orElseGet(() -> messages.get(sender, randomOnly(plugin, definition)
                            ? "schedule.random" : "schedule.manual", name)));
        }
        if (lines.isEmpty()) {
            lines.add(messages.get(sender, "schedule.empty"));
        }
        return lines;
    }

    private static boolean randomOnly(EventidePlugin plugin, EventDefinition definition) {
        return definition.random().isPresent() && plugin.randomEvents().running();
    }
}
