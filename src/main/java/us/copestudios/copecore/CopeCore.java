package us.copestudios.copecore;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import us.copestudios.copecore.config.ConfigFile;
import us.copestudios.copecore.entity.ProtectedEntities;
import us.copestudios.copecore.gui.MenuListener;
import us.copestudios.copecore.hook.PlaceholderService;
import us.copestudios.copecore.internal.CoreCommand;
import us.copestudios.copecore.internal.StartupChecks;
import us.copestudios.copecore.player.PlayerStates;
import us.copestudios.copecore.prompt.ChatPrompts;
import us.copestudios.copecore.region.RegionListener;
import us.copestudios.copecore.region.RegionService;
import us.copestudios.copecore.scheduler.Tasks;
import us.copestudios.copecore.text.Messages;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CopeCore's main class, and the front door for every other Cope plugin.
 *
 * <h2>Using CopeCore from another plugin</h2>
 * <ol>
 *   <li>pom.xml: depend on {@code us.copestudios:copecore} with {@code <scope>provided</scope>} (see README).</li>
 *   <li>plugin.yml: {@code depend: [CopeCore]}</li>
 *   <li>Code: use the static shortcuts below.</li>
 * </ol>
 *
 * <pre>{@code
 * @Override
 * public void onEnable() {
 *     CopeCore.requireApi(this, 1);                     // stop with a clear message if CopeCore is too old
 *     Messages messages = CopeCore.messages(this);      // plugins/YourPlugin/messages.yml
 *     RegionStore arenas = CopeCore.regions().store(this, "arenas.yml");
 *     CopeCore.placeholders().register(this, "pvp", (player, key) -> ...);
 * }
 * }</pre>
 *
 * <h2>The shortcuts</h2>
 * <table>
 *   <caption>What each one gives you</caption>
 *   <tr><td>{@link #messages(Plugin)}</td><td>your plugin's messages.yml with MiniMessage + legacy colors</td></tr>
 *   <tr><td>{@link #prompts()}</td><td>thread-safe "type the answer in chat" questions</td></tr>
 *   <tr><td>{@link #regions()}</td><td>region files, the shared selection wand, region lookups</td></tr>
 *   <tr><td>{@link #states()}</td><td>shared player states (combat, in-arena, ...)</td></tr>
 *   <tr><td>{@link #protection()}</td><td>indestructible entities</td></tr>
 *   <tr><td>{@link #placeholders()}</td><td>%cope_yourplugin_...% PlaceholderAPI placeholders</td></tr>
 * </table>
 * <p>Plus classes you use directly: {@code Text}, {@code ConfigFile}, {@code CopeCommand}, {@code Menu},
 * {@code ItemBuilder}, {@code Tasks}, {@code Cooldowns}, {@code RewardSet}, {@code Sounds}, {@code TimeFormat},
 * {@code Locations}, {@code ServerVersion}, {@code Services}, {@code Particles}, {@code Attributes}, {@code Entities}.</p>
 */
public final class CopeCore extends JavaPlugin implements Listener {

    /**
     * Version of CopeCore's API. Goes up by one whenever something is ADDED that other plugins may rely on.
     * Plugins check it with {@link #requireApi(Plugin, int)}.
     * <ul>
     *   <li>1 - CopeCore 1.0.0: first release</li>
     *   <li>2 - CopeCore 1.1.0: Particles, Attributes, Entities, CopeCommand.fallback()</li>
     * </ul>
     */
    public static final int API_VERSION = 2;

    private static CopeCore instance;

    private ConfigFile coreConfig;
    private Messages coreMessages;
    private MenuListener menus;
    private ChatPrompts prompts;
    private RegionService regions;
    private PlayerStates states;
    private ProtectedEntities protection;
    private PlaceholderService placeholders;
    /** One Messages instance per plugin + file, so every call to messages(plugin) shares it. */
    private final Map<String, Messages> pluginMessages = new ConcurrentHashMap<>();

    // =====================================================================
    //  Startup / shutdown
    // =====================================================================

    @Override
    public void onEnable() {
        instance = this;
        long start = System.currentTimeMillis();

        // 1) Files first: everything else reads settings and texts from them.
        coreConfig = new ConfigFile(this, "config.yml");
        coreMessages = new Messages(this, "messages.yml", null);

        // 2) Services.
        menus = new MenuListener();
        prompts = new ChatPrompts(this);
        regions = new RegionService(this);
        states = new PlayerStates(this);
        protection = new ProtectedEntities(this);
        placeholders = new PlaceholderService(this);

        // 3) Listeners.
        var pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(menus, this);
        pluginManager.registerEvents(prompts, this);
        pluginManager.registerEvents(new RegionListener(this, regions), this);
        pluginManager.registerEvents(protection, this);
        pluginManager.registerEvents(this, this);

        // 4) /copecore
        new CoreCommand(this).register();

        // 5) Optional hooks.
        placeholders.hook();

        // 6) Checks that need every plugin loaded: run one tick after the server finished starting.
        Tasks.later(this, 1, () -> new StartupChecks(this).run());

        getLogger().info("CopeCore " + getDescription().getVersion() + " enabled in "
                + (System.currentTimeMillis() - start) + "ms (API " + API_VERSION + ").");
    }

    @Override
    public void onDisable() {
        if (menus != null) {
            menus.closeAll(null);
        }
        if (placeholders != null) {
            placeholders.unhook();
        }
        pluginMessages.clear();
        instance = null;
    }

    /** Reloads CopeCore's own config.yml and messages.yml (used by /copecore reload). */
    public void reloadCore() {
        coreConfig.reload();
        coreMessages.reload();
    }

    // =====================================================================
    //  Static shortcuts for other plugins
    // =====================================================================

    /**
     * The running CopeCore.
     *
     * @throws IllegalStateException if CopeCore isn't enabled - almost always means "depend: [CopeCore]" is
     *                               missing from your plugin.yml
     */
    public static CopeCore get() {
        if (instance == null) {
            throw new IllegalStateException("CopeCore is not enabled. Add 'depend: [CopeCore]' to your plugin.yml "
                    + "and make sure CopeCore.jar is in the plugins folder.");
        }
        return instance;
    }

    /** The running CopeCore, or null (for code that must work even during shutdown). */
    public static CopeCore instance() {
        return instance;
    }

    /**
     * Checks that the installed CopeCore is new enough. If not, prints a clear message and disables the
     * calling plugin instead of letting it crash later with a confusing error.
     *
     * @return true if it's fine to continue enabling
     */
    public static boolean requireApi(Plugin plugin, int minimumApi) {
        if (instance == null || API_VERSION >= minimumApi) {
            return instance != null;
        }
        plugin.getLogger().severe("This plugin needs CopeCore API " + minimumApi + " or newer, but the installed "
                + "CopeCore is API " + API_VERSION + " (v" + instance.getDescription().getVersion() + "). Update CopeCore.");
        Bukkit.getPluginManager().disablePlugin(plugin);
        return false;
    }

    /** The plugin's messages.yml (created from the jar's copy the first time). */
    public static Messages messages(Plugin plugin) {
        return messages(plugin, "messages.yml");
    }

    /** A messages file with a custom name, e.g. "lang/es.yml". */
    public static Messages messages(Plugin plugin, String fileName) {
        CopeCore core = get();
        if (plugin == core && fileName.equals("messages.yml")) {
            return core.coreMessages;
        }
        return core.pluginMessages.computeIfAbsent(plugin.getName() + "/" + fileName,
                key -> new Messages(plugin, fileName, core.coreMessages));
    }

    /** Thread-safe "type it in chat" questions. */
    public static ChatPrompts prompts() {
        return get().prompts;
    }

    /** Region files, lookups and the selection wand. */
    public static RegionService regions() {
        return get().regions;
    }

    /** Shared player states (combat, in-arena, ...). */
    public static PlayerStates states() {
        return get().states;
    }

    /** Make entities indestructible. */
    public static ProtectedEntities protection() {
        return get().protection;
    }

    /** %cope_...% PlaceholderAPI placeholders. */
    public static PlaceholderService placeholders() {
        return get().placeholders;
    }

    /** Prints a line to the plugin's console log only when debug is on in CopeCore's config.yml. */
    public static void debug(Plugin plugin, String message) {
        CopeCore core = instance;
        if (core != null && core.coreConfig.get().getBoolean("debug", false)) {
            plugin.getLogger().info("[debug] " + message);
        }
    }

    // =====================================================================
    //  Instance access (mostly used inside CopeCore)
    // =====================================================================

    /** CopeCore's config.yml. */
    public ConfigFile coreConfig() {
        return coreConfig;
    }

    /** CopeCore's messages.yml (the shared "generic.*", "prompt.*" and "selection.*" texts). */
    public Messages coreMessages() {
        return coreMessages;
    }

    /** The menu system. */
    public MenuListener menus() {
        return menus;
    }

    /** Every enabled or disabled plugin that has CopeCore in depend or softdepend. */
    public List<Plugin> dependents() {
        List<Plugin> result = new ArrayList<>();
        for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            if (plugin == this) {
                continue;
            }
            if (plugin.getDescription().getDepend().contains(getName())
                    || plugin.getDescription().getSoftDepend().contains(getName())) {
                result.add(plugin);
            }
        }
        return result;
    }

    // =====================================================================
    //  Clean-up when players leave or plugins stop
    // =====================================================================

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        states.clear(player);
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        Plugin plugin = event.getPlugin();
        if (plugin == this) {
            return;
        }
        placeholders.unregisterAll(plugin);
        pluginMessages.keySet().removeIf(key -> key.startsWith(plugin.getName() + "/"));
    }
}
