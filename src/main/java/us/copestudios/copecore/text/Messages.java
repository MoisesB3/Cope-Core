package us.copestudios.copecore.text;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import us.copestudios.copecore.CopeCore;
import us.copestudios.copecore.config.ConfigFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A plugin's messages.yml: every chat line, title and item name lives here, so server owners can
 * translate and recolor everything without touching code.
 *
 * <h2>Getting one</h2>
 * <pre>{@code
 * // In your plugin's onEnable(). Loads plugins/YourPlugin/messages.yml (created from your jar's copy).
 * Messages messages = CopeCore.messages(this);
 * }</pre>
 *
 * <h2>Using it</h2>
 * <pre>{@code
 * # messages.yml
 * prefix: "<gold>[KOTH] "
 * koth:
 *   started: "<yellow>KOTH <white><koth></white> has started! Capture time: <green><time>"
 *
 * // Java
 * messages.send(player, "koth.started", "koth", name, "time", "5m");   // with prefix
 * messages.broadcast("koth.started", "koth", name, "time", "5m");     // everyone
 * messages.actionBar(player, "koth.capturing", "progress", "45%");
 * meta.setDisplayName(messages.itemName("menu.start-button"));
 * }</pre>
 *
 * <h2>Shared messages</h2>
 * <p>If a key is missing from your plugin's messages.yml, CopeCore's own messages.yml is checked next.
 * That's where the shared texts live ({@code generic.no-permission}, {@code generic.players-only}, the help
 * page layout, prompt hints...). So every plugin gets them for free, and can still override any of them by
 * adding the same key to its own file.</p>
 *
 * <h2>Disabling a message</h2>
 * <p>Set it to {@code ""} in messages.yml and it's simply not sent.</p>
 */
public final class Messages {

    private final Plugin plugin;
    private final ConfigFile file;
    /** CopeCore's own messages, checked when a key is missing. Null for CopeCore itself. */
    private final Messages fallback;

    /**
     * Use {@link CopeCore#messages(Plugin)} instead of calling this directly, so each plugin
     * gets one shared instance.
     */
    public Messages(Plugin plugin, String fileName, Messages fallback) {
        this.plugin = plugin;
        this.file = new ConfigFile(plugin, fileName);
        this.fallback = fallback;
    }

    /** Reloads messages.yml from disk. Call this from your plugin's reload command. */
    public void reload() {
        file.reload();
    }

    /** The plugin these messages belong to. */
    public Plugin plugin() {
        return plugin;
    }

    /** The underlying file, in case you want to read something else from it. */
    public ConfigFile file() {
        return file;
    }

    // =====================================================================
    //  Reading templates
    // =====================================================================

    /** True if this file (or CopeCore's fallback file) has the key. */
    public boolean has(String key) {
        return file.get().contains(key) || (fallback != null && fallback.has(key));
    }

    /**
     * The raw text for a key, before placeholders and colors. Lists are joined with new lines.
     * Returns a visible "missing message" text instead of null, so a typo in a key is easy to spot in-game.
     */
    public String template(String key) {
        ConfigurationSection config = file.get();
        if (config.isList(key)) {
            return String.join("\n", config.getStringList(key));
        }
        String value = config.getString(key);
        if (value != null) {
            return value;
        }
        if (fallback != null && fallback.has(key)) {
            return fallback.template(key);
        }
        return "<red>[Missing message: " + plugin.getName() + " → " + key + "]";
    }

    /** Lines of a list message (for item lore). A single string becomes a one-line list. */
    public List<String> templates(String key) {
        ConfigurationSection config = file.get();
        if (config.isList(key)) {
            return new ArrayList<>(config.getStringList(key));
        }
        if (config.isString(key)) {
            return new ArrayList<>(Collections.singletonList(config.getString(key)));
        }
        if (fallback != null && fallback.has(key)) {
            return fallback.templates(key);
        }
        return new ArrayList<>(Collections.singletonList("<red>[Missing message: " + key + "]"));
    }

    /** The prefix ("prefix" key) as raw text. Empty string if there is none. */
    public String prefix() {
        String prefix = file.get().getString("prefix");
        return prefix == null ? "" : prefix;
    }

    /**
     * A message with placeholders filled in, still as MiniMessage text (no prefix).
     * Useful when you want to build a bigger message from several parts.
     */
    public String format(String key, String... pairs) {
        return Text.replace(template(key), pairs);
    }

