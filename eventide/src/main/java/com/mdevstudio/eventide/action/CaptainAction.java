package com.mdevstudio.eventide.action;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mdevstudio.core.reward.BuiltinRewards;
import com.mdevstudio.core.scenario.ActionFactory;
import com.mdevstudio.core.scenario.ScenarioAction;
import com.mdevstudio.eventide.EventidePlugin;
import com.mdevstudio.eventide.EventideSettings;
import com.mdevstudio.eventide.loot.Rarity;
import com.mdevstudio.eventide.hook.FancyNpcs;
import com.mdevstudio.eventide.running.ActiveEvent;
import com.mdevstudio.eventide.running.EntityRole;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * A captain NPC who gives a key for a small task: kill some mobs near the event, bring items, or
 * just talk. Progress is kept per player; {@code per-player} caps the keys one player can get.
 */
final class CaptainAction implements ActionFactory {

    private enum Kind { NONE, KILL, BRING }

    private final EventidePlugin plugin;

    CaptainAction(EventidePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public ScenarioAction create(ConfigurationSection settings) {
        MobSpec mob = MobSpec.parse(settings, MobKind.parse(plugin, settings.getString("entity", "villager")),
                NamedTextColor.AQUA);
        String rarityId = settings.getString("rarity", "").trim();
        if (rarityId.isEmpty()) {
            throw new IllegalArgumentException("captain: 'rarity' is required, the key the captain gives");
        }
        int spread = Math.max(1, settings.getInt("spread", 4));
        int perPlayer = Math.max(1, settings.getInt("per-player", 1));
        int points = settings.getInt("points", 3);
        List<String> dialog = settings.getStringList("dialog");
        String spawnEffect = settings.getString("effects.spawn");
        String rewardEffect = settings.getString("effects.reward");
        Task task = Task.parse(settings.getConfigurationSection("task"));
        boolean playerNpc = settings.isConfigurationSection("npc") && fancyNpcsReady();
        String skin = settings.getString("npc.skin");
        if (settings.isConfigurationSection("npc") && !playerNpc) {
            plugin.getSLF4JLogger().warn("captain: 'npc' needs the FancyNpcs plugin and modules.fancy-npcs; "
                    + "the captain is shown as {} instead", mob.kind());
        }

        return EventActions.forEvent(event -> {
            EventideSettings.Keys keys = plugin.settings().modules().keys();
            if (!keys.enabled() || !keys.captain()) {
                return;
            }
            Location spot = Spots.randomSurface(event.location(), spread);
            if (spot == null) {
                spot = event.location().clone();
            }
            Captain captain = new Captain(event, task, rarityId, perPlayer, points, dialog, rewardEffect,
                    settings.getInt("radius", event.reach() + 16));
            if (playerNpc) {
                // Faces the event, so players walking up see the captain's face, not his back.
                Vector toEvent = event.location().toVector().subtract(spot.toVector()).setY(0);
                if (toEvent.lengthSquared() > 0) {
                    spot.setDirection(toEvent);
                }
                Runnable remove = FancyNpcs.spawn(plugin, spot, mob.name() == null ? "Captain" : mob.name(), skin,
                        plugin.settings().modules().mobGlow() ? mob.glow() : null, captain::interacted);
                event.addPart(remove::run);
            } else {
                LivingEntity npc = mob.spawn(event, spot, plugin.text());
                npc.setAI(false);
                npc.setSilent(true);
                npc.setInvulnerable(true);
                npc.setCollidable(false);
                event.claim(npc, captain);
            }
            plugin.playEffect(spawnEffect, spot);
            if (task.kind() == Kind.KILL) {
                Bukkit.getPluginManager().registerEvents(captain, plugin);
                event.addPart(() -> HandlerList.unregisterAll(captain));
            }
        });
    }

    private boolean fancyNpcsReady() {
        return plugin.settings().modules().fancyNpcs()
                && plugin.getServer().getPluginManager().isPluginEnabled(FancyNpcs.PLUGIN);
    }

    private record Task(Kind kind, int count, Set<EntityType> victims, Material item) {

        static Task parse(ConfigurationSection section) {
            if (section == null) {
                return new Task(Kind.NONE, 0, Set.of(), null);
            }
            Kind kind = EventActions.enumValue(Kind.class, section.getString("type", "none"));
            int count = Math.max(1, section.getInt("count", 5));
            Set<EntityType> victims = EnumSet.noneOf(EntityType.class);
            section.getStringList("entities").forEach(name -> victims.add(MobSpec.entityType(name)));
            Material item = null;
            if (kind == Kind.BRING) {
                item = Material.matchMaterial(section.getString("item", ""));
                if (item == null || !item.isItem()) {
                    throw new IllegalArgumentException("captain: task.item '" + section.getString("item", "")
                            + "' is not an item");
                }
            }
            return new Task(kind, count, victims, item);
        }

        boolean accepts(LivingEntity dead) {
            return victims.isEmpty() ? dead instanceof Monster : victims.contains(dead.getType());
        }
    }

    private static final class Progress {
        boolean accepted;
        int kills;
        int rewards;
    }

    private final class Captain implements EntityRole, Listener {

        private final ActiveEvent event;
        private final Task task;
        private final String rarityId;
        private final int perPlayer;
        private final int points;
        private final List<String> dialog;
        private final String rewardEffect;
        private final long radiusSquared;
        private final Map<UUID, Progress> progress = new HashMap<>();

        Captain(ActiveEvent event, Task task, String rarityId, int perPlayer, int points, List<String> dialog,
                String rewardEffect, int radius) {
            this.event = event;
            this.task = task;
            this.rarityId = rarityId;
            this.perPlayer = perPlayer;
            this.points = points;
            this.dialog = dialog;
            this.rewardEffect = rewardEffect;
            this.radiusSquared = (long) radius * radius;
        }

        @Override
        public boolean interactive() {
            return true;
        }

        @Override
        public void interacted(Player player) {
            Progress mine = progress.computeIfAbsent(player.getUniqueId(), id -> new Progress());
            if (mine.rewards >= perPlayer) {
                plugin.messages().send(player, "captain.already");
                return;
            }
            if (!mine.accepted) {
                mine.accepted = true;
                dialog.forEach(line -> player.sendMessage(plugin.text().parse(line, event.placeholders())));
                if (task.kind() != Kind.NONE) {
                    plugin.messages().send(player, "captain.task-" + task.kind().name().toLowerCase(Locale.ROOT), resolvers());
                }
            }
            if (finished(player, mine)) {
                reward(player, mine);
            } else if (task.kind() == Kind.KILL) {
                plugin.messages().send(player, "captain.progress-kill", resolvers(
                        Placeholder.unparsed("done", Integer.toString(mine.kills))));
            } else {
                plugin.messages().send(player, "captain.progress-bring", resolvers(
                        Placeholder.unparsed("done", Integer.toString(carried(player)))));
            }
        }

        @Override
        public void died(EntityDeathEvent death, Player killer) {
            event.release(death.getEntity());
            death.getDrops().clear();
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onKill(EntityDeathEvent death) {
            LivingEntity dead = death.getEntity();
            Player killer = dead.getKiller();
            if (killer == null || dead instanceof Player || !task.accepts(dead)) {
                return;
            }
            Progress mine = progress.get(killer.getUniqueId());
            if (mine == null || !mine.accepted || mine.kills >= task.count()) {
                return;
            }
            Location at = dead.getLocation();
            if (!at.getWorld().equals(event.location().getWorld())
                    || at.distanceSquared(event.location()) > radiusSquared) {
                return;
            }
            mine.kills++;
            if (mine.kills == task.count()) {
                plugin.messages().send(killer, "captain.kill-done");
            }
        }

        private boolean finished(Player player, Progress mine) {
            return switch (task.kind()) {
                case NONE -> true;
                case KILL -> mine.kills >= task.count();
                case BRING -> carried(player) >= task.count();
            };
        }

        private int carried(Player player) {
            int total = 0;
            for (ItemStack stack : player.getInventory().getContents()) {
                if (stack != null && stack.getType() == task.item()) {
                    total += stack.getAmount();
                }
            }
            return total;
        }

        private void reward(Player player, Progress mine) {
            EventideSettings.Keys keys = plugin.settings().modules().keys();
            Rarity rarity = plugin.rarity(rarityId).filter(Rarity::locked).orElse(null);
            if (!keys.enabled() || !keys.captain() || rarity == null) {
                return;
            }
            if (task.kind() == Kind.BRING) {
                player.getInventory().removeItem(new ItemStack(task.item(), task.count()));
            }
            BuiltinRewards.give(player, plugin.keys().create(rarity, 1));
            event.score().add(player, points);
            plugin.playEffect(rewardEffect, player.getLocation());
            plugin.messages().send(player, "captain.rewarded", Placeholder.parsed("rarity", rarity.displayName()));
            mine.rewards++;
            mine.accepted = false;
            mine.kills = 0;
        }

        private TagResolver resolvers(TagResolver... extra) {
            Component item = task.item() == null ? Component.empty() : Component.translatable(task.item());
            return TagResolver.resolver(
                    TagResolver.resolver(extra),
                    Placeholder.unparsed("count", Integer.toString(task.count())),
                    Placeholder.component("item", item));
        }
    }
}
