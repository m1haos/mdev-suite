package com.mdevstudio.eventide.listener;

import java.util.List;

import com.mdevstudio.eventide.EventidePlugin;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Routes clicks, damage and deaths to whatever part of a running event owns the block or mob. */
public final class EventListener implements Listener {

    private final EventidePlugin plugin;

    public EventListener(EventidePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null) {
            return;
        }
        plugin.manager().roleOf(block).ifPresent(role -> {
            // Cancel for both hands so the vanilla container screen never opens; act on the main hand only.
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND) {
                role.use(event.getPlayer());
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (plugin.manager().isProtected(event.getBlock())) {
            event.setCancelled(true);
            plugin.messages().send(event.getPlayer(), "container.protected");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (plugin.manager().isProtected(event.getBlockPlaced())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(plugin.manager()::isProtected);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(plugin.manager()::isProtected);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (movesProtected(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (movesProtected(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    // MONITOR: only damage that actually went through counts toward the boss's damage split.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player attacker = attackerOf(event.getDamager());
        if (attacker != null) {
            plugin.manager().roleOf(event.getEntity()).ifPresent(role -> role.damaged(attacker, event.getFinalDamage()));
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        plugin.manager().roleOf(event.getEntity()).ifPresent(role -> role.died(event, event.getEntity().getKiller()));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.manager().playerJoined(event.getPlayer());
    }

    private boolean movesProtected(List<Block> blocks) {
        return blocks.stream().anyMatch(plugin.manager()::isProtected);
    }

    static Player attackerOf(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }
}
