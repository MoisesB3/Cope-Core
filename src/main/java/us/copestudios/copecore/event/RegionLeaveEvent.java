package us.copestudios.copecore.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import us.copestudios.copecore.region.RegionRef;

/**
 * Fired when a player walks or teleports OUT of a region of any Cope plugin.
 *
 * <p>Cancelling it keeps the player inside (useful for arenas during a fight). Leaving because of logging out
 * also fires this event with {@link #isQuit()} = true; that one can't be cancelled.</p>
 */
public final class RegionLeaveEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final RegionRef region;
    private final boolean quit;
    private boolean cancelled;

    public RegionLeaveEvent(Player player, RegionRef region, boolean quit) {
        super(player);
        this.region = region;
        this.quit = quit;
    }

    /** The region being left, with the plugin that owns it. */
    public RegionRef region() {
        return region;
    }

    /** True if the player is leaving because they left the server. */
    public boolean isQuit() {
        return quit;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    /** Ignored when {@link #isQuit()} is true. */
    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel && !quit;
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
