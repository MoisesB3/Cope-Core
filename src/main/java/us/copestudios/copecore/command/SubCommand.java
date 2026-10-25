package us.copestudios.copecore.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * One sub command, like the "start" in "/koth start castle". Created with {@link CopeCommand#sub(String)} and set
 * up by chaining methods:
 *
 * <pre>{@code
 * command.sub("start")
 *         .aliases("begin")
 *         .permission("cskoth.admin")
 *         .usage("<koth>")
 *         .description("Start a KOTH event")
 *         .minArgs(1)
 *         .tab(ctx -> new ArrayList<>(koths.names()))
 *         .executes(ctx -> startKoth(ctx.sender(), ctx.arg(0)));
 * }</pre>
 *
 * <p>The framework checks, in order: permission → players only → enough arguments → runs your code.
 * Each failed check sends the matching message, so your code only runs when everything is fine.</p>
 */
public final class SubCommand {

    private final String name;
    private final List<String> aliases = new ArrayList<>();
    private String permission;
    private String usage = "";
    private String description = "";
    private String descriptionKey;
    private boolean playerOnly;
    private boolean hidden;
    private int minArgs;
    private Consumer<CommandContext> executor = ctx -> { };
    private Function<CommandContext, List<String>> tabCompleter = ctx -> Collections.emptyList();

    SubCommand(String name) {
        this.name = name.toLowerCase(Locale.ROOT);
    }

    // ---------------------------------------------------------------------
    //  Setup (chainable)
    // ---------------------------------------------------------------------

    /** Other names that run the same sub command. */
    public SubCommand aliases(String... names) {
        for (String alias : names) {
            aliases.add(alias.toLowerCase(Locale.ROOT));
        }
        return this;
    }

    /** Permission needed. Without one, everyone who can use the main command can use this. */
    public SubCommand permission(String permission) {
        this.permission = permission;
        return this;
    }

    /** What comes AFTER the sub command name in the help and usage messages, e.g. "&lt;koth&gt; [seconds]". */
    public SubCommand usage(String usage) {
        this.usage = usage == null ? "" : usage;
        return this;
    }

    /** Help text shown on the help page (written in code). */
    public SubCommand description(String description) {
        this.description = description == null ? "" : description;
        return this;
    }

    /** Help text taken from the plugin's messages.yml instead (so it can be translated). */
    public SubCommand descriptionKey(String messageKey) {
        this.descriptionKey = messageKey;
        return this;
    }

    /** Console can't use it (for anything that needs a location or inventory). */
    public SubCommand playerOnly() {
        this.playerOnly = true;
        return this;
    }

    /** Works but isn't listed on the help page or in tab completion (for debug tools). */
    public SubCommand hidden() {
        this.hidden = true;
        return this;
    }

    /** How many arguments are required. Fewer = the usage message is shown. */
    public SubCommand minArgs(int count) {
        this.minArgs = Math.max(0, count);
        return this;
    }

    /** What happens when the sub command runs. */
    public SubCommand executes(Consumer<CommandContext> executor) {
        this.executor = executor;
        return this;
    }

    /**
     * Tab suggestions for the arguments. {@code ctx.size()} tells you which argument is being typed
     * (1 = the first one). Return every option: the framework keeps only those matching what's typed so far.
     */
    public SubCommand tab(Function<CommandContext, List<String>> completer) {
        this.tabCompleter = completer;
        return this;
    }

    // ---------------------------------------------------------------------
    //  Read access (used by CopeCommand)
    // ---------------------------------------------------------------------

    public String name() {
        return name;
    }

    public List<String> aliases() {
        return Collections.unmodifiableList(aliases);
    }

    public String permission() {
        return permission;
    }

    public String usage() {
        return usage;
    }

    String description() {
        return description;
    }

    String descriptionKey() {
        return descriptionKey;
    }

    boolean isPlayerOnly() {
        return playerOnly;
    }

    boolean isHidden() {
        return hidden;
    }

    int minArgs() {
        return minArgs;
    }

    Consumer<CommandContext> executor() {
        return executor;
    }

    Function<CommandContext, List<String>> tabCompleter() {
        return tabCompleter;
    }

    /** True if this name or one of its aliases matches. */
    boolean matches(String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return name.equals(lower) || aliases.contains(lower);
    }
}
