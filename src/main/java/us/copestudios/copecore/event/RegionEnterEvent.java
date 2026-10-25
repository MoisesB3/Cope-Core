package us.copestudios.copecore.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import us.copestudios.copecore.region.RegionRef;

/**
 * Fired when a player walks or teleports INTO any region of any Cope plugin
 * (enable with config.yml: regions.enter-leave-events).
 *
 * <p>Cancelling it stops the player from entering (they stay where they were). Example: blocking players who are
 * in combat from entering a safe zone, or keeping people out of a running KOTH.</p>
 *
 * <pre>{@code
 * @EventHandler
 * public void onEnter(RegionEnterEvent event) {
 *     if (!event.region().ownerName().equals("CSNumbers")) return; // only my regions
 *     stats.startTimer(event.getPlayer(), event.region().name());
 * }
 * }</pre>
 */
public final class RegionEnterEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final RegionRef region;
    private boolean cancelled;

    public RegionEnterEvent(Player player, RegionRef region) {
        super(player);
        this.region = region;
    }

    /** The region being entered, with the plugin that owns it. */
    public RegionRef region() {
        return region;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    /** Required by Bukkit for every event class. */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
