package us.copestudios.copecore.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumSet;
import java.util.Set;

/**
 * Saving, loading and describing locations, plus finding a safe place to stand.
 *
 * <pre>{@code
 * Locations.save(config, "lobby", player.getLocation());
 * Location lobby = Locations.load(config, "lobby");     // null if missing or the world isn't loaded
 * String text = Locations.describe(lobby);              // "120, 64, -30 (world)"
 *
 * Location spot = Locations.findSafeSpot(world, x, z, 0, 320); // for random teleports
 * }</pre>
 */
public final class Locations {

    /** Blocks you must never be placed on top of or inside. */
    private static final Set<Material> DANGEROUS = EnumSet.of(
            Material.LAVA, Material.MAGMA_BLOCK, Material.CACTUS, Material.FIRE, Material.SOUL_FIRE,
            Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.POWDER_SNOW, Material.SWEET_BERRY_BUSH,
            Material.WITHER_ROSE, Material.POINTED_DRIPSTONE);

    private Locations() {
        // static helper only
    }

    /**
     * Writes a location to a config under {@code path} (world, x, y, z, yaw, pitch).
     * Example result in YAML:
     * <pre>
     * lobby:
     *   world: world
     *   x: 120.5
     *   ...
     * </pre>
     */
    public static void save(ConfigurationSection config, String path, Location location) {
        if (location == null || location.getWorld() == null) {
            config.set(path, null);
            return;
        }
        config.set(path + ".world", location.getWorld().getName());
        config.set(path + ".x", location.getX());
        config.set(path + ".y", location.getY());
        config.set(path + ".z", location.getZ());
        config.set(path + ".yaw", (double) location.getYaw());
        config.set(path + ".pitch", (double) location.getPitch());
    }

    /**
     * Reads a location saved with {@link #save}.
     *
     * @return the location, or null if it's missing or its world isn't loaded
     */
    public static Location load(ConfigurationSection config, String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            return null;
        }
        World world = Bukkit.getWorld(section.getString("world", ""));
        if (world == null) {
            return null;
        }
        return new Location(world,
                section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                (float) section.getDouble("yaw"), (float) section.getDouble("pitch"));
    }

    /** "120, 64, -30 (world)" - block coordinates, for messages. */
    public static String describe(Location location) {
        if (location == null) {
            return "not set";
        }
        String world = location.getWorld() == null ? "?" : location.getWorld().getName();
        return location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ() + " (" + world + ")";
    }

    /**
     * Finds the highest safe place to stand in one column, between {@code minY} and {@code maxY}.
     *
     * <p>"Safe" = solid ground that isn't lava/magma/cactus/fire/etc, with two free (non-liquid) blocks above
     * for the player's legs and head.</p>
     *
     * <p><b>Nether fix:</b> the nether has a bedrock roof at Y 127. Scanning from the top finds that roof first
     * and puts players on top of it (the bug CZRTP had). In the nether we start scanning below the roof.</p>
     *
     * <p>Note: this loads the chunk if it isn't loaded, so don't call it hundreds of times in one tick.</p>
     *
     * @return the middle of the safe block (x+0.5, y, z+0.5), or null if the column has no safe spot
     */
    public static Location findSafeSpot(World world, int x, int z, int minY, int maxY) {
        if (world == null) {
            return null;
        }
        int top = Math.min(maxY, world.getMaxHeight() - 2);
        int bottom = Math.max(minY, world.getMinHeight());
        if (world.getEnvironment() == World.Environment.NETHER) {
            top = Math.min(top, 120); // stay under the bedrock roof
        }

        for (int y = top; y >= bottom; y--) {
            Block ground = world.getBlockAt(x, y, z);
            if (!isSafeGround(ground)) {
                continue;
            }
            Block feet = ground.getRelative(0, 1, 0);
            Block head = ground.getRelative(0, 2, 0);
            if (isFreeSpace(feet) && isFreeSpace(head)) {
                return new Location(world, x + 0.5, y + 1, z + 0.5);
            }
        }
        return null;
    }

    private static boolean isSafeGround(Block block) {
        Material type = block.getType();
        return type.isSolid() && !block.isLiquid() && !DANGEROUS.contains(type);
    }

    private static boolean isFreeSpace(Block block) {
        return block.isPassable() && !block.isLiquid() && !DANGEROUS.contains(block.getType());
    }
}
