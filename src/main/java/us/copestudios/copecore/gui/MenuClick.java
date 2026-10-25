package us.copestudios.copecore.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Information about one click on a menu button, passed to the button's action.
 *
 * <pre>{@code
 * set(13, startButton, click -> {
 *     if (click.isShift()) { ... } else if (click.isRight()) { ... } else { ... }
 * });
 * }</pre>
 */
public final class MenuClick {

    private final Player player;
    private final int slot;
    private final ClickType type;
    private final InventoryClickEvent event;

    MenuClick(Player player, int slot, ClickType type, InventoryClickEvent event) {
        this.player = player;
        this.slot = slot;
        this.type = type;
        this.event = event;
    }

    /** The player who clicked (always the menu's viewer). */
    public Player player() {
        return player;
    }

    /** The slot that was clicked (0 = top left). */
    public int slot() {
        return slot;
    }

    /** The exact kind of click, for anything the helpers below don't cover. */
    public ClickType type() {
        return type;
    }

    /** Left click (with or without shift). */
    public boolean isLeft() {
        return type.isLeftClick();
    }

    /** Right click (with or without shift). */
    public boolean isRight() {
        return type.isRightClick();
    }

    /** Shift was held. */
    public boolean isShift() {
        return type.isShiftClick();
    }

    /** The player pressed their drop key (Q) on the button. Used for "type an exact value". */
    public boolean isDrop() {
        return type == ClickType.DROP || type == ClickType.CONTROL_DROP;
    }

    /** A number key 1-9 was pressed over the button; returns 0-8, or -1 if it wasn't a number key. */
    public int hotbarKey() {
        return type == ClickType.NUMBER_KEY ? event.getHotbarButton() : -1;
    }

    /** The item the player is holding on their cursor (may be null or AIR). */
    public ItemStack cursor() {
        return event.getCursor();
    }

    /** The raw Bukkit event, for advanced cases. */
    public InventoryClickEvent event() {
        return event;
    }
}
