package us.copestudios.copecore.region;

import org.bukkit.Location;

/**
 * The two corners a player picked with the selection wand (or /copecore pos1 and pos2).
 * Every Cope plugin reads the SAME selection, so admins learn one tool for KOTHs, arenas, zones and RTP areas.
 *
 * <pre>{@code
 * Selection selection = CopeCore.regions().selection(player);
 * if (!selection.isComplete()) {
 *     CopeCore.get().coreMessages().send(player, "selection.incomplete");   // shared message
 *     return;
 * }
 * Region region = selection.toRegion("castle");
 * }</pre>
 */
public final class Selection {

    private Location corner1;
    private Location corner2;

    /** Corner 1 (left click), or null. */
    public Location corner1() {
        return corner1 == null ? null : corner1.clone();
    }

    /** Corner 2 (right click), or null. */
    public Location corner2() {
        return corner2 == null ? null : corner2.clone();
    }

    /** Sets corner 1 or 2. */
    public void set(int corner, Location location) {
        Location block = location.getBlock().getLocation(); // snap to the block
        if (corner == 1) {
            corner1 = block;
        } else {
            corner2 = block;
        }
    }

    /** True if both corners are set AND in the same world. */
    public boolean isComplete() {
        return corner1 != null && corner2 != null && corner1.getWorld() != null
                && corner1.getWorld().equals(corner2.getWorld());
    }

    /** True if both corners are set but in different worlds. */
    public boolean isDifferentWorlds() {
        return corner1 != null && corner2 != null && corner1.getWorld() != null
                && !corner1.getWorld().equals(corner2.getWorld());
    }

    /** The selection as a region with this name, or null if it isn't complete. */
    public Region toRegion(String name) {
        return isComplete() ? Region.of(name, corner1, corner2) : null;
    }

    /** Forgets both corners. */
    public void clear() {
        corner1 = null;
        corner2 = null;
    }
}
