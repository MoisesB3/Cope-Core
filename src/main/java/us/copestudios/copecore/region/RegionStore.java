package us.copestudios.copecore.region;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import us.copestudios.copecore.config.ConfigFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A plugin's saved regions (one YAML file, by default plugins/YourPlugin/regions.yml).
 * Get it with {@code CopeCore.regions().store(this)}.
 *
 * <pre>{@code
 * RegionStore arenas = CopeCore.regions().store(this, "arenas.yml");
 *
 * Region arena = CopeCore.regions().selection(player).toRegion("pit");   // from the wand selection
 * arenas.put(arena);                       // saved immediately
 *
 * Region pit = arenas.get("pit");
 * List<Region> here = arenas.at(player.getLocation());
 * arenas.remove("pit");
 * }</pre>
 *
 * <p>Names are case-insensitive ("Pit" and "pit" are the same region) and stored in lower case.
 * Every change is saved to disk right away, and the global lookup used for enter/leave events is updated.</p>
 *
 * <p>File layout:</p>
 * <pre>
 * regions:
 *   pit:
 *     world: world
 *     min: {x: 100, y: 60, z: -20}
 *     max: {x: 120, y: 80, z: 0}
 * </pre>
 */
public final class RegionStore {

    private final RegionService service;
    private final Plugin owner;
    private final ConfigFile file;
    private final Map<String, Region> regions = new LinkedHashMap<>();

    RegionStore(RegionService service, Plugin owner, String fileName) {
        this.service = service;
        this.owner = owner;
        // false = this is a DATA file: never merge example content from the jar into it.
        this.file = new ConfigFile(owner, fileName, false);
        loadFromFile();
    }

    /** The plugin that owns this file. */
    public Plugin owner() {
        return owner;
    }

    /** File name relative to the plugin folder. */
    public String fileName() {
        return file.path();
    }

    // =====================================================================
    //  Reading
    // =====================================================================

    /** The region with this name, or null. */
    public Region get(String name) {
        return name == null ? null : regions.get(key(name));
    }

    /** True if a region with this name exists. */
    public boolean contains(String name) {
        return get(name) != null;
    }

    /** Every region in this file. */
    public Collection<Region> all() {
        return Collections.unmodifiableCollection(regions.values());
    }

    /** Every region name (for tab completion). */
    public List<String> names() {
        return new ArrayList<>(regions.keySet());
    }

    /** The regions of this file that contain the location. */
    public List<Region> at(Location location) {
        List<Region> found = new ArrayList<>();
        for (Region region : regions.values()) {
            if (region.contains(location)) {
                found.add(region);
            }
        }
        return found;
    }

    // =====================================================================
    //  Changing (saved immediately)
    // =====================================================================

    /** Adds the region, or replaces the one with the same name. */
    public void put(Region region) {
        Region stored = region.withName(key(region.name()));
        regions.put(stored.name(), stored);
        stored.save(section().createSection(stored.name()));
        save();
    }

    /** Deletes a region. Returns false if it didn't exist. */
    public boolean remove(String name) {
        Region removed = regions.remove(key(name));
        if (removed == null) {
            return false;
        }
        section().set(removed.name(), null);
        save();
        return true;
    }

    /** Re-reads the file from disk (for your plugin's reload command). */
    public void reload() {
        file.reload();
        loadFromFile();
    }

    // =====================================================================
    //  Internals
    // =====================================================================

    private void loadFromFile() {
        regions.clear();
        ConfigurationSection section = file.get().getConfigurationSection("regions");
        if (section != null) {
            for (String name : section.getKeys(false)) {
                Region region = Region.load(key(name), section.getConfigurationSection(name));
                if (region == null) {
                    owner.getLogger().warning("Region '" + name + "' in " + file.path() + " is incomplete and was skipped.");
                } else {
                    regions.put(region.name(), region);
                }
            }
        }
        service.markChanged();
    }

    private ConfigurationSection section() {
        ConfigurationSection section = file.get().getConfigurationSection("regions");
        return section != null ? section : file.get().createSection("regions");
    }

    private void save() {
        file.save();
        service.markChanged();
    }

    private static String key(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
