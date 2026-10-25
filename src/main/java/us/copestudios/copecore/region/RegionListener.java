package us.copestudios.copecore.region;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.EquipmentSlot;
import us.copestudios.copecore.CopeCore;
import us.copestudios.copecore.event.RegionEnterEvent;
import us.copestudios.copecore.event.RegionLeaveEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Handles the selection wand clicks and fires {@link RegionEnterEvent} / {@link RegionLeaveEvent}.
 *
 * <p>Doing the movement tracking ONCE here means plugins like CSNumbers and CSKoth no longer each run their own
 * "check every region on every step of every player" listener.</p>
 */
public final class RegionListener implements Listener {

    private final CopeCore core;
    private final RegionService regions;
    /** player -> ids ("plugin:region") of the regions they are currently in */
    private final Map<UUID, Set<String>> inside = new HashMap<>();

    public RegionListener(CopeCore core, RegionService regions) {
        this.core = core;
        this.regions = regions;
    }

    // =====================================================================
    //  Wand
    // =====================================================================

    @EventHandler(priority = EventPriority.HIGH)
    public void onWandClick(PlayerInteractEvent event) {
        // Only the main hand, otherwise the event fires twice (once per hand).
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) {
            return;
        }
        if (!regions.isWand(event.getItem())) {
            return;
        }
        event.setCancelled(true); // don't break or use the block
        Player player = event.getPlayer();
        if (!player.hasPermission("copecore.wand")) {
            core.coreMessages().send(player, "generic.no-permission");
            return;
        }
        Location block = event.getClickedBlock().getLocation();
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            regions.setCorner(player, 1, block);
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            regions.setCorner(player, 2, block);
        }
    }

    // =====================================================================
    //  Enter / leave tracking
    // =====================================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        // Ignore head turning: only react when the player changes block.
        if (to == null || (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ() && from.getWorld() == to.getWorld())) {
            return;
        }
        if (!update(event.getPlayer(), to)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() != null && !update(event.getPlayer(), event.getTo())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        update(event.getPlayer(), event.getPlayer().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Set<String> was = inside.remove(player.getUniqueId());
        if (was != null && enabled()) {
            for (RegionRef ref : regions.at(player.getLocation())) {
                if (was.contains(ref.id())) {
                    Bukkit.getPluginManager().callEvent(new RegionLeaveEvent(player, ref, true));
                }
            }
        }
        regions.forget(player);
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() != core) {
            regions.unload(event.getPlugin());
        }
    }

    /**
     * Compares the regions at the new location with the ones the player was in, and fires events.
     *
     * @return false if a listener cancelled the enter/leave (the movement should be stopped)
     */
    private boolean update(Player player, Location to) {
        if (!enabled()) {
            return true;
        }
        Set<String> before = inside.getOrDefault(player.getUniqueId(), Set.of());
        List<RegionRef> now = regions.at(to);
        Set<String> nowIds = new HashSet<>();
        for (RegionRef ref : now) {
            nowIds.add(ref.id());
        }
        if (nowIds.equals(before)) {
            return true; // nothing changed - the common case, no events
        }

        // Leaving: regions we were in that are not here. They are looked up from all regions, because
        // the player's NEW location no longer contains them.
        List<RegionRef> leaving = new ArrayList<>();
        if (!before.isEmpty()) {
            for (RegionRef ref : regions.all()) {
                if (before.contains(ref.id()) && !nowIds.contains(ref.id())) {
                    leaving.add(ref);
                }
            }
        }
        for (RegionRef ref : leaving) {
            RegionLeaveEvent leave = new RegionLeaveEvent(player, ref, false);
            Bukkit.getPluginManager().callEvent(leave);
            if (leave.isCancelled()) {
                return false;
            }
        }
        // Entering: regions here we weren't in.
        for (RegionRef ref : now) {
            if (!before.contains(ref.id())) {
                RegionEnterEvent enter = new RegionEnterEvent(player, ref);
                Bukkit.getPluginManager().callEvent(enter);
                if (enter.isCancelled()) {
                    return false;
                }
            }
        }
        inside.put(player.getUniqueId(), nowIds);
        return true;
    }

    private boolean enabled() {
        return core.coreConfig().get().getBoolean("regions.enter-leave-events", true);
    }
}
