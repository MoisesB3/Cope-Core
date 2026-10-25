package us.copestudios.copecore.region;

import org.bukkit.plugin.Plugin;

/**
 * A region together with the plugin (and file) it belongs to. Returned by the global lookups in
 * {@link RegionService}, because two plugins may each have a region called "arena".
 *
 * @param owner  the plugin that created the region
 * @param store  the file it is saved in
 * @param region the region itself
 */
public record RegionRef(Plugin owner, RegionStore store, Region region) {

    /** The region's name. */
    public String name() {
        return region.name();
    }

    /** The owning plugin's name, e.g. "CSKoth". */
    public String ownerName() {
        return owner.getName();
    }

    /** A unique id across all plugins: "cskoth:castle". */
    public String id() {
        return owner.getName().toLowerCase(java.util.Locale.ROOT) + ":" + region.name();
    }

    /** True if this region belongs to the given plugin. */
    public boolean isFrom(Plugin plugin) {
        return owner.equals(plugin);
    }
}
