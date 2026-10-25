package us.copestudios.copecore.region;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Objects;
import java.util.Random;

/**
 * A named box of blocks in one world. This single class replaces the SIX region implementations your plugins had
 * (Cope Bosses zones, CSKoth zones, CZRTP zones, CSSPVP arenas and duel zones, CSNumbers zones).
 *
 * <h2>Rules it follows (and the bugs it avoids)</h2>
 * <ul>
 *   <li>Always 3D. CSSPVP ignored height, so a player flying above an arena counted as inside.</li>
 *   <li>The two corners are sorted into min/max when created, so the order you click them never matters.
 *       (CSSPVP started "max" at Double.MIN_VALUE, which is a tiny POSITIVE number, so arenas at negative
 *       coordinates stretched all the way to 0.)</li>
 *   <li>Both corners included: a region from x=0 to x=10 is 11 blocks wide, just like WorldEdit.</li>
 *   <li>Stores the world NAME, not the World object, so it can be loaded before the world and never holds
 *       an unloaded world in memory.</li>
 *   <li>Immutable: once made it never changes. "Changing" a region means making a new one, which makes it
 *       safe to share between plugins and threads.</li>
 * </ul>
 *
 * <h2>Examples</h2>
 * <pre>{@code
 * Region arena = Region.of("arena", corner1, corner2);
 * if (arena.contains(player.getLocation())) { ... }
 *
 * Location middle = arena.center();
 * Location random = arena.randomSafeLocation(new Random(), 25);   // for RTP / spawns
 *
 * Region bigger = arena.expand(5);           // 5 blocks bigger in every direction
 * }</pre>
 */
public final class Region {

    private final String name;
    private final String world;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;

