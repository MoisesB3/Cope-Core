package us.copestudios.copecore.region;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import us.copestudios.copecore.CopeCore;
import us.copestudios.copecore.gui.ItemBuilder;
import us.copestudios.copecore.scheduler.Tasks;
import us.copestudios.copecore.text.Messages;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything about regions in one place: each plugin's region files, the global "which regions are here?"
 * lookup, and the shared selection wand.
 * Get it with {@code CopeCore.regions()}.
 *
 * <pre>{@code
 * RegionService regions = CopeCore.regions();
 *
 * RegionStore myRegions = regions.store(this);                  // plugins/MyPlugin/regions.yml
 * List<RegionRef> here = regions.at(player.getLocation());     // regions of ALL Cope plugins here
 * boolean inArena = regions.isInside(player, otherPlugin, "pit");
 *
 * regions.giveWand(player);
 * Region picked = regions.selection(player).toRegion("castle");
 * }</pre>
 */
public final class RegionService {

    private final CopeCore core;
    /** "PluginName/file.yml" -> store */
    private final Map<String, RegionStore> stores = new LinkedHashMap<>();
    private final Map<UUID, Selection> selections = new HashMap<>();
    private final Map<UUID, BukkitTask> outlines = new HashMap<>();

    /** world name -> every region in that world. Rebuilt lazily after any change. */
    private Map<String, List<RegionRef>> byWorld = Collections.emptyMap();
    private boolean changed = true;

    public RegionService(CopeCore core) {
        this.core = core;
    }

    // =====================================================================
    //  Stores
    // =====================================================================

    /** The plugin's default region file: plugins/YourPlugin/regions.yml */
    public RegionStore store(Plugin plugin) {
        return store(plugin, "regions.yml");
    }

    /** A region file with a custom name, e.g. "arenas.yml" or "zones/duels.yml". Same instance every call. */
    public synchronized RegionStore store(Plugin plugin, String fileName) {
        String id = plugin.getName() + "/" + fileName;
        RegionStore store = stores.get(id);
        if (store == null) {
            store = new RegionStore(this, plugin, fileName);
            stores.put(id, store);
            changed = true;
        }
        return store;
    }

    /** Forgets a plugin's stores (called automatically when that plugin is disabled). */
    public synchronized void unload(Plugin plugin) {
        stores.values().removeIf(store -> store.owner().equals(plugin));
        changed = true;
    }

    /** Every loaded store. */
    public synchronized List<RegionStore> stores() {
        return new ArrayList<>(stores.values());
    }

    // =====================================================================
    //  Global lookups (all plugins)
    // =====================================================================

    /** Every region of every Cope plugin that contains this location. */
    public List<RegionRef> at(Location location) {
        if (location == null || location.getWorld() == null) {
            return Collections.emptyList();
        }
        List<RegionRef> inWorld = index().getOrDefault(location.getWorld().getName(), Collections.emptyList());
        List<RegionRef> found = new ArrayList<>();
        for (RegionRef ref : inWorld) {
            if (ref.region().contains(location)) {
                found.add(ref);
            }
        }
        return found;
    }

    /** Every region of every plugin, in every world. */
    public List<RegionRef> all() {
        List<RegionRef> all = new ArrayList<>();
        for (List<RegionRef> list : index().values()) {
            all.addAll(list);
        }
        return all;
    }

    /** True if the player is inside the named region of the given plugin. */
    public boolean isInside(Player player, Plugin owner, String regionName) {
        for (RegionRef ref : at(player.getLocation())) {
            if (ref.isFrom(owner) && ref.name().equalsIgnoreCase(regionName)) {
                return true;
            }
        }
        return false;
    }

    /** Called by RegionStore after any change. */
    void markChanged() {
        changed = true;
    }

    private synchronized Map<String, List<RegionRef>> index() {
        if (changed) {
            Map<String, List<RegionRef>> rebuilt = new HashMap<>();
            for (RegionStore store : stores.values()) {
                for (Region region : store.all()) {
                    rebuilt.computeIfAbsent(region.world(), world -> new ArrayList<>())
                            .add(new RegionRef(store.owner(), store, region));
                }
            }
            byWorld = rebuilt;
            changed = false;
        }
        return byWorld;
    }

    // =====================================================================
    //  Selection wand
    // =====================================================================

    /** The player's selection (created empty the first time). */
    public Selection selection(Player player) {
        return selections.computeIfAbsent(player.getUniqueId(), id -> new Selection());
    }

    /** Forgets a player's selection (on logout). */
    public void forget(Player player) {
        selections.remove(player.getUniqueId());
        BukkitTask outline = outlines.remove(player.getUniqueId());
        if (outline != null) {
            outline.cancel();
        }
    }

    /** The wand item (material from config.yml, name/lore from messages.yml, marked with a hidden tag). */
    public ItemStack wandItem() {
        Messages messages = core.coreMessages();
        Material material = ItemBuilder.material(
                core.coreConfig().get().getString("selection.wand-material", "BLAZE_ROD"), Material.BLAZE_ROD);
        return ItemBuilder.of(material)
                .name(messages.format("selection.wand-name"))
                .lore(messages.templates("selection.wand-lore"))
                .glow()
                .tag(core, "wand", "selection")
                .build();
    }

