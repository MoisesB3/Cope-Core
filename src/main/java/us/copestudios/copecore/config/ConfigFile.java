package us.copestudios.copecore.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;

/**
 * A YAML file in a plugin's folder, loaded safely. Replaces the hand-written
 * "if (!file.exists()) saveResource(...); YamlConfiguration.loadConfiguration(...)" code that every plugin had.
 *
 * <h2>What it does for you</h2>
 * <ol>
 *   <li><b>Creates the file</b> from the copy inside your jar the first time (keeping its comments).</li>
 *   <li><b>Fills missing settings</b>: when you add a new option in an update, it is added to the
 *       server owner's existing file automatically, without touching their other values.</li>
 *   <li><b>Never crashes on a broken file</b>: if the YAML has a typo or the file was emptied, the broken
 *       file is kept as {@code name.broken-<date>} and a fresh default is written. The console explains
 *       what happened. (This is the "config repair" idea from Cope Bosses, now available to every plugin.)</li>
 *   <li><b>Falls back to defaults</b>: reading a key that is missing still returns the value from the jar.</li>
 * </ol>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * // In onEnable():
 * ConfigFile koths = new ConfigFile(this, "koths.yml");
 * int time = koths.get().getInt("koths.castle.capture-time", 300);
 *
 * koths.get().set("koths.castle.capture-time", 600);
 * koths.save();
 *
 * // In your reload command:
 * koths.reload();
 * }</pre>
 *
 * <p>Files in sub folders work too: {@code new ConfigFile(this, "bosses/hunter.yml")}.</p>
 */
public final class ConfigFile {

    private final Plugin plugin;
    /** Path relative to the plugin's data folder AND inside the jar, e.g. "config.yml" or "bosses/hunter.yml". */
    private final String path;
    /** When true, settings that exist in the jar's copy but not on disk are written into the file. */
    private final boolean copyMissingKeys;
    private final File file;
    private YamlConfiguration config = new YamlConfiguration();

    /**
     * Opens (and creates if needed) a file and adds any missing settings from the jar's copy.
     *
     * @param plugin the plugin that owns the file
     * @param path   path inside the plugin folder, e.g. "config.yml" or "lang/messages.yml"
     */
    public ConfigFile(Plugin plugin, String path) {
        this(plugin, path, true);
    }

    /**
     * @param copyMissingKeys pass {@code false} for DATA files (like regions.yml or stats.yml) where the jar's
     *                        copy is only an example and must never be merged into the real data.
     */
    public ConfigFile(Plugin plugin, String path, boolean copyMissingKeys) {
        this.plugin = plugin;
        this.path = path.replace('\\', '/');
        this.copyMissingKeys = copyMissingKeys;
        this.file = new File(plugin.getDataFolder(), this.path);
        reload();
    }

    /** The loaded YAML. Read and change values with the normal Bukkit methods. */
    public YamlConfiguration get() {
        return config;
    }

    /** The file on disk. */
    public File file() {
        return file;
    }

    /** The path this file was opened with (e.g. "config.yml"). */
    public String path() {
        return path;
    }

    /**
     * Reads the file from disk again. Call this from your plugin's reload command.
     * Unsaved changes made with {@code get().set(...)} are thrown away.
     */
    public void reload() {
        YamlConfiguration defaults = readJarCopy();

        // 1) First run: create the file (copy from the jar to keep its comments, or an empty file).
        if (!file.exists()) {
            createFromJar(defaults != null);
        }

        // 2) Load it. An empty file is treated as broken when the jar has a real default.
        YamlConfiguration loaded = new YamlConfiguration();
        boolean broken = false;
        String reason = null;
        if (file.length() == 0 && defaults != null && !defaults.getKeys(false).isEmpty()) {
            broken = true;
            reason = "the file is empty";
        } else {
            try {
                loaded.load(file);
            } catch (IOException | InvalidConfigurationException error) {
                broken = true;
                reason = error.getMessage();
            }
        }

        // 3) Broken? Keep a copy, write a fresh one, and load that instead.
        if (broken) {
            loaded = repair(reason, defaults != null);
        }

        // 4) Defaults: missing keys fall back to the jar's values, and can be written into the file.
        if (defaults != null) {
            loaded.setDefaults(defaults);
            if (copyMissingKeys && hasMissingKeys(loaded, defaults)) {
                loaded.options().copyDefaults(true);
                this.config = loaded;
                save();
                plugin.getLogger().info("Added new settings to " + path + " (your existing values were kept).");
                return;
            }
        }
        this.config = loaded;
    }

    /**
     * Writes the current values to disk. Comments that were in the file are kept on Minecraft 1.18.1+.
     *
     * @return true if it saved, false if writing failed (the reason is printed to the console)
     */
    public boolean save() {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("Could not create folder " + parent.getPath());
            }
            config.save(file);
            return true;
        } catch (IOException error) {
            plugin.getLogger().log(Level.WARNING, "Could not save " + path + ": " + error.getMessage());
            return false;
        }
    }

    // ---------------------------------------------------------------------
    //  Internals
    // ---------------------------------------------------------------------

    /** Reads the copy of this file shipped inside the plugin jar, or null if the jar has none. */
    private YamlConfiguration readJarCopy() {
        try (InputStream in = plugin.getResource(path)) {
            if (in == null) {
                return null;
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException error) {
            return null;
        }
    }

    private void createFromJar(boolean jarHasCopy) {
        try {
            if (jarHasCopy) {
                // saveResource keeps comments and formatting exactly as written in the jar.
                plugin.saveResource(path, false);
            } else {
                File parent = file.getParentFile();
                if (parent != null) {
                    parent.mkdirs();
                }
                file.createNewFile();
            }
        } catch (IOException | IllegalArgumentException error) {
            plugin.getLogger().warning("Could not create " + path + ": " + error.getMessage());
        }
    }

    /** Moves the broken file aside and writes a fresh default. */
    private YamlConfiguration repair(String reason, boolean jarHasCopy) {
        String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        File backup = new File(file.getParentFile(), file.getName() + ".broken-" + stamp);
        try {
            if (file.exists() && file.length() > 0) {
                Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            if (jarHasCopy) {
                plugin.saveResource(path, true);
            } else {
                Files.writeString(file.toPath(), "", StandardCharsets.UTF_8);
            }
        } catch (IOException | IllegalArgumentException error) {
            plugin.getLogger().severe("Could not repair " + path + ": " + error.getMessage());
        }
        plugin.getLogger().warning("------------------------------------------------------------");
        plugin.getLogger().warning(path + " could not be read (" + reason + ").");
        if (backup.exists()) {
            plugin.getLogger().warning("Your old file was kept as " + backup.getName() + " - fix it and copy your values back.");
        }
        plugin.getLogger().warning("A fresh " + path + " was written so the plugin can keep running.");
        plugin.getLogger().warning("------------------------------------------------------------");

        YamlConfiguration fresh = new YamlConfiguration();
        try {
            fresh.load(file);
        } catch (IOException | InvalidConfigurationException error) {
            plugin.getLogger().severe("The default " + path + " inside the jar is invalid too: " + error.getMessage());
        }
        return fresh;
    }

    /** True if the jar's copy has a setting the file on disk doesn't. */
    private static boolean hasMissingKeys(YamlConfiguration onDisk, YamlConfiguration defaults) {
        for (String key : defaults.getKeys(true)) {
            // "true" = ignore defaults, so we see what is really in the file.
            if (!onDisk.contains(key, true)) {
                return true;
            }
        }
        return false;
    }
}
