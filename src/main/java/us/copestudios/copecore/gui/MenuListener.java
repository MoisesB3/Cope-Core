package us.copestudios.copecore.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;
import us.copestudios.copecore.CopeCore;
import us.copestudios.copecore.util.Sounds;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Receives inventory events for EVERY plugin's menus and forwards them to the right {@link Menu}.
 * There is only one of these (inside CopeCore), so individual plugins don't register any menu listener.
 *
 * <p>Compatibility note: this class never calls methods on {@code InventoryView}. That type changed from a class
 * to an interface in Minecraft 1.21, and code compiled for 1.20 that calls it crashes on 1.21. Everything here
 * uses {@code event.getInventory()} / {@code getClickedInventory()} instead, which work on both.</p>
 */
public final class MenuListener implements Listener {

    /** True while a menu click action runs, so Menu.open()/close() know to wait one tick. */
    private static boolean handlingClick;

    /** Which menu each player has open right now. */
    private final Map<UUID, Menu> open = new HashMap<>();
    /** Last click time per player, for the spam-click limit. */
    private final Map<UUID, Long> lastClick = new HashMap<>();

    static boolean isHandlingClick() {
        return handlingClick;
    }

    /** Called by Menu after it opened. */
    void track(Menu menu) {
        open.put(menu.viewer().getUniqueId(), menu);
    }

    /** The menu a player has open, or null. */
    public Menu openMenu(Player player) {
        return open.get(player.getUniqueId());
    }

    /** Closes every CopeCore menu that belongs to a plugin (used when that plugin is disabled or reloaded). */
    public void closeAll(Plugin owner) {
        List<Menu> toClose = new ArrayList<>();
        for (Menu menu : open.values()) {
            if (owner == null || menu.plugin().equals(owner)) {
                toClose.add(menu);
            }
        }
        for (Menu menu : toClose) {
            menu.viewer().closeInventory(); // fires InventoryCloseEvent -> removed from the map below
            open.remove(menu.viewer().getUniqueId());
        }
    }

    // =====================================================================
    //  Clicks
    // =====================================================================

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getInventory();
        if (!(top.getHolder() instanceof Menu menu) || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory clicked = event.getClickedInventory();
        boolean clickedTop = clicked != null && clicked.equals(top);

        if (!clickedTop) {
            // A click in the player's own inventory (or outside the window).
            // Shift-click / double-click could move items INTO the menu, so they are blocked.
            if (event.isShiftClick()
                    || event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                    || event.getAction() == InventoryAction.COLLECT_TO_CURSOR
                    || !menu.hasEditableSlots()) {
                event.setCancelled(true);
            }
            return;
        }

        // A click inside the menu.
        int slot = event.getRawSlot();
        if (menu.isEditable(slot) && event.getAction() != InventoryAction.COLLECT_TO_CURSOR) {
            return; // the player may place/take items here - let Minecraft handle it normally
        }
        event.setCancelled(true);
        if (!player.equals(menu.viewer()) || isSpamClick(player)) {
            return;
        }

        String sound = CopeCore.get().coreConfig().get().getString("menus.click-sound", "");
        handlingClick = true;
        try {
            menu.handleClick(new MenuClick(player, slot, event.getClick(), event));
            if (sound != null && !sound.isEmpty()) {
                Sounds.play(player, sound);
            }
        } catch (RuntimeException error) {
            menu.plugin().getLogger().log(Level.SEVERE,
                    "Error in menu " + menu.getClass().getSimpleName() + " (slot " + slot + ")", error);
        } finally {
            handlingClick = false;
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getInventory();
        if (!(top.getHolder() instanceof Menu menu)) {
            return;
        }
        // Allowed only if every slot it touches in the menu is editable.
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < top.getSize() && !menu.isEditable(rawSlot)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /** True if the player clicked again faster than menus.click-cooldown-ms. */
    private boolean isSpamClick(Player player) {
        long cooldown = CopeCore.get().coreConfig().get().getLong("menus.click-cooldown-ms", 150);
        if (cooldown <= 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long last = lastClick.put(player.getUniqueId(), now);
        return last != null && now - last < cooldown;
    }

    // =====================================================================
    //  Closing
    // =====================================================================

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Menu menu)) {
            return;
        }
        UUID id = event.getPlayer().getUniqueId();
        // Only forget it if this is still the menu we have on record (a new menu may already be tracked).
        if (open.get(id) == menu) {
            open.remove(id);
        }
        try {
            menu.fireClose();
        } catch (RuntimeException error) {
            menu.plugin().getLogger().log(Level.SEVERE, "Error closing menu " + menu.getClass().getSimpleName(), error);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        open.remove(event.getPlayer().getUniqueId());
        lastClick.remove(event.getPlayer().getUniqueId());
    }

    /** When any plugin is disabled, close its menus so nobody is left in a "dead" menu. */
    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        closeAll(event.getPlugin());
    }
}
