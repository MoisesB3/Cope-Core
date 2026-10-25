package us.copestudios.copecore.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import us.copestudios.copecore.text.Messages;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * A main command with sub commands, permissions, usage messages, a help page and tab completion - all
 * handled for you. Replaces the big "switch (args[0])" blocks every plugin had.
 *
 * <h2>Example (inside onEnable)</h2>
 * <pre>{@code
 * Messages messages = CopeCore.messages(this);
 * CopeCommand koth = new CopeCommand(this, "cskoth", messages);   // "cskoth" must be in plugin.yml
 *
 * koth.defaultAction(ctx -> gui.openMain(ctx.player()));           // "/koth" with nothing after it
 *
 * koth.sub("start").permission("cskoth.admin").usage("<koth>").minArgs(1)
 *         .description("Start a KOTH event")
 *         .tab(ctx -> new ArrayList<>(manager.names()))
 *         .executes(ctx -> manager.start(ctx.sender(), ctx.arg(0)));
 *
 * koth.sub("reload").permission("cskoth.admin").description("Reload the config")
 *         .executes(ctx -> reload(ctx.sender()));
 *
 * koth.register();
 * }</pre>
 *
 * <p>"/koth help" is added automatically and only lists the sub commands the player is allowed to use.
 * The shared texts (no permission, usage, help layout) come from CopeCore's messages.yml under
 * {@code generic.*}, and each plugin can override them in its own messages.yml.</p>
 */
public class CopeCommand implements TabExecutor {

    private final JavaPlugin plugin;
    private final String name;
    private final Messages messages;
    /** Sub commands in the order they were added (that's also the help page order). */
    private final Map<String, SubCommand> subs = new LinkedHashMap<>();
    /** What "/command" does with no arguments. Shows the help page unless changed. */
    private Consumer<CommandContext> defaultAction;
    /** Optional: handles "/command <something that isn't a sub command>" (see {@link #fallback()}). */
    private SubCommand fallback;

    /**
     * @param plugin   your plugin
     * @param name     the command name exactly as written in your plugin.yml
     * @param messages your plugin's messages ({@code CopeCore.messages(this)})
     */
    public CopeCommand(JavaPlugin plugin, String name, Messages messages) {
        this.plugin = plugin;
        this.name = name;
        this.messages = messages;
        this.defaultAction = ctx -> sendHelp(ctx.sender(), ctx.label());
        // Built-in help page.
        sub("help").aliases("?").descriptionKey("generic.help-command")
                .description("Show this list")
                .executes(ctx -> sendHelp(ctx.sender(), ctx.label()));
    }

    /** Adds a sub command and returns it so you can set it up. */
    public final SubCommand sub(String subName) {
        SubCommand sub = new SubCommand(subName);
        subs.put(sub.name(), sub);
        return sub;
    }

    /**
     * Handles a first argument that is NOT a sub command name, for commands shaped like
     * "/coni &lt;player&gt; [type]" or "/rtp &lt;zone&gt;". Set it up like a normal sub command; inside it,
     * {@code ctx.arg(0)} is the first word the player typed. It's listed first on the help page.
     *
     * <pre>{@code
     * command.fallback()
     *         .usage("<player|all|random> [type]").description("Scare someone").minArgs(1)
     *         .tab(ctx -> ctx.size() == 1 ? onlineNames() : scareTypes())
     *         .executes(ctx -> scare(ctx.sender(), ctx.arg(0), ctx.argOr(1, "random")));
     * }</pre>
     *
     * <p>Without a fallback, an unknown first word shows the "unknown command" message. (Added in API 2.)</p>
     */
    public final SubCommand fallback() {
        if (fallback == null) {
            fallback = new SubCommand("");
        }
        return fallback;
    }

    /** What happens when the command is typed with no arguments (for example, opening a menu). */
    public CopeCommand defaultAction(Consumer<CommandContext> action) {
        this.defaultAction = action;
        return this;
    }

    /** Connects this command to the server. Call once, after adding the sub commands. */
    public void register() {
        PluginCommand command = plugin.getCommand(name);
        if (command == null) {
            plugin.getLogger().severe("Command '" + name + "' is missing from plugin.yml, so it can't be registered.");
            return;
        }
        command.setExecutor(this);
        command.setTabCompleter(this);
    }

    /** All sub commands (read only), e.g. to build a GUI listing them. */
    public List<SubCommand> subCommands() {
        return Collections.unmodifiableList(new ArrayList<>(subs.values()));
    }

