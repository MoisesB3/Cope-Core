package us.copestudios.copecore.internal;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import us.copestudios.copecore.CopeCore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Runs once, one tick after the server finished loading every plugin:
 * <ul>
 *   <li>prints which plugins use CopeCore (config.yml: startup.show-banner)</li>
 *   <li>warns when two plugins claim the same command or alias, if at least one of them is a Cope plugin
 *       (config.yml: startup.warn-command-conflicts). This is how the "/cb" clash between CSCombat and
 *       Cope Bosses would have been caught on day one.</li>
 * </ul>
 */
public final class StartupChecks {

    private final CopeCore core;

    public StartupChecks(CopeCore core) {
        this.core = core;
    }

    public void run() {
        if (core.coreConfig().get().getBoolean("startup.show-banner", true)) {
            banner();
        }
        if (core.coreConfig().get().getBoolean("startup.warn-command-conflicts", true)) {
            commandConflicts();
        }
    }

    private void banner() {
        List<Plugin> dependents = core.dependents();
        List<String> names = new ArrayList<>();
        for (Plugin plugin : dependents) {
            names.add(plugin.getName() + " " + plugin.getDescription().getVersion() + (plugin.isEnabled() ? "" : " (DISABLED)"));
        }
        core.getLogger().info("Plugins using CopeCore (" + dependents.size() + "): "
                + (names.isEmpty() ? "none yet" : String.join(", ", names)));
    }

    private void commandConflicts() {
        Set<String> copePlugins = new HashSet<>();
        copePlugins.add(core.getName());
        for (Plugin plugin : core.dependents()) {
            copePlugins.add(plugin.getName());
        }

        // label (command name or alias) -> plugins that declared it in their plugin.yml
        Map<String, List<String>> owners = new HashMap<>();
        for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            Map<String, Map<String, Object>> commands = plugin.getDescription().getCommands();
            if (commands == null) {
                continue;
            }
            for (Map.Entry<String, Map<String, Object>> command : commands.entrySet()) {
                addOwner(owners, command.getKey(), plugin.getName());
                Object aliases = command.getValue() == null ? null : command.getValue().get("aliases");
                if (aliases instanceof List<?> list) {
                    for (Object alias : list) {
                        addOwner(owners, String.valueOf(alias), plugin.getName());
                    }
                } else if (aliases instanceof String single) {
                    addOwner(owners, single, plugin.getName());
                }
            }
        }

        for (Map.Entry<String, List<String>> entry : owners.entrySet()) {
            List<String> plugins = entry.getValue();
            if (plugins.size() < 2 || plugins.stream().noneMatch(copePlugins::contains)) {
                continue;
            }
            core.getLogger().warning("Command conflict: /" + entry.getKey() + " is claimed by " + String.join(" and ", plugins)
                    + ". Only one of them will receive it. Use the plugin prefix (/" + plugins.get(0).toLowerCase(Locale.ROOT)
                    + ":" + entry.getKey() + ") or rename the alias in one plugin.yml.");
        }
    }

    private static void addOwner(Map<String, List<String>> owners, String label, String plugin) {
        List<String> list = owners.computeIfAbsent(label.toLowerCase(Locale.ROOT), key -> new ArrayList<>());
        if (!list.contains(plugin)) {
            list.add(plugin);
        }
    }
}
