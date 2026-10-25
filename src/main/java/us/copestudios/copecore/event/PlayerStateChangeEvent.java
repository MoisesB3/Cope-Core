package us.copestudios.copecore.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/**
 * Fired when a shared player state (see {@link us.copestudios.copecore.player.PlayerStates}) starts or ends,
 * for example "combat" starting when CSCombat tags someone, or ending when the tag runs out.
 *
 * <pre>{@code
 * @EventHandler
 * public void onState(PlayerStateChangeEvent event) {
 *     if (event.state().equals(PlayerStates.COMBAT) && event.started()) {
 *         teleports.cancel(event.getPlayer()); // CZRTP: cancel a pending random teleport
 *     }
 * }
 * }</pre>
 */
public final class PlayerStateChangeEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String state;
    private final boolean started;
    private final String setBy;

    public PlayerStateChangeEvent(Player player, String state, boolean started, String setBy) {
        super(player);
        this.state = state;
        this.started = started;
        this.setBy = setBy;
    }

    /** The state's name, e.g. "combat" or "in-arena". */
    public String state() {
        return state;
    }

    /** True when the state began, false when it ended (removed or expired). */
    public boolean started() {
        return started;
    }

    /** Name of the plugin that set the state (may be "unknown"). */
    public String setBy() {
        return setBy;
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