    /** Puts a wand in the player's inventory (dropped at their feet if it's full). */
    public void giveWand(Player player) {
        for (ItemStack leftover : player.getInventory().addItem(wandItem()).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
        core.coreMessages().send(player, "selection.wand-given");
    }

    /** True if the item is the selection wand. */
    public boolean isWand(ItemStack item) {
        return "selection".equals(ItemBuilder.readTag(core, item, "wand"));
    }

    /**
     * Sets a corner, tells the player, and shows the outline. Used by the wand and by /copecore pos1|pos2.
     */
    public void setCorner(Player player, int corner, Location location) {
        Selection selection = selection(player);
        selection.set(corner, location);
        Messages messages = core.coreMessages();
        messages.send(player, "selection.corner-set",
                "corner", String.valueOf(corner),
                "x", String.valueOf(location.getBlockX()),
                "y", String.valueOf(location.getBlockY()),
                "z", String.valueOf(location.getBlockZ()),
                "world", location.getWorld() == null ? "?" : location.getWorld().getName());
        if (selection.isDifferentWorlds()) {
            messages.send(player, "selection.different-worlds");
        } else if (selection.isComplete()) {
            Region region = selection.toRegion("selection");
            messages.send(player, "selection.selection-size",
                    "x", String.valueOf(region.sizeX()), "y", String.valueOf(region.sizeY()),
                    "z", String.valueOf(region.sizeZ()), "volume", String.valueOf(region.volume()));
            showOutline(player, region);
        }
    }

    // =====================================================================
    //  Outline particles
    // =====================================================================

    /**
     * Draws the edges of a region with particles that only this player can see, for the number of seconds
     * in config.yml (selection.outline-seconds). Plugins can call it too, e.g. "/koth show castle".
     */
    public void showOutline(Player player, Region region) {
        if (!core.coreConfig().get().getBoolean("selection.show-outline", true)) {
            return;
        }
        Particle particle = outlineParticle();
        World world = region.bukkitWorld();
        if (particle == null || world == null) {
            return;
        }
        BukkitTask previous = outlines.remove(player.getUniqueId());
        if (previous != null) {
            previous.cancel();
        }

        List<Location> points = edgePoints(world, region);
        int seconds = Math.max(1, core.coreConfig().get().getInt("selection.outline-seconds", 10));
        int[] runsLeft = {seconds * 2}; // runs twice a second
        BukkitTask[] self = new BukkitTask[1];
        self[0] = Tasks.timer(core, 0, 10, () -> {
            if (!player.isOnline() || runsLeft[0]-- <= 0 || !player.getWorld().equals(world)) {
                self[0].cancel();
                outlines.remove(player.getUniqueId(), self[0]);
                return;
            }
            for (Location point : points) {
                player.spawnParticle(particle, point, 1, 0, 0, 0, 0);
            }
        });
        outlines.put(player.getUniqueId(), self[0]);
    }

    /** Points along the 12 edges of the box, spaced so huge regions don't spam thousands of particles. */
    private static List<Location> edgePoints(World world, Region r) {
        double x1 = r.minX();
        double y1 = r.minY();
        double z1 = r.minZ();
        double x2 = r.maxX() + 1;
        double y2 = r.maxY() + 1;
        double z2 = r.maxZ() + 1;
        long perimeter = 4L * (r.sizeX() + r.sizeY() + r.sizeZ());
        double step = Math.max(0.5, perimeter / 400.0); // at most ~400 particles per draw

        List<Location> points = new ArrayList<>();
        double[][] corners = {{x1, y1, z1}, {x2, y1, z1}, {x1, y2, z1}, {x1, y1, z2},
                {x2, y2, z1}, {x2, y1, z2}, {x1, y2, z2}, {x2, y2, z2}};
        int[][] edges = {{0, 1}, {0, 2}, {0, 3}, {1, 4}, {1, 5}, {2, 4}, {2, 6}, {3, 5}, {3, 6}, {4, 7}, {5, 7}, {6, 7}};
        for (int[] edge : edges) {
            double[] a = corners[edge[0]];
            double[] b = corners[edge[1]];
            double length = Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) + Math.abs(a[2] - b[2]);
            int count = (int) Math.max(1, Math.round(length / step));
            for (int i = 0; i <= count; i++) {
                double t = (double) i / count;
                points.add(new Location(world, a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t));
            }
        }
        return points;
    }

    /** The first particle from config that exists on this server version (names changed in 1.20.5). */
    private Particle outlineParticle() {
        List<String> names = core.coreConfig().get().getStringList("selection.outline-particles");
        if (names.isEmpty()) {
            names = List.of("HAPPY_VILLAGER", "VILLAGER_HAPPY", "FLAME");
        }
        for (String name : names) {
            try {
                Particle particle = Particle.valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
                // Particles that need extra data (like DUST) can't be drawn this simple way.
                if (particle.getDataType() == Void.class) {
                    return particle;
                }
            } catch (IllegalArgumentException notOnThisVersion) {
                // try the next name
            }
        }
        return null;
    }
}
