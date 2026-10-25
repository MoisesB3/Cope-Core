package us.copestudios.copecore.hook;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import us.copestudios.copecore.CopeCore;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.logging.Level;

/**
 * ONE PlaceholderAPI expansion for every Cope plugin: {@code %cope_<namespace>_<placeholder>%}.
 * Get it with {@code CopeCore.placeholders()}.
 *
 * <h2>Why</h2>
 * <ul>
 *   <li>CSKoth crashed on servers WITHOUT PlaceholderAPI, because it always created a class that extends
 *       PlaceholderAPI's classes. Here, PlaceholderAPI classes are only touched when it's installed.</li>
 *   <li>Plugins register a simple function - no PlaceholderAPI dependency in their pom.xml at all.</li>
 *   <li>CSCommands' fake "%CSCDATA_PlayerName%" can become a real placeholder.</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * // In CSKoth's onEnable():
 * CopeCore.placeholders().register(this, "koth", (player, key) -> switch (key) {
 *     case "wins" -> String.valueOf(stats.wins(player.getUniqueId()));
 *     case "active" -> koths.activeName();
 *     default -> null;          // null = "unknown placeholder"
 * });
 * // Now %cope_koth_wins% and %cope_koth_active% work in scoreboards, tab, holograms...
 * }</pre>
 *
 * <p>When the registering plugin is disabled, its placeholders are removed automatically.</p>
 */
public final class PlaceholderService {

    /** A plugin's placeholder function. */
    private record Provider(Plugin owner, BiFunction<OfflinePlayer, String, String> resolver) {
    }

    private final CopeCore core;
    private final Map<String, Provider> providers = new ConcurrentHashMap<>();
    /** True once our expansion is registered with PlaceholderAPI. */
    private boolean hooked;

    public PlaceholderService(CopeCore core) {
        this.core = core;
    }

    // =====================================================================
    //  Registering
    // =====================================================================

    /**
     * Registers placeholders under {@code %cope_<namespace>_...%}.
     *
     * @param namespace short lowercase name without "_", e.g. "koth", "bosses", "combat"
     * @param resolver  gets the player (may be offline, may be null for global placeholders) and the part after
     *                  the namespace; returns the text, or null if the placeholder doesn't exist
     */
    public void register(Plugin owner, String namespace, BiFunction<OfflinePlayer, String, String> resolver) {
        String name = namespace.toLowerCase(Locale.ROOT);
        if (name.contains("_")) {
            throw new IllegalArgumentException("Placeholder namespace can't contain '_': " + namespace);
        }
        Provider existing = providers.get(name);
        if (existing != null && !existing.owner().equals(owner)) {
            core.getLogger().warning("Placeholder namespace '" + name + "' was already used by "
                    + existing.owner().getName() + " and is now taken over by " + owner.getName() + ".");
        }
        providers.put(name, new Provider(owner, resolver));
    }

    /** Removes every namespace a plugin registered (done automatically when it's disabled). */
    public void unregisterAll(Plugin owner) {
        providers.values().removeIf(provider -> provider.owner().equals(owner));
    }

    /** Registered namespaces and the plugin behind each one (for /copecore info). */
    public Map<String, String> namespaces() {
        Map<String, String> result = new TreeMap<>();
        providers.forEach((name, provider) -> result.put(name, provider.owner().getName()));
        return result;
    }

    // =====================================================================
    //  Resolving
    // =====================================================================

    /**
     * Resolves "koth_wins" style text (namespace + "_" + rest). Used by the PlaceholderAPI expansion,
     * and usable directly without PlaceholderAPI.
     *
     * @return the value, or null if unknown
     */
    public String resolve(OfflinePlayer player, String params) {
        int split = params.indexOf('_');
        String namespace = (split < 0 ? params : params.substring(0, split)).toLowerCase(Locale.ROOT);
        String rest = split < 0 ? "" : params.substring(split + 1);
        Provider provider = providers.get(namespace);
        if (provider == null || !provider.owner().isEnabled()) {
            return null;
        }
        try {
            return provider.resolver().apply(player, rest);
        } catch (RuntimeException error) {
            provider.owner().getLogger().log(Level.WARNING, "Placeholder %cope_" + params + "% failed", error);
            return null;
        }
    }

    /**
     * Fills in ALL PlaceholderAPI placeholders in a text (any plugin's, not just ours) if PlaceholderAPI is
     * installed and config.yml placeholders.parse-in-messages is true. Otherwise returns the text unchanged.
     */
    public String applyPlaceholderApi(Player player, String text) {
        if (!hooked || text == null || text.indexOf('%') < 0
                || !core.coreConfig().get().getBoolean("placeholders.parse-in-messages", true)) {
            return text;
        }
        try {
            return PapiBridge.apply(player, text);
        } catch (Throwable error) {
            return text; // PlaceholderAPI removed or broken - never break a message because of it
        }
    }

    /** True if PlaceholderAPI is installed and our expansion is registered. */
    public boolean isHooked() {
        return hooked;
    }

    // =====================================================================
    //  Hooking into PlaceholderAPI
    // =====================================================================

    /** Registers %cope_...% with PlaceholderAPI if it's installed and enabled in config. Called on startup. */
    public void hook() {
        if (hooked || !core.coreConfig().get().getBoolean("placeholders.enabled", true)) {
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return; // not installed: our placeholders simply aren't available to other plugins
        }
        try {
            // PapiBridge is the ONLY class that mentions PlaceholderAPI types. Because we only reach this line
            // when PlaceholderAPI is installed, servers without it never load those types (no crash).
            hooked = PapiBridge.register(core, this);
            if (hooked) {
                core.getLogger().info("Hooked into PlaceholderAPI: %cope_<plugin>_<placeholder>% is available.");
            }
        } catch (Throwable error) {
            core.getLogger().log(Level.WARNING, "Could not hook into PlaceholderAPI", error);
        }
    }

    /** Unregisters our expansion (on shutdown). */
    public void unhook() {
        if (hooked) {
            try {
                PapiBridge.unregister();
            } catch (Throwable ignored) {
                // shutting down anyway
            }
            hooked = false;
        }
    }
}