    /**
     * Makes a region from block coordinates. The corners may be given in any order.
     */
    public Region(String name, String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.name = Objects.requireNonNull(name, "name");
        this.world = Objects.requireNonNull(world, "world");
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    /**
     * Makes a region between two locations (their block positions are used).
     *
     * @throws IllegalArgumentException if the locations are in different worlds or have no world
     */
    public static Region of(String name, Location corner1, Location corner2) {
        if (corner1.getWorld() == null || corner2.getWorld() == null) {
            throw new IllegalArgumentException("Both corners need a world");
        }
        if (!corner1.getWorld().equals(corner2.getWorld())) {
            throw new IllegalArgumentException("Both corners must be in the same world");
        }
        return new Region(name, corner1.getWorld().getName(),
                corner1.getBlockX(), corner1.getBlockY(), corner1.getBlockZ(),
                corner2.getBlockX(), corner2.getBlockY(), corner2.getBlockZ());
    }

    /** A region of {@code radius} blocks around a point (a cube). Handy for "create a KOTH here". */
    public static Region around(String name, Location center, int radius, int height) {
        Location low = center.clone().add(-radius, -1, -radius);
        Location high = center.clone().add(radius, Math.max(0, height - 2), radius);
        return of(name, low, high);
    }

    // =====================================================================
    //  Questions
    // =====================================================================

    /** True if the location is inside (edges included). */
    public boolean contains(Location location) {
        if (location == null || location.getWorld() == null || !location.getWorld().getName().equals(world)) {
            return false;
        }
        return contains(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    /** True if the block is inside. */
    public boolean contains(Block block) {
        return block != null && block.getWorld().getName().equals(world)
                && contains(block.getX(), block.getY(), block.getZ());
    }

    /** True if these block coordinates are inside (world not checked). */
    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /** True if the location is inside when looking from above (height ignored). Rarely what you want. */
    public boolean containsIgnoringHeight(Location location) {
        if (location == null || location.getWorld() == null || !location.getWorld().getName().equals(world)) {
            return false;
        }
        return location.getBlockX() >= minX && location.getBlockX() <= maxX
                && location.getBlockZ() >= minZ && location.getBlockZ() <= maxZ;
    }

    /** True if this region and the other share at least one block. */
    public boolean overlaps(Region other) {
        return other != null && world.equals(other.world)
                && minX <= other.maxX && maxX >= other.minX
                && minY <= other.maxY && maxY >= other.minY
                && minZ <= other.maxZ && maxZ >= other.minZ;
    }

    // =====================================================================
    //  Positions
    // =====================================================================

    /** The world, or null if it isn't loaded. */
    public World bukkitWorld() {
        return Bukkit.getWorld(world);
    }

    /** The middle of the box, or null if the world isn't loaded. */
    public Location center() {
        World bukkitWorld = bukkitWorld();
        if (bukkitWorld == null) {
            return null;
        }
        return new Location(bukkitWorld, (minX + maxX + 1) / 2.0, (minY + maxY + 1) / 2.0, (minZ + maxZ + 1) / 2.0);
    }

    /** The lowest corner as a location, or null if the world isn't loaded. */
    public Location minLocation() {
        World bukkitWorld = bukkitWorld();
        return bukkitWorld == null ? null : new Location(bukkitWorld, minX, minY, minZ);
    }

    /** The highest corner as a location, or null if the world isn't loaded. */
    public Location maxLocation() {
        World bukkitWorld = bukkitWorld();
        return bukkitWorld == null ? null : new Location(bukkitWorld, maxX, maxY, maxZ);
    }

    /**
     * A random safe place to stand inside the region (solid ground, room for a player, no lava/cactus/etc).
     * Uses the region's own height limits, and stays below the nether roof.
     *
     * @param attempts how many random columns to try before giving up (20-50 is plenty)
     * @return a location, or null if no safe spot was found or the world isn't loaded
     */
    public Location randomSafeLocation(Random random, int attempts) {
        World bukkitWorld = bukkitWorld();
        if (bukkitWorld == null) {
            return null;
        }
        for (int i = 0; i < attempts; i++) {
            int x = minX + random.nextInt(sizeX());
            int z = minZ + random.nextInt(sizeZ());
            Location spot = us.copestudios.copecore.util.Locations.findSafeSpot(bukkitWorld, x, z, minY, maxY);
            if (spot != null) {
                return spot;
            }
        }
        return null;
    }

    // =====================================================================
    //  Size
    // =====================================================================

    public int sizeX() {
        return maxX - minX + 1;
    }

    public int sizeY() {
        return maxY - minY + 1;
    }

    public int sizeZ() {
        return maxZ - minZ + 1;
    }

    /** Number of blocks inside. */
    public long volume() {
        return (long) sizeX() * sizeY() * sizeZ();
    }

    // =====================================================================
    //  Making changed copies
    // =====================================================================

    /** Same box, different name. */
    public Region withName(String newName) {
        return new Region(newName, world, minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** The box grown (or shrunk with a negative number) by this many blocks on every side. */
    public Region expand(int blocks) {
        return new Region(name, world, minX - blocks, minY - blocks, minZ - blocks,
                maxX + blocks, maxY + blocks, maxZ + blocks);
    }

    // =====================================================================
    //  Saving / loading
    // =====================================================================

    /**
     * Writes the region into a config section:
     * <pre>
     * world: world
     * min: {x: 100, y: 60, z: -20}
     * max: {x: 120, y: 80, z: 0}
     * </pre>
     */
    public void save(ConfigurationSection section) {
        section.set("world", world);
        section.set("min.x", minX);
        section.set("min.y", minY);
        section.set("min.z", minZ);
        section.set("max.x", maxX);
        section.set("max.y", maxY);
        section.set("max.z", maxZ);
    }

    /**
     * Reads a region written by {@link #save}. Also understands the old "pos1/pos2" layout
     * (x/y/z under pos1 and pos2) used by Cope Bosses and CSNumbers, so their old files can be imported.
     *
     * @return the region, or null if the section is incomplete
     */
    public static Region load(String name, ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String worldName = section.getString("world");
        String a = section.isConfigurationSection("min") ? "min" : "pos1";
        String b = section.isConfigurationSection("max") ? "max" : "pos2";
        if (!section.isConfigurationSection(a) || !section.isConfigurationSection(b)) {
            return null;
        }
        if (worldName == null) {
            // CSNumbers kept the world inside each corner.
            worldName = section.getString(a + ".world");
        }
        if (worldName == null) {
            return null;
        }
        return new Region(name, worldName,
                (int) Math.floor(section.getDouble(a + ".x")), (int) Math.floor(section.getDouble(a + ".y")),
                (int) Math.floor(section.getDouble(a + ".z")),
                (int) Math.floor(section.getDouble(b + ".x")), (int) Math.floor(section.getDouble(b + ".y")),
                (int) Math.floor(section.getDouble(b + ".z")));
    }

    // =====================================================================
    //  Getters
    // =====================================================================

    public String name() {
        return name;
    }

    public String world() {
        return world;
    }

    public int minX() {
        return minX;
    }

    public int minY() {
        return minY;
    }

    public int minZ() {
        return minZ;
    }

    public int maxX() {
        return maxX;
    }

    public int maxY() {
        return maxY;
    }

    public int maxZ() {
        return maxZ;
    }

    /** "world 100,60,-20 → 120,80,0 (21×21×21)" for messages and logs. */
    public String describe() {
        return world + " " + minX + "," + minY + "," + minZ + " → " + maxX + "," + maxY + "," + maxZ
                + " (" + sizeX() + "×" + sizeY() + "×" + sizeZ() + ")";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Region region)) {
            return false;
        }
        return minX == region.minX && minY == region.minY && minZ == region.minZ
                && maxX == region.maxX && maxY == region.maxY && maxZ == region.maxZ
                && name.equals(region.name) && world.equals(region.world);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, world, minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    public String toString() {
        return "Region{" + name + " " + describe() + "}";
    }
}
