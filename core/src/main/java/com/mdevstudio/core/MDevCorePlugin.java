package com.mdevstudio.core;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.mdevstudio.core.config.YamlFile;
import com.mdevstudio.core.economy.Money;
import com.mdevstudio.core.effect.EffectLibrary;
import com.mdevstudio.core.effect.HarmlessFireworks;
import com.mdevstudio.core.hook.VaultMoney;
import com.mdevstudio.core.reward.BuiltinRewards;
import com.mdevstudio.core.reward.RewardRegistry;
import com.mdevstudio.core.scenario.ActionRegistry;
import com.mdevstudio.core.scenario.BuiltinActions;
import com.mdevstudio.core.stats.PlayerStats;
import com.mdevstudio.core.storage.Database;
import com.mdevstudio.core.text.Text;
import com.mdevstudio.core.util.Durations;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

public final class MDevCorePlugin extends JavaPlugin {

    private static final String ADMIN_PERMISSION = "mdevcore.admin";
    private static final String COMMAND = "mdevcore";

    private final RewardRegistry rewards = new RewardRegistry();
    private final ActionRegistry actions = new ActionRegistry();
    private Text text;
    private EffectLibrary effects;
    private Database database;
    private PlayerStats stats;
    private boolean moneyMissingReported;

    @Override
    public void onEnable() {
        YamlConfiguration config = loadFiles();
        BuiltinRewards.registerAll(rewards, text);
        BuiltinActions.registerAll(actions, text);
        rewards.register("effect", id -> player -> effects.play(id, player.getLocation()));
        rewards.register("money", value -> {
            double amount;
            try {
                amount = Double.parseDouble(value);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("money: '" + value + "' is not a number");
            }
            return player -> money().ifPresentOrElse(money -> money.deposit(player, amount), this::reportNoMoney);
        });
        openStorage(config);
        MDevCore.attach(this);

        getServer().getPluginManager().registerEvents(new HarmlessFireworks(this), this);
        List<String> aliases = config.getStringList("command-aliases").stream()
                .map(alias -> alias.trim().toLowerCase(Locale.ROOT))
                .filter(alias -> alias.matches("[a-z0-9_-]+") && !alias.equals(COMMAND))
                .toList();
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(command(), "M/Dev Studio core", aliases));
        getSLF4JLogger().info("Loaded {} effects", effects.ids().size());
    }

    @Override
    public void onDisable() {
        if (database != null) {
            database.close();
        }
    }

    public Text text() {
        return text;
    }

    public RewardRegistry rewards() {
        return rewards;
    }

    public ActionRegistry actions() {
        return actions;
    }

    public EffectLibrary effects() {
        return effects;
    }

    PlayerStats stats() {
        return stats;
    }

    Optional<Money> money() {
        if (!getServer().getPluginManager().isPluginEnabled("Vault")) {
            return Optional.empty();
        }
        return VaultMoney.find(getServer());
    }

    private YamlConfiguration loadFiles() {
        YamlConfiguration config = YamlFile.load(this, "config.yml");
        text = Text.fromConfig(config.getConfigurationSection("palette"));
        effects = EffectLibrary.load(this, config.getConfigurationSection("effects"));
        return config;
    }

    // Waits for the tables at startup so a wrong password shows up right away in the console,
    // not as errors scattered over the first hours of play.
    private void openStorage(YamlConfiguration config) {
        try {
            database = Database.fromConfig(this, config.getConfigurationSection("storage"));
            PlayerStats created = new PlayerStats(this, database);
            created.createTables().get(15, TimeUnit.SECONDS);
            long refresh = Durations.toTicks(Durations.parse(config.getString("stats.top-refresh", "5m")));
            created.startCaching(Math.max(20 * 30, refresh));
            stats = created;
        } catch (IllegalArgumentException | IllegalStateException e) {
            disableStorage("config.yml: " + e.getMessage());
        } catch (ExecutionException e) {
            disableStorage(e.getCause().getMessage());
        } catch (TimeoutException e) {
            disableStorage("the database did not answer in 15 seconds");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            disableStorage("interrupted");
        }
    }

    private void disableStorage(String reason) {
        getSLF4JLogger().error("Player statistics are off: {}", reason);
        if (database != null) {
            database.close();
            database = null;
        }
    }

    private void reportNoMoney() {
        if (!moneyMissingReported) {
            moneyMissingReported = true;
            getSLF4JLogger().warn("A 'money' reward was given, but Vault or an economy plugin is not installed");
        }
    }

    private LiteralCommandNode<CommandSourceStack> command() {
        return Commands.literal(COMMAND)
                .requires(source -> source.getSender().hasPermission(ADMIN_PERMISSION))
                .executes(context -> {
                    reply(context.getSource().getSender(), "<accent>MDevCore</accent> <gray><value>",
                            getPluginMeta().getVersion());
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("reload").executes(context -> {
                    loadFiles();
                    reply(context.getSource().getSender(),
                            "<success>MDevCore reloaded.</success> <gray>Effects: <white><value>",
                            Integer.toString(effects.ids().size()));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("effects").executes(context -> {
                    reply(context.getSource().getSender(), "<gray>Effects: <white><value>",
                            String.join(", ", effects.ids()));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("effect")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    String typed = builder.getRemainingLowerCase();
                                    effects.ids().stream().filter(id -> id.startsWith(typed)).forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    CommandSender sender = context.getSource().getSender();
                                    Entity executor = context.getSource().getExecutor();
                                    String id = StringArgumentType.getString(context, "id").toLowerCase(Locale.ROOT);
                                    if (executor == null) {
                                        reply(sender, "<error>Run this in game: the effect plays where you stand.", "");
                                    } else if (!effects.has(id)) {
                                        reply(sender, "<error>No effect <value> in MDevCore/effects.", id);
                                    } else {
                                        effects.play(id, executor.getLocation());
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })))
                .build();
    }

    private void reply(CommandSender sender, String message, String value) {
        sender.sendMessage(text.parse(message, Placeholder.unparsed("value", value)));
    }
}
