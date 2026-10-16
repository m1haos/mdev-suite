package com.mdevstudio.eventide.listener;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.mdevstudio.eventide.EventidePlugin;
import com.mdevstudio.eventide.definition.RegionFlag;
import com.mdevstudio.eventide.running.ActiveEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.InventoryHolder;

/**
 * Applies event regions: what the event file forbids around a running event, plus the build, which
 * forbids every change to itself because it is rolled back when the event ends.
 */
public final class RegionListener implements Listener {

    public static final String BYPASS_PERMISSION = "eventide.region.bypass";

    // Leaves decay up to 6 blocks from a log, and trees cut by a build have no logs until it is gone.
    private static final int LEAF_MARGIN = 7;
    // Event mobs are spawned by the plugin and come with CUSTOM, so they are never caught here.
    private static final Set<SpawnReason> NATURAL_SPAWNS = EnumSet.of(SpawnReason.NATURAL, SpawnReason.PATROL,
            SpawnReason.REINFORCEMENTS, SpawnReason.SPAWNER, SpawnReason.TRIAL_SPAWNER);

    private static final Set<Material> SWITCHES = EnumSet.of(Material.LEVER, Material.REPEATER, Material.COMPARATOR,
            Material.NOTE_BLOCK, Material.DAYLIGHT_DETECTOR, Material.BELL, Material.RESPAWN_ANCHOR);

    private final EventidePlugin plugin;

    public RegionListener(EventidePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        refuse(event, event.getPlayer(), event.getBlock().getLocation(), RegionFlag.BREAK);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        refuse(event, event.getPlayer(), event.getBlockPlaced().getLocation(), RegionFlag.PLACE);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        refuse(event, event.getPlayer(), event.getBlock().getLocation(), RegionFlag.BUCKETS);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        refuse(event, event.getPlayer(), event.getBlock().getLocation(), RegionFlag.BUCKETS);
    }

    // HIGHEST: EventListener has already taken clicks on caches and crates and cancelled them.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        if (event.getAction() == Action.PHYSICAL) {
            refuse(event, event.getPlayer(), block.getLocation(), RegionFlag.USE);
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (block.getState(false) instanceof InventoryHolder) {
                refuse(event, event.getPlayer(), block.getLocation(), RegionFlag.CONTAINERS);
            } else if (usable(block.getType())) {
                refuse(event, event.getPlayer(), block.getLocation(), RegionFlag.USE);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (event.getPlayer() != null) {
            refuse(event, event.getPlayer(), event.getEntity().getLocation(), RegionFlag.PLACE);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        Location at = event.getEntity().getLocation();
        if (event instanceof HangingBreakByEntityEvent byEntity
                && EventListener.attackerOf(byEntity.getRemover()) instanceof Player player) {
            refuse(event, player, at, RegionFlag.BREAK);
        } else if (event.getCause() == HangingBreakEvent.RemoveCause.EXPLOSION && forbids(at, RegionFlag.EXPLOSIONS)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> forbids(block.getLocation(), RegionFlag.EXPLOSIONS));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> forbids(block.getLocation(), RegionFlag.EXPLOSIONS));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (pistonForbidden(event.getBlock(), event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (pistonForbidden(event.getBlock(), event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (forbids(event.getToBlock().getLocation(), RegionFlag.FLUIDS)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (forbids(event.getBlock().getLocation(), RegionFlag.FIRE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (forbids(event.getBlock().getLocation(), RegionFlag.FIRE)) {
            event.setCancelled(true);
        }
    }

    // Endermen, ravagers, withers and falling blocks. Players trampling farmland count as "use".
    @EventHandler(ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof Player)
                && forbids(event.getBlock().getLocation(), RegionFlag.MOB_GRIEFING)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        if (plugin.manager().inBuild(event.getBlock().getLocation(), LEAF_MARGIN)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (NATURAL_SPAWNS.contains(event.getSpawnReason())
                && forbids(event.getLocation(), RegionFlag.MOB_SPAWNING)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim
                && EventListener.attackerOf(event.getDamager()) instanceof Player attacker
                && !attacker.equals(victim)) {
            refuse(event, attacker, victim.getLocation(), RegionFlag.PVP);
            if (!event.isCancelled() && !attacker.hasPermission(BYPASS_PERMISSION)) {
                plugin.manager().pvpAfterEnd(victim.getLocation()).ifPresent(protection -> {
                    event.setCancelled(true);
                    plugin.messages().send(attacker, "region.pvp-after-end", protection.placeholders(),
                            Placeholder.unparsed("seconds", Long.toString(protection.secondsLeft())));
                });
            }
        }
    }

    // Void and /kill stay deadly, or a player stuck in the region would have no way out.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        DamageCause cause = event.getCause();
        if (event.getEntity() instanceof Player && cause != DamageCause.VOID && cause != DamageCause.KILL
                && cause != DamageCause.SUICIDE && forbids(event.getEntity().getLocation(), RegionFlag.DAMAGE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        if (plugin.manager().keepsInventory(event.getPlayer().getLocation())) {
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        TeleportCause cause = event.getCause();
        if (cause == TeleportCause.ENDER_PEARL || cause == TeleportCause.CONSUMABLE_EFFECT) {
            refuse(event, event.getPlayer(), event.getTo(), RegionFlag.ENDER_PEARLS);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission(BYPASS_PERMISSION)) {
            return;
        }
        String label = event.getMessage().substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        // "essentials:sethome" is the same command as "sethome".
        String command = label.substring(label.indexOf(':') + 1);
        plugin.manager().blockingCommand(player.getLocation(), command).ifPresent(owner -> {
            event.setCancelled(true);
            plugin.messages().send(player, "region.command-blocked", owner.placeholders());
        });
    }

    private void refuse(Cancellable event, Player player, Location at, RegionFlag flag) {
        if (player.hasPermission(BYPASS_PERMISSION)) {
            return;
        }
        Optional<ActiveEvent> owner = plugin.manager().forbidding(at, flag);
        if (owner.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        boolean build = owner.get().inBuild(at, 0) && RegionFlag.BUILD.contains(flag);
        String key = build ? "build.protected" : "region.denied";
        plugin.messages().send(player, key, owner.get().placeholders());
    }

    // Blocks that switch something or let players through; what "use" in event-region covers.
    private static boolean usable(Material type) {
        return Tag.DOORS.isTagged(type) || Tag.TRAPDOORS.isTagged(type) || Tag.FENCE_GATES.isTagged(type)
                || Tag.BUTTONS.isTagged(type) || Tag.BEDS.isTagged(type) || SWITCHES.contains(type);
    }

    private boolean forbids(Location at, RegionFlag flag) {
        return plugin.manager().forbidding(at, flag).isPresent();
    }

    private boolean pistonForbidden(Block piston, List<Block> moved, BlockFace direction) {
        return forbids(piston.getLocation(), RegionFlag.PISTONS) || moved.stream().anyMatch(block ->
                forbids(block.getLocation(), RegionFlag.PISTONS)
                        || forbids(block.getRelative(direction).getLocation(), RegionFlag.PISTONS));
    }
}
