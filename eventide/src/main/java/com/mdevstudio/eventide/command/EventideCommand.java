package com.mdevstudio.eventide.command;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import com.mdevstudio.core.MDevCore;
import com.mdevstudio.core.economy.Money;
import com.mdevstudio.core.reward.BuiltinRewards;
import com.mdevstudio.core.util.Durations;
import com.mdevstudio.eventide.EventidePlugin;
import com.mdevstudio.eventide.EventideSettings;
import com.mdevstudio.eventide.definition.EventDefinition;
import com.mdevstudio.eventide.loot.Rarity;
import com.mdevstudio.eventide.menu.LootEditor;
import com.mdevstudio.eventide.menu.ScheduleDialog;
import com.mdevstudio.eventide.running.ActiveEvent;
import com.mdevstudio.eventide.running.RandomEvents;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

public final class EventideCommand {

    public static final String NAME = "eventide";
    public static final String ADMIN_PERMISSION = "eventide.admin";
    public static final String BUY_KEY_PERMISSION = "eventide.command.buykey";
    public static final String TELEPORT_PERMISSION = "eventide.command.tp";
    // Subcommand names; the aliases for each are set in config.yml under commands.subcommands.
    public static final String HELP = "help";
    public static final String START = "start";
    public static final String STOP = "stop";
    public static final String TELEPORT = "tp";
    public static final String GIVE_KEY = "givekey";
    public static final String BUY_KEY = "buykey";
    public static final String LOOT = "loot";
    public static final String RANDOM = "random";
    public static final String RELOAD = "reload";
    public static final List<String> SUBCOMMANDS = List.of(HELP, START, STOP, TELEPORT, GIVE_KEY, BUY_KEY,
            LOOT, RANDOM, RELOAD);

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final EventidePlugin plugin;

    public EventideCommand(EventidePlugin plugin) {
        this.plugin = plugin;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(NAME).executes(context -> {
            ScheduleDialog.show(plugin, sender(context));
            return Command.SINGLE_SUCCESS;
        });
        add(root, HELP, this::help);
        add(root, START, this::startCommand);
        add(root, STOP, this::stopCommand);
        add(root, TELEPORT, this::teleportCommand);
        add(root, GIVE_KEY, this::giveKeyCommand);
        add(root, BUY_KEY, this::buyKeyCommand);
        add(root, LOOT, this::lootCommand);
        add(root, RANDOM, this::randomCommand);
        add(root, RELOAD, this::reloadCommand);
        return root.build();
    }

