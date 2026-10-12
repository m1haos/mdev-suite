package com.mdevstudio.eventide.action;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import com.mdevstudio.core.scenario.ActionFactory;
import com.mdevstudio.core.scenario.ScenarioAction;
import com.mdevstudio.core.util.Durations;
import com.mdevstudio.eventide.EventidePlugin;
import com.mdevstudio.eventide.loot.LootTier;
import com.mdevstudio.eventide.running.ActiveEvent;
import com.mdevstudio.eventide.running.EventStats;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * A crate falls from the sky onto the event spot. It opens for the player who stays next to it
 * alone long enough; anyone else stepping in stops the progress. Sends {@code airdrop-captured}.
 */
final class AirdropAction implements ActionFactory {

    static final String SIGNAL = "airdrop-captured";
    private static final String SLOT = "airdrop";
    private static final int CHECK_TICKS = 10;
    private static final int FALL_STEP_TICKS = 2;
    private static final int TRAIL_EVERY_TICKS = 4;

    private final EventidePlugin plugin;

    private enum Contest { PAUSE, RESET }

    private record Settings(
            String tier,
            double height,
            long fallTicks,
            double radius,
            long captureTicks,
            Contest contest,
            BarStyle bar,
            String trailEffect,
            String landEffect) {
    }

    AirdropAction(EventidePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public ScenarioAction create(ConfigurationSection section) {
        String tier = section.getString("tier");
        if (tier == null) {
            throw new IllegalArgumentException("airdrop: 'tier' is required, the loot tier inside the crate");
        }
        Duration fall = Durations.parse(section.getString("fall-time", "6s"));
        Duration capture = Durations.parse(section.getString("capture.time", "30s"));
        Settings settings = new Settings(
                tier,
                Math.max(10, section.getDouble("height", 60)),
                Math.max(FALL_STEP_TICKS, Durations.toTicks(fall)),
                Math.max(1, section.getDouble("capture.radius", 4)),
                Math.max(CHECK_TICKS, Durations.toTicks(capture)),
                EventActions.enumValue(Contest.class, section.getString("capture.contested", "pause")),
                BarStyle.parse(section, "blue", 64),
                section.getString("effects.trail"),
                section.getString("effects.land"));

        return EventActions.forEvent(event -> {
            LootTier loot = event.lootTable().tier(settings.tier()).orElseThrow(() -> new IllegalStateException(
                    "Loot table '" + event.lootTable().id() + "' has no tier '" + settings.tier() + "'"));
            new Crate(event, loot, settings).drop();
        });
    }

    private final class Crate {

        private final ActiveEvent event;
        private final LootTier loot;
        private final Settings settings;
        private final Block block;
        private final Location center;
        private BossBar bar;
        private UUID capturer;
        private long progress;
        private boolean opened;

        Crate(ActiveEvent event, LootTier loot, Settings settings) {
            this.event = event;
            this.loot = loot;
            this.settings = settings;
            Block target = event.location().getBlock();
            // Grass and snow layers are replaced, so the crate sits on the ground instead of above them.
            while (!target.isReplaceable() && target.getY() < target.getWorld().getMaxHeight() - 1) {
                target = target.getRelative(0, 1, 0);
            }
            this.block = target;
            this.center = block.getLocation().add(0.5, 0.5, 0.5);
        }

        void drop() {
            // Claimed before the fall, so nobody can build on the spot while the crate is in the air.
            event.claim(block, player -> plugin.messages().send(player, "airdrop.incoming"));
            Location start = block.getLocation().add(0, settings.height(), 0);
            BlockDisplay display = start.getWorld().spawn(start, BlockDisplay.class, spawned -> {
                spawned.setBlock(loot.block().createBlockData());
                spawned.setPersistent(false);
                spawned.setTeleportDuration(FALL_STEP_TICKS);
            });
            event.addPart(display::remove);
            Fall fall = new Fall(display, start);
            fall.task = event.every(FALL_STEP_TICKS, fall);
        }

        // The display moves in short teleports that the client smooths out, so the crate glides down.
        private final class Fall implements Runnable {

            private final BlockDisplay display;
            private final Location start;
            private final long steps = Math.max(1, settings.fallTicks() / FALL_STEP_TICKS);
            private long done;
            private BukkitTask task;

            Fall(BlockDisplay display, Location start) {
                this.display = display;
                this.start = start;
            }

            @Override
            public void run() {
                done++;
                display.teleport(start.clone().subtract(0, settings.height() * done / steps, 0));
                if (done * FALL_STEP_TICKS % TRAIL_EVERY_TICKS == 0) {
                    plugin.playEffect(settings.trailEffect(), display.getLocation().add(0.5, 0.5, 0.5));
                }
                if (done >= steps) {
                    task.cancel();
                    display.remove();
                    land();
                }
            }
        }

        private void land() {
            block.setType(loot.block());
            event.claim(block, player -> plugin.messages().send(player, "airdrop.hint",
                    Placeholder.unparsed("seconds", Long.toString(settings.captureTicks() / 20))));
            plugin.playEffect(settings.landEffect(), center);
            plugin.messages().broadcast("airdrop.landed", event.placeholders());
            bar = settings.bar().create(barTitle("airdrop.bar-waiting"), 0);
            event.bars().show(SLOT, bar, settings.bar().radius());
            event.every(CHECK_TICKS, this::check);
        }

        private void check() {
            if (opened) {
                return;
            }
            List<Player> near = Spots.playersIn(center, settings.radius(), settings.radius());
            if (near.isEmpty()) {
                capturer = null;
                progress = 0;
                show("airdrop.bar-waiting", null);
                return;
            }
            if (near.size() > 1) {
                if (settings.contest() == Contest.RESET) {
                    progress = 0;
                }
                show("airdrop.bar-contested", null);
                return;
            }
            Player player = near.getFirst();
            if (!player.getUniqueId().equals(capturer)) {
                capturer = player.getUniqueId();
                progress = 0;
            }
            progress += CHECK_TICKS;
            if (progress >= settings.captureTicks()) {
                open(player);
            } else {
                show("airdrop.bar-capturing", player);
            }
        }

        private void show(String key, Player player) {
            bar.progress(Math.clamp(progress / (float) settings.captureTicks(), 0f, 1f));
            bar.name(barTitle(key, Placeholder.unparsed("player", player == null ? "" : player.getName()),
                    Placeholder.unparsed("percent",
                            Long.toString(Math.round(100.0 * progress / settings.captureTicks())))));
        }

        private void open(Player player) {
            opened = true;
            event.release(block);
            block.setType(Material.AIR);
            event.bars().hide(SLOT, bar);
            plugin.playEffect(loot.openEffect(), center);
            loot.dropLoot(center, player, plugin.text());
            event.score().add(player, loot.points());
            EventStats.add(plugin, player.getUniqueId(), EventStats.AIRDROPS, 1);
            plugin.messages().broadcast("airdrop.captured", event.placeholders(),
                    Placeholder.unparsed("player", player.getName()));
            event.signal(SIGNAL);
        }

        private Component barTitle(String key, TagResolver... extra) {
            return plugin.messages().get(Bukkit.getConsoleSender(), key,
                    TagResolver.resolver(event.placeholders(), TagResolver.resolver(extra)));
        }
    }
}