    // =====================================================================
    //  Running
    // =====================================================================

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            run(sender, label, "", new String[0], null, defaultAction);
            return true;
        }

        SubCommand sub = find(args[0]);
        String[] rest;
        if (sub != null) {
            rest = Arrays.copyOfRange(args, 1, args.length);
        } else if (fallback != null) {
            // Not a sub command name: the fallback gets ALL the arguments.
            sub = fallback;
            rest = args;
        } else {
            messages.send(sender, "generic.unknown-subcommand", "input", args[0], "label", label);
            return true;
        }

        if (sub.permission() != null && !sender.hasPermission(sub.permission())) {
            messages.send(sender, "generic.no-permission");
            return true;
        }
        if (sub.isPlayerOnly() && !(sender instanceof Player)) {
            messages.send(sender, "generic.players-only");
            return true;
        }
        if (rest.length < sub.minArgs()) {
            sendUsage(sender, label, sub);
            return true;
        }
        run(sender, label, sub == fallback ? "" : args[0], rest, sub, sub.executor());
        return true;
    }

    /** Runs a command body, turning CommandFailure into a message and real errors into a log entry. */
    private void run(CommandSender sender, String label, String subLabel, String[] args,
                     SubCommand sub, Consumer<CommandContext> body) {
        CommandContext ctx = new CommandContext(sender, label, subLabel, args, messages);
        try {
            body.accept(ctx);
        } catch (CommandFailure failure) {
            if (CommandContext.UsageFailure.KEY.equals(failure.messageKey())) {
                if (sub != null) {
                    sendUsage(sender, label, sub);
                } else {
                    sendHelp(sender, label);
                }
            } else {
                messages.send(sender, failure.messageKey(), failure.pairs());
            }
        } catch (RuntimeException error) {
            plugin.getLogger().log(Level.SEVERE,
                    "Error while running /" + label + " " + String.join(" ", args), error);
            messages.send(sender, "generic.error");
        }
    }

    private void sendUsage(CommandSender sender, String label, SubCommand sub) {
        String usage = "/" + label + (sub.name().isEmpty() ? "" : " " + sub.name())
                + (sub.usage().isEmpty() ? "" : " " + sub.usage());
        messages.send(sender, "generic.usage", "usage", escapeAngles(usage));
    }

    /**
     * Sends the help page: header, one line per sub command the sender may use, footer.
     * You can call this yourself, e.g. from a "/koth" default action that also shows help.
     */
    public final void sendHelp(CommandSender sender, String label) {
        messages.sendRaw(sender, "generic.help-header",
                "plugin", plugin.getName(), "version", plugin.getDescription().getVersion());
        List<SubCommand> lines = new ArrayList<>();
        if (fallback != null) {
            lines.add(fallback);
        }
        lines.addAll(subs.values());
        for (SubCommand sub : lines) {
            if (sub.isHidden() || !canUse(sender, sub)) {
                continue;
            }
            String description = sub.descriptionKey() != null && messages.has(sub.descriptionKey())
                    ? messages.format(sub.descriptionKey())
                    : sub.description();
            messages.sendRaw(sender, "generic.help-line",
                    "command", label + (sub.name().isEmpty() ? "" : " " + sub.name()),
                    "usage", escapeAngles(sub.usage()),
                    "description", description);
        }
        messages.sendRaw(sender, "generic.help-footer");
    }

    // =====================================================================
    //  Tab completion
    // =====================================================================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        // Typing the sub command name: suggest the ones this sender may use.
        if (args.length <= 1) {
            String typed = args.length == 0 ? "" : args[0];
            List<String> names = new ArrayList<>();
            for (SubCommand sub : subs.values()) {
                if (!sub.isHidden() && canUse(sender, sub)) {
                    names.add(sub.name());
                }
            }
            // The fallback's own suggestions for the first word (player names, zones...).
            if (fallback != null && canUse(sender, fallback)) {
                names.addAll(suggest(fallback, sender, label, args.length == 0 ? new String[]{""} : args));
            }
            return filter(names, typed);
        }

        // Typing an argument: ask the sub command (or the fallback, which sees all arguments).
        SubCommand sub = find(args[0]);
        String[] rest;
        if (sub != null) {
            rest = Arrays.copyOfRange(args, 1, args.length);
        } else if (fallback != null) {
            sub = fallback;
            rest = args;
        } else {
            return Collections.emptyList();
        }
        if (!canUse(sender, sub)) {
            return Collections.emptyList();
        }
        try {
            List<String> options = sub.tabCompleter().apply(new CommandContext(sender, label, args[0], rest, messages));
            return filter(options == null ? Collections.emptyList() : options, rest[rest.length - 1]);
        } catch (RuntimeException error) {
            return Collections.emptyList(); // a broken tab completer must never spam errors while typing
        }
    }

    /** A sub command's raw suggestions (unfiltered), never throwing. */
    private List<String> suggest(SubCommand sub, CommandSender sender, String label, String[] args) {
        try {
            List<String> options = sub.tabCompleter().apply(new CommandContext(sender, label, "", args, messages));
            return options == null ? Collections.emptyList() : options;
        } catch (RuntimeException error) {
            return Collections.emptyList();
        }
    }

    // =====================================================================
    //  Helpers
    // =====================================================================

    private SubCommand find(String input) {
        for (SubCommand sub : subs.values()) {
            if (sub.matches(input)) {
                return sub;
            }
        }
        return null;
    }

    private static boolean canUse(CommandSender sender, SubCommand sub) {
        return sub.permission() == null || sender.hasPermission(sub.permission());
    }

    /** Keeps options that start with what was typed (ignoring case). */
    private static List<String> filter(List<String> options, String typed) {
        String start = typed.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option != null && option.toLowerCase(Locale.ROOT).startsWith(start)) {
                result.add(option);
            }
        }
        return result;
    }

    /**
     * Usage text like "&lt;koth&gt;" would be read as a MiniMessage tag and disappear. Escaping the "&lt;"
     * makes it show up literally.
     */
    private static String escapeAngles(String text) {
        return text.replace("<", "\\<");
    }
}
