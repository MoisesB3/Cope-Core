package us.copestudios.copecore.internal;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import us.copestudios.copecore.CopeCore;
import us.copestudios.copecore.command.CopeCommand;
import us.copestudios.copecore.region.RegionRef;
import us.copestudios.copecore.region.Selection;
import us.copestudios.copecore.text.Messages;
import us.copestudios.copecore.util.ServerVersion;
import us.copestudios.copecore.util.TimeFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * /copecore - built with CopeCore's own command framework, so it doubles as a working example of
 * {@link CopeCommand}.
 *
 * <pre>
 * /copecore info              version, server, plugins using CopeCore
 * /copecore reload            reload config.yml and messages.yml
 * /copecore wand              get the selection wand
 * /copecore pos1 | pos2       set a corner where you stand
 * /copecore selection [clear] show or clear your selection
 * /copecore regions [plugin]  regions where you stand, or all regions of a plugin
 * /copecore states [player]   shared states (combat, in-arena...)
 * /copecore debug             toggle debug logging
 * </pre>
 */
public final class CoreCommand {

    private final CopeCore core;

    public CoreCommand(CopeCore core) {
        this.core = core;
    }

    public void register() {
        Messages messages = core.coreMessages();
        CopeCommand command = new CopeCommand(core, "copecore", messages);
        String admin = "copecore.admin";

        command.sub("info").permission(admin)
                .descriptionKey("command.help-description.info")
                .executes(ctx -> {
                    messages.sendRaw(ctx.sender(), "command.info-header",
                            "version", core.getDescription().getVersion(),
                            "server", Bukkit.getName() + " " + ServerVersion.display(),
                            "java", System.getProperty("java.version"));
                    List<Plugin> dependents = core.dependents();
                    messages.sendRaw(ctx.sender(), "command.info-plugins", "count", String.valueOf(dependents.size()));
                    if (dependents.isEmpty()) {
                        messages.sendRaw(ctx.sender(), "command.info-no-plugins");
                    }
                    for (Plugin plugin : dependents) {
                        messages.sendRaw(ctx.sender(), "command.info-plugin-line",
                                "plugin", plugin.getName(),
                                "version", plugin.getDescription().getVersion(),
                                "status", plugin.isEnabled() ? "<green>" : "<red>");
                    }
                });

        command.sub("reload").permission(admin)
                .descriptionKey("command.help-description.reload")
                .executes(ctx -> {
                    long start = System.currentTimeMillis();
                    core.reloadCore();
                    messages.send(ctx.sender(), "generic.reloaded", "plugin", core.getName(),
                            "ms", String.valueOf(System.currentTimeMillis() - start));
                });

        command.sub("wand").permission("copecore.wand").playerOnly()
                .descriptionKey("command.help-description.wand")
                .executes(ctx -> CopeCore.regions().giveWand(ctx.player()));

        command.sub("pos1").permission("copecore.wand").playerOnly()
                .descriptionKey("command.help-description.pos1")
                .executes(ctx -> CopeCore.regions().setCorner(ctx.player(), 1, ctx.player().getLocation()));

        command.sub("pos2").permission("copecore.wand").playerOnly()
                .descriptionKey("command.help-description.pos2")
                .executes(ctx -> CopeCore.regions().setCorner(ctx.player(), 2, ctx.player().getLocation()));

        command.sub("selection").aliases("sel").permission("copecore.wand").playerOnly().usage("[clear]")
                .descriptionKey("command.help-description.selection")
                .tab(ctx -> List.of("clear"))
                .executes(ctx -> {
                    Player player = ctx.player();
                    Selection selection = CopeCore.regions().selection(player);
                    if (ctx.has(0) && ctx.arg(0).equalsIgnoreCase("clear")) {
                        selection.clear();
                        messages.send(player, "selection.cleared");
                        return;
                    }
                    if (!selection.isComplete()) {
                        messages.send(player, selection.isDifferentWorlds() ? "selection.different-worlds" : "selection.incomplete");
                        return;
                    }
                    var region = selection.toRegion("selection");
                    messages.send(player, "selection.selection-size",
                            "x", String.valueOf(region.sizeX()), "y", String.valueOf(region.sizeY()),
                            "z", String.valueOf(region.sizeZ()), "volume", String.valueOf(region.volume()));
                    CopeCore.regions().showOutline(player, region);
                });

        command.sub("regions").permission(admin).usage("[plugin]")
                .descriptionKey("command.help-description.regions")
                .tab(ctx -> {
                    List<String> names = new ArrayList<>();
                    for (RegionRef ref : CopeCore.regions().all()) {
                        if (!names.contains(ref.ownerName())) {
                            names.add(ref.ownerName());
                        }
                    }
                    return names;
                })
                .executes(ctx -> {
                    List<RegionRef> found = new ArrayList<>();
                    String where;
                    if (ctx.has(0)) {
                        for (RegionRef ref : CopeCore.regions().all()) {
                            if (ref.ownerName().equalsIgnoreCase(ctx.arg(0))) {
                                found.add(ref);
                            }
                        }
                        where = "from " + ctx.arg(0);
                    } else {
                        found.addAll(CopeCore.regions().at(ctx.player().getLocation()));
                        where = "here";
                    }
                    if (found.isEmpty()) {
                        messages.send(ctx.sender(), "command.regions-none", "where", where);
                        return;
                    }
                    messages.send(ctx.sender(), "command.regions-header", "count", String.valueOf(found.size()), "where", where);
                    for (RegionRef ref : found) {
                        messages.sendRaw(ctx.sender(), "command.regions-line",
                                "plugin", ref.ownerName(), "region", ref.name(), "description", ref.region().describe());
                    }
                });

        command.sub("states").permission(admin).usage("[player]")
                .descriptionKey("command.help-description.states")
                .tab(ctx -> {
                    List<String> names = new ArrayList<>();
                    Bukkit.getOnlinePlayers().forEach(player -> names.add(player.getName()));
                    return names;
                })
                .executes(ctx -> {
                    Player target = ctx.has(0) ? ctx.onlinePlayer(0) : ctx.player();
                    Map<String, Long> states = CopeCore.states().all(target);
                    if (states.isEmpty()) {
                        messages.send(ctx.sender(), "command.states-none", "player", target.getName());
                        return;
                    }
                    messages.send(ctx.sender(), "command.states-header", "player", target.getName());
                    states.forEach((state, millis) -> messages.sendRaw(ctx.sender(), "command.states-line",
                            "state", state, "time", millis < 0 ? "∞" : TimeFormat.duration(millis)));
                });

        command.sub("debug").permission(admin)
                .descriptionKey("command.help-description.debug")
                .executes(ctx -> {
                    boolean now = !core.coreConfig().get().getBoolean("debug", false);
                    core.coreConfig().get().set("debug", now);
                    core.coreConfig().save();
                    messages.send(ctx.sender(), "command.debug-toggled", "state", now ? "on" : "off");
                });

        command.register();
    }
}