    // =====================================================================
    //  Sending
    // =====================================================================

    /**
     * Sends a message WITH the prefix.
     *
     * @param to    a player or the console
     * @param key   path in messages.yml, e.g. "koth.started"
     * @param pairs placeholder name/value pairs: "koth", name, "time", "5m"
     */
    public void send(CommandSender to, String key, String... pairs) {
        deliver(to, format(key, pairs), true);
    }

    /** Sends a message WITHOUT the prefix (good for multi-line lists and help pages). */
    public void sendRaw(CommandSender to, String key, String... pairs) {
        deliver(to, format(key, pairs), false);
    }

    /**
     * Sends text that is NOT in messages.yml (built in code), with the prefix.
     * Prefer keys in messages.yml when the text is something a server owner might want to change.
     */
    public void sendText(CommandSender to, String text, String... pairs) {
        deliver(to, Text.replace(text, pairs), true);
    }

    /** Sends a message (with prefix) to every online player and the console. */
    public void broadcast(String key, String... pairs) {
        String text = format(key, pairs);
        if (text.isEmpty()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            deliver(player, text, true);
        }
        deliver(Bukkit.getConsoleSender(), text, true);
    }

    /** Sends a message (with prefix) to everyone who has a permission, e.g. staff alerts. */
    public void broadcastPermission(String permission, String key, String... pairs) {
        String text = format(key, pairs);
        if (text.isEmpty()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(permission)) {
                deliver(player, text, true);
            }
        }
        deliver(Bukkit.getConsoleSender(), text, true);
    }

    /** Shows a message in the player's action bar (no prefix). */
    public void actionBar(Player player, String key, String... pairs) {
        String text = applyPapi(player, format(key, pairs));
        if (!text.isEmpty()) {
            Text.actionBar(player, text);
        }
    }

    /**
     * Shows a title. Either key may be null for "no title"/"no subtitle". Times are in ticks.
     */
    public void title(Player player, String titleKey, String subtitleKey,
                      int fadeIn, int stay, int fadeOut, String... pairs) {
        String title = titleKey == null ? "" : applyPapi(player, format(titleKey, pairs));
        String subtitle = subtitleKey == null ? "" : applyPapi(player, format(subtitleKey, pairs));
        if (title.isEmpty() && subtitle.isEmpty()) {
            return;
        }
        Text.title(player, title, subtitle, fadeIn, stay, fadeOut);
    }

    // =====================================================================
    //  Items and other plain-string places
    // =====================================================================

    /** A message as an item display name (§ codes, not italic). */
    public String itemName(String key, String... pairs) {
        return Text.itemName(format(key, pairs));
    }

    /** A list message as item lore. */
    public List<String> itemLore(String key, String... pairs) {
        List<String> lines = new ArrayList<>();
        for (String line : templates(key)) {
            lines.add(Text.replace(line, pairs));
        }
        return Text.itemLore(lines);
    }

    /** A message as a § string, for inventory titles, scoreboards, boss bars, signs... */
    public String legacy(String key, String... pairs) {
        return Text.legacy(format(key, pairs));
    }

    // =====================================================================
    //  Internals
    // =====================================================================

    private void deliver(CommandSender to, String text, boolean withPrefix) {
        if (to == null || text == null || text.isEmpty()) {
            return;
        }
        if (to instanceof Player player) {
            text = applyPapi(player, text);
        }
        // Each line of a multi-line message gets the prefix, like a normal chat message would.
        for (String line : text.split("\n", -1)) {
            Component message = Text.component(line);
            if (withPrefix) {
                String prefix = prefix();
                if (!prefix.isEmpty()) {
                    // Prefix and message are parsed separately so the prefix's colors never "leak"
                    // into the message (for example an unclosed <bold> in the prefix).
                    message = Component.empty().append(Text.component(prefix)).append(message);
                }
            }
            Text.sendComponent(to, message);
        }
    }

    /** Fills in PlaceholderAPI placeholders when PlaceholderAPI is installed and enabled in config. */
    private static String applyPapi(Player player, String text) {
        CopeCore core = CopeCore.instance();
        if (core == null || player == null || text.indexOf('%') < 0) {
            return text;
        }
        return CopeCore.placeholders().applyPlaceholderApi(player, text);
    }
}