    // Every alias gets its own copy of the subcommand, so tab completion shows it like the real name.
    private void add(LiteralArgumentBuilder<CommandSourceStack> root, String name,
            Function<String, LiteralArgumentBuilder<CommandSourceStack>> subcommand) {
        root.then(subcommand.apply(name));
        plugin.settings().commands().aliasesOf(name).forEach(alias -> root.then(subcommand.apply(alias)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> help(String name) {
        return Commands.literal(name).executes(context -> {
            CommandSender sender = sender(context);
            plugin.messages().send(sender, "help.header");
            helpLine(sender, null, "help.menu");
            helpLine(sender, START, "help.start");
            helpLine(sender, STOP, "help.stop");
            helpLine(sender, TELEPORT, "help.tp");
            helpLine(sender, GIVE_KEY, "help.givekey");
            helpLine(sender, BUY_KEY, "help.buykey");
            helpLine(sender, LOOT, "help.loot");
            helpLine(sender, RANDOM, "help.random");
            helpLine(sender, RELOAD, "help.reload");
            return Command.SINGLE_SUCCESS;
        });
    }

    private void helpLine(CommandSender sender, String subcommand, String key) {
        if (subcommand != null && !sender.hasPermission(permissionOf(subcommand))) {
            return;
        }
        List<String> aliases = subcommand == null ? plugin.settings().commands().aliases()
                : plugin.settings().commands().aliasesOf(subcommand);
        plugin.messages().send(sender, key, Placeholder.unparsed("aliases",
                aliases.isEmpty() ? "" : String.join(", ", aliases)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> startCommand(String name) {
        return Commands.literal(name)
                .requires(this::isAdmin)
                .then(Commands.argument("event", StringArgumentType.word())
                        .suggests((context, builder) -> suggest(builder, plugin.definitions().keySet()))
                        .executes(this::start));
    }

    private LiteralArgumentBuilder<CommandSourceStack> stopCommand(String name) {
        return Commands.literal(name)
                .requires(this::isAdmin)
                .then(Commands.argument("event", StringArgumentType.word())
                        .suggests((context, builder) -> suggest(builder, activeIds()))
                        .executes(this::stop));
    }

    private LiteralArgumentBuilder<CommandSourceStack> teleportCommand(String name) {
        return Commands.literal(name)
                .requires(source -> source.getSender().hasPermission(TELEPORT_PERMISSION)
                        && source.getExecutor() instanceof Player)
                .executes(this::teleportToOnly)
                .then(Commands.argument("event", StringArgumentType.word())
                        .suggests((context, builder) -> suggest(builder, activeIds()))
                        .executes(context -> {
                            Player player = (Player) context.getSource().getExecutor();
                            String id = StringArgumentType.getString(context, "event").toLowerCase(Locale.ROOT);
                            Optional<ActiveEvent> event = plugin.manager().get(id);
                            if (event.isEmpty()) {
                                plugin.messages().send(player, "command.not-running", Placeholder.unparsed("id", id));
                                return 0;
                            }
                            teleport(player, event.get());
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    // Without an event name: straight there if only one runs, otherwise a list to click.
    private int teleportToOnly(CommandContext<CommandSourceStack> context) {
        Player player = (Player) context.getSource().getExecutor();
        Collection<ActiveEvent> running = plugin.manager().active();
        if (running.isEmpty()) {
            plugin.messages().send(player, "command.tp-none");
            return 0;
        }
        if (running.size() == 1) {
            teleport(player, running.iterator().next());
            return Command.SINGLE_SUCCESS;
        }
        plugin.messages().send(player, "command.tp-list");
        for (ActiveEvent event : running) {
            Location at = event.location();
            String command = "/" + NAME + " " + TELEPORT + " " + event.definition().id();
            player.sendMessage(plugin.messages().get(player, "command.tp-line", event.placeholders(),
                            Placeholder.unparsed("x", Integer.toString(at.getBlockX())),
                            Placeholder.unparsed("z", Integer.toString(at.getBlockZ())),
                            Placeholder.unparsed("world", at.getWorld().getName()))
                    .clickEvent(ClickEvent.runCommand(command))
                    .hoverEvent(HoverEvent.showText(plugin.messages().get(player, "command.tp-hover"))));
        }
        return Command.SINGLE_SUCCESS;
    }

    // The spot is the ground the event stands on; the highest block puts the player on top of a crate
    // or a meteor that has landed there since, not inside it.
    private void teleport(Player player, ActiveEvent event) {
        Location at = event.location();
        World world = at.getWorld();
        world.getChunkAtAsync(at).thenAccept(chunk -> {
            Location target = world.getHighestBlockAt(at.getBlockX(), at.getBlockZ())
                    .getLocation().add(0.5, 1, 0.5);
            target.setYaw(player.getLocation().getYaw());
            target.setPitch(player.getLocation().getPitch());
            player.teleportAsync(target, TeleportCause.COMMAND).thenAccept(moved -> {
                if (moved) {
                    plugin.messages().send(player, "command.teleported", event.placeholders());
                }
            });
        });
    }

    private LiteralArgumentBuilder<CommandSourceStack> giveKeyCommand(String name) {
        return Commands.literal(name)
                .requires(this::isAdmin)
                .then(Commands.argument("player", ArgumentTypes.player())
                        .then(Commands.argument("rarity", StringArgumentType.word())
                                .suggests((context, builder) -> suggest(builder, lockedRarities(false)))
                                .executes(context -> giveKey(context, 1))
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                        .executes(context -> giveKey(context,
                                                IntegerArgumentType.getInteger(context, "amount"))))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> buyKeyCommand(String name) {
        return Commands.literal(name)
                .requires(source -> source.getSender().hasPermission(BUY_KEY_PERMISSION)
                        && source.getExecutor() instanceof Player)
                .then(Commands.argument("rarity", StringArgumentType.word())
                        .suggests((context, builder) -> suggest(builder, lockedRarities(true)))
                        .executes(context -> buyKey(context, 1))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                .executes(context -> buyKey(context,
                                        IntegerArgumentType.getInteger(context, "amount")))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> lootCommand(String name) {
        return Commands.literal(name)
                .requires(source -> isAdmin(source) && source.getExecutor() instanceof Player)
                .executes(context -> {
                    new LootEditor(plugin).open((Player) context.getSource().getExecutor());
                    return Command.SINGLE_SUCCESS;
                });
    }

    private LiteralArgumentBuilder<CommandSourceStack> randomCommand(String name) {
        return Commands.literal(name)
                .requires(this::isAdmin)
                .executes(this::randomStatus)
                .then(Commands.literal("pause").executes(context -> setRandomPaused(context, true)))
                .then(Commands.literal("resume").executes(context -> setRandomPaused(context, false)))
                .then(Commands.literal("roll").executes(this::randomRoll));
    }

    private LiteralArgumentBuilder<CommandSourceStack> reloadCommand(String name) {
        return Commands.literal(name)
                .requires(this::isAdmin)
                .executes(context -> {
                    plugin.reload();
                    plugin.messages().send(sender(context), "command.reloaded",
                            Placeholder.unparsed("events", Integer.toString(plugin.definitions().size())),
                            Placeholder.unparsed("rarities", Integer.toString(plugin.rarities().size())));
                    return Command.SINGLE_SUCCESS;
                });
    }

    private int start(CommandContext<CommandSourceStack> context) {
        CommandSender sender = sender(context);
        String id = StringArgumentType.getString(context, "event").toLowerCase(Locale.ROOT);
        EventDefinition definition = plugin.definitions().get(id);
        var name = Placeholder.unparsed("id", id);
        if (definition == null) {
            plugin.messages().send(sender, "command.unknown-event", name);
            return 0;
        }
        String key = switch (plugin.manager().start(definition, reason -> plugin.messages().send(sender,
                "command.start-failed", name, Placeholder.unparsed("reason", reason)))) {
            case STARTING -> "command.starting";
            case ALREADY_RUNNING -> "command.already-running";
            case TOO_MANY_ACTIVE -> "command.too-many";
            case UNKNOWN_LOOT_TABLE -> "command.unknown-loot";
        };
        plugin.messages().send(sender, key, name,
                Placeholder.parsed("event", definition.displayName()),
                Placeholder.unparsed("loot", definition.lootTable()),
                Placeholder.unparsed("max", Integer.toString(plugin.settings().maxActive())));
        return Command.SINGLE_SUCCESS;
    }

    private int stop(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "event").toLowerCase(Locale.ROOT);
        String key = plugin.manager().stop(id) ? "command.stopped" : "command.not-running";
        plugin.messages().send(sender(context), key, Placeholder.unparsed("id", id));
        return Command.SINGLE_SUCCESS;
    }

    private int randomStatus(CommandContext<CommandSourceStack> context) {
        CommandSender sender = sender(context);
        RandomEvents random = plugin.randomEvents();
        if (!random.running()) {
            plugin.messages().send(sender, "random.disabled");
            return 0;
        }
        if (random.paused()) {
            plugin.messages().send(sender, "random.status-paused");
        } else {
            plugin.messages().send(sender, "random.status-next",
                    Placeholder.unparsed("time", TIME.format(random.nextRoll().atZone(plugin.settings().timezone()))),
                    Placeholder.unparsed("in", Durations.clock(random.untilNextRoll().orElse(Duration.ZERO))));
        }
        Map<String, Duration> rarities = random.rarityCooldowns();
        Map<String, Duration> events = random.eventCooldowns();
        if (rarities.isEmpty() && events.isEmpty()) {
            plugin.messages().send(sender, "random.no-cooldowns");
            return Command.SINGLE_SUCCESS;
        }
        rarities.forEach((id, left) -> plugin.messages().send(sender, "random.rarity-cooldown",
                Placeholder.unparsed("rarity", id), Placeholder.unparsed("left", Durations.clock(left))));
        events.forEach((id, left) -> plugin.messages().send(sender, "random.event-cooldown",
                Placeholder.parsed("event", Optional.ofNullable(plugin.definitions().get(id))
                        .map(EventDefinition::displayName).orElse(id)),
                Placeholder.unparsed("left", Durations.clock(left))));
        return Command.SINGLE_SUCCESS;
    }

    private int setRandomPaused(CommandContext<CommandSourceStack> context, boolean paused) {
        CommandSender sender = sender(context);
        if (!plugin.randomEvents().running()) {
            plugin.messages().send(sender, "random.disabled");
            return 0;
        }
        plugin.randomEvents().setPaused(paused);
        plugin.messages().send(sender, paused ? "random.paused" : "random.resumed");
        return Command.SINGLE_SUCCESS;
    }

    private int randomRoll(CommandContext<CommandSourceStack> context) {
        CommandSender sender = sender(context);
        if (!plugin.randomEvents().running()) {
            plugin.messages().send(sender, "random.disabled");
            return 0;
        }
        RandomEvents.Roll roll = plugin.randomEvents().roll(true);
        if (roll.skip() == null) {
            plugin.messages().send(sender, "random.rolled",
                    Placeholder.parsed("event", roll.event().displayName()),
                    Placeholder.unparsed("rarity", roll.rarity()));
            return Command.SINGLE_SUCCESS;
        }
        plugin.messages().send(sender, switch (roll.skip()) {
            case TOO_MANY_RUNNING -> "random.too-many";
            case START_FAILED -> "random.start-failed";
            default -> "random.nothing";
        }, Placeholder.parsed("event", roll.event() == null ? "" : roll.event().displayName()),
                Placeholder.unparsed("reason", roll.reason() == null ? "" : roll.reason()),
                Placeholder.unparsed("max", Integer.toString(plugin.settings().random().maxConcurrent())));
        return 0;
    }

    private int giveKey(CommandContext<CommandSourceStack> context, int amount) throws CommandSyntaxException {
        CommandSender sender = sender(context);
        Player target = context.getArgument("player", PlayerSelectorArgumentResolver.class)
                .resolve(context.getSource()).getFirst();
        String rarityId = StringArgumentType.getString(context, "rarity");
        Rarity rarity = plugin.rarity(rarityId).orElse(null);
        if (rarity == null || !rarity.locked()) {
            plugin.messages().send(sender, rarity == null ? "command.unknown-rarity" : "command.keyless-rarity",
                    Placeholder.unparsed("rarity", rarityId));
            return 0;
        }
        BuiltinRewards.give(target, plugin.keys().create(rarity, amount));
        plugin.messages().send(sender, "command.key-given",
                Placeholder.parsed("rarity", rarity.displayName()),
                Placeholder.unparsed("player", target.getName()),
                Placeholder.unparsed("amount", Integer.toString(amount)));
        return Command.SINGLE_SUCCESS;
    }

    private int buyKey(CommandContext<CommandSourceStack> context, int amount) {
        Player player = (Player) context.getSource().getExecutor();
        String rarityId = StringArgumentType.getString(context, "rarity");
        EventideSettings.Keys keys = plugin.settings().modules().keys();
        if (!keys.enabled() || !keys.shop()) {
            plugin.messages().send(player, "shop.disabled");
            return 0;
        }
        Rarity rarity = plugin.rarity(rarityId).filter(Rarity::forSale).orElse(null);
        if (rarity == null) {
            plugin.messages().send(player, "shop.not-for-sale", Placeholder.unparsed("rarity", rarityId));
            return 0;
        }
        Optional<Money> money = MDevCore.money();
        if (money.isEmpty()) {
            plugin.messages().send(player, "shop.no-economy");
            return 0;
        }
        double total = rarity.key().price() * amount;
        var price = Placeholder.unparsed("price", money.get().format(total));
        var rarityName = Placeholder.parsed("rarity", rarity.displayName());
        if (!money.get().withdraw(player, total)) {
            plugin.messages().send(player, "shop.not-enough", rarityName, price);
            return 0;
        }
        BuiltinRewards.give(player, plugin.keys().create(rarity, amount));
        plugin.messages().send(player, "shop.bought", rarityName, price,
                Placeholder.unparsed("amount", Integer.toString(amount)));
        return Command.SINGLE_SUCCESS;
    }

    private Collection<String> lockedRarities(boolean forSaleOnly) {
        return plugin.rarities().values().stream()
                .filter(forSaleOnly ? Rarity::forSale : Rarity::locked)
                .map(Rarity::id)
                .toList();
    }

    private Collection<String> activeIds() {
        return plugin.manager().active().stream().map(active -> active.definition().id()).toList();
    }

    private static String permissionOf(String subcommand) {
        return switch (subcommand) {
            case BUY_KEY -> BUY_KEY_PERMISSION;
            case TELEPORT -> TELEPORT_PERMISSION;
            default -> ADMIN_PERMISSION;
        };
    }

    private boolean isAdmin(CommandSourceStack source) {
        return source.getSender().hasPermission(ADMIN_PERMISSION);
    }

    private static CommandSender sender(CommandContext<CommandSourceStack> context) {
        return context.getSource().getSender();
    }

    private static CompletableFuture<Suggestions> suggest(SuggestionsBuilder builder, Collection<String> values) {
        String typed = builder.getRemainingLowerCase();
        values.stream().filter(value -> value.startsWith(typed)).forEach(builder::suggest);
        return builder.buildFuture();
    }
}
