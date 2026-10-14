package com.mdevstudio.eventide.action;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.mdevstudio.core.scenario.ActionFactory;
import com.mdevstudio.core.scenario.ScenarioAction;
import com.mdevstudio.core.util.Durations;
import com.mdevstudio.eventide.EventidePlugin;
import com.mdevstudio.eventide.running.ActiveEvent;
import com.mdevstudio.eventide.running.BlockRole;
import com.mdevstudio.eventide.running.EntityRole;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Blocks scattered around the spot that players pick up with a click or a punch for points, e.g. pumpkins.
 * New ones keep appearing while the action runs. A share of them can be cursed: worth more, but mobs jump out.
 * Sends {@code collect-won} when someone reaches {@code goal}.
 */
final class CollectAction implements ActionFactory {

    static final String SIGNAL = "collect-won";
    private static final String SLOT = "collect";
    private static final List<BlockFace> FACINGS = List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH,
            BlockFace.WEST);

    private final EventidePlugin plugin;

    private record Curse(double chance, Material block, int points, Map<MobKind, Integer> mobs, MobSpec spec,
            int pointsPerKill, String effect) {
    }

    private record Settings(
            Material block,
            int points,
            int count,
            int max,
            long respawnTicks,
            int respawnAmount,
            int spread,
            int goal,
            Curse curse,
            BarStyle bar,
            String idleEffect,
            long idleTicks,
            String pickEffect) {
    }

    CollectAction(EventidePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public ScenarioAction create(ConfigurationSection section) {
        int count = Math.max(1, section.getInt("count", 10));
        Settings settings = new Settings(
                block(section.getString("block", "carved_pumpkin"), "block"),
                section.getInt("points", 1),
                count,
                Math.max(count, section.getInt("max", count)),
                Math.max(20, Durations.toTicks(Durations.parse(section.getString("respawn.every", "10s")))),
                Math.max(0, section.getInt("respawn.amount", 1)),
                Math.max(3, section.getInt("spread", 16)),
                Math.max(0, section.getInt("goal", 0)),
                curse(section.getConfigurationSection("cursed")),
                BarStyle.parse(section, "yellow", 96),
                section.getString("effects.idle"),
                Math.max(10, Durations.toTicks(Durations.parse(section.getString("effects.idle-every", "3s")))),
                section.getString("effects.pick"));
        return EventActions.forEvent(event -> new Field(event, settings).start());
    }

    private Curse curse(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String chance = section.getString("chance", "20%").replace("%", "").trim();
        double share;
        try {
            share = Double.parseDouble(chance) / 100;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("cursed.chance: '" + section.getString("chance") + "' is not a percent");
        }
        Map<MobKind, Integer> mobs = new LinkedHashMap<>();
        ConfigurationSection mobSection = section.getConfigurationSection("mobs");
        if (mobSection != null) {
            for (String type : mobSection.getKeys(false)) {
                mobs.put(MobKind.parse(plugin, type), Math.max(0, mobSection.getInt(type)));
            }
        }
        return new Curse(
                Math.clamp(share, 0, 1),
                block(section.getString("block", "jack_o_lantern"), "cursed.block"),
                section.getInt("points", 3),
                mobs,
                MobSpec.parse(section, MobKind.vanilla(EntityType.ZOMBIE),
                        NamedTextColor.DARK_PURPLE),
                section.getInt("points-per-kill", 1),
                section.getString("effect"));
    }

    private static Material block(String name, String field) {
        Material material = Material.matchMaterial(name);
        if (material == null || !material.isBlock() || material.isAir()) {
            throw new IllegalArgumentException(field + ": '" + name + "' is not a block");
        }
        return material;
    }

    private final class Field implements EntityRole {

        private final ActiveEvent event;
        private final Settings settings;
        // Each block remembers whether it is cursed.
        private final Map<Block, Boolean> placed = new HashMap<>();
        private BossBar bar;
        private boolean won;

        Field(ActiveEvent event, Settings settings) {
            this.event = event;
            this.settings = settings;
        }

        void start() {
            for (int i = 0; i < settings.count(); i++) {
                placeOne();
            }
            bar = settings.bar().create(title(), 0);
            event.bars().show(SLOT, bar, settings.bar().radius());
            if (settings.respawnAmount() > 0) {
                event.every(settings.respawnTicks(), () -> {
                    for (int i = 0; i < settings.respawnAmount() && placed.size() < settings.max(); i++) {
                        placeOne();
                    }
                    updateBar();
                });
            }
            if (settings.idleEffect() != null) {
                event.every(settings.idleTicks(), () -> placed.keySet().forEach(block ->
                        plugin.playEffect(settings.idleEffect(), block.getLocation().add(0.5, 0, 0.5))));
            }
        }

        private void placeOne() {
            Block block = Spots.freeBlock(event.location(), settings.spread(), placed.keySet());
            if (block == null) {
                return;
            }
            Curse curse = settings.curse();
            boolean cursed = curse != null && ThreadLocalRandom.current().nextDouble() < curse.chance();
            block.setType(cursed ? curse.block() : settings.block());
            if (block.getBlockData() instanceof Directional directional) {
                directional.setFacing(FACINGS.get(ThreadLocalRandom.current().nextInt(FACINGS.size())));
                block.setBlockData(directional);
            }
            placed.put(block, cursed);
            event.claim(block, new BlockRole() {
                @Override
                public void use(Player player) {
                    pick(player, block);
                }

                @Override
                public boolean usedByPunch() {
                    return true;
                }
            });
        }

        private void pick(Player player, Block block) {
            Boolean cursed = placed.remove(block);
            if (cursed == null || won) {
                return;
            }
            event.release(block);
            Location center = block.getLocation().add(0.5, 0.5, 0.5);
            block.setType(Material.AIR);
            plugin.playEffect(settings.pickEffect(), center);
            int points = cursed ? settings.curse().points() : settings.points();
            event.score().add(player, points);
            player.sendActionBar(plugin.messages().get(player, cursed ? "collect.picked-cursed" : "collect.picked",
                    Placeholder.unparsed("points", Integer.toString(points)),
                    Placeholder.unparsed("total", Integer.toString(event.score().of(player.getUniqueId())))));
            if (cursed) {
                releaseCurse(center);
            }
            updateBar();
            checkGoal();
        }

        private void releaseCurse(Location at) {
            Curse curse = settings.curse();
            plugin.playEffect(curse.effect(), at);
            curse.mobs().forEach((kind, amount) -> {
                for (int i = 0; i < amount; i++) {
                    LivingEntity mob = curse.spec().withKind(kind).spawn(event, at, plugin.text());
                    event.claim(mob, this);
                }
            });
        }

        @Override
        public void died(EntityDeathEvent death, Player killer) {
            event.release(death.getEntity());
            death.getDrops().clear();
            death.setDroppedExp(0);
            if (killer != null && !won) {
                event.score().add(killer, settings.curse().pointsPerKill());
                updateBar();
                checkGoal();
            }
        }

        private void checkGoal() {
            Optional<Map.Entry<UUID, Integer>> leader = event.score().leader();
            if (won || settings.goal() == 0 || leader.isEmpty() || leader.get().getValue() < settings.goal()) {
                return;
            }
            won = true;
            plugin.messages().broadcast("collect.won", event.placeholders(),
                    Placeholder.unparsed("player", nameOf(leader.get().getKey())));
            event.signal(SIGNAL);
        }

        private void updateBar() {
            bar.name(title());
            int best = event.score().leader().map(Map.Entry::getValue).orElse(0);
            bar.progress(settings.goal() > 0 ? Math.clamp(best / (float) settings.goal(), 0f, 1f) : 1f);
        }

        private Component title() {
            Optional<Map.Entry<UUID, Integer>> leader = event.score().leader();
            return plugin.messages().get(Bukkit.getConsoleSender(), "collect.bar", TagResolver.resolver(
                    event.placeholders(),
                    Placeholder.unparsed("leader", leader.map(entry -> nameOf(entry.getKey())).orElse("—")),
                    Placeholder.unparsed("points", Integer.toString(leader.map(Map.Entry::getValue).orElse(0))),
                    Placeholder.unparsed("goal", settings.goal() > 0 ? Integer.toString(settings.goal()) : "∞"),
                    Placeholder.unparsed("left", Integer.toString(placed.size()))));
        }

        private String nameOf(UUID id) {
            return Optional.ofNullable(Bukkit.getOfflinePlayer(id).getName()).orElse("?");
        }
    }
}
