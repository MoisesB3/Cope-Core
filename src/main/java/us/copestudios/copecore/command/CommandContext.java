package us.copestudios.copecore.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import us.copestudios.copecore.text.Messages;

import java.util.Arrays;

/**
 * Everything a sub command needs: who ran it, the arguments, and helpers that read arguments
 * safely. The helpers throw {@link CommandFailure} with a friendly message when something is wrong,
 * so your command code can stay short.
 *
 * <pre>{@code
 * // /koth settime <koth> <seconds>
 * command.sub("settime")
 *         .usage("<koth> <seconds>").minArgs(2).permission("cskoth.admin")
 *         .executes(ctx -> {
 *             String koth = ctx.arg(0);
 *             int seconds = ctx.integer(1, 10, 3600);   // shows an error and stops if invalid
 *             ...
 *             ctx.reply("koth.time-set", "koth", koth, "time", String.valueOf(seconds));
 *         });
 * }</pre>
 *
 * <p>Note: {@code arg(0)} is the first argument AFTER the sub command name. For "/koth settime castle 300",
 * {@code arg(0)} is "castle" and {@code arg(1)} is "300".</p>
 */
public final class CommandContext {

    private final CommandSender sender;
    private final String label;
    private final String subLabel;
    private final String[] args;
    private final Messages messages;

    CommandContext(CommandSender sender, String label, String subLabel, String[] args, Messages messages) {
        this.sender = sender;
        this.label = label;
        this.subLabel = subLabel;
        this.args = args;
        this.messages = messages;
    }

    /** Whoever ran the command (a player, the console, or a command block). */
    public CommandSender sender() {
        return sender;
    }

    /** True if a player ran the command. */
    public boolean isPlayer() {
        return sender instanceof Player;
    }

    /**
     * The player who ran the command. If the console ran it, the command stops with "players only".
     * (You usually don't need this check yourself: use {@code .playerOnly()} on the sub command.)
     */
    public Player player() {
        if (sender instanceof Player player) {
            return player;
        }
        throw new CommandFailure("generic.players-only");
    }

    /** The command label the player typed (could be an alias, e.g. "koth" instead of "cskoth"). */
    public String label() {
        return label;
    }

    /** The sub command name the player typed (could be an alias). Empty for the default action. */
    public String subLabel() {
        return subLabel;
    }

    /** All arguments after the sub command name. */
    public String[] args() {
        return args.clone();
    }

    /** Number of arguments after the sub command name. */
    public int size() {
        return args.length;
    }

    /** True if argument {@code index} was given. */
    public boolean has(int index) {
        return index >= 0 && index < args.length;
    }

    /** Argument {@code index}. Stops the command with the usage message if it's missing. */
    public String arg(int index) {
        if (!has(index)) {
            throw new CommandFailure(UsageFailure.KEY);
        }
        return args[index];
    }

    /** Argument {@code index}, or {@code fallback} when it wasn't given. */
    public String argOr(int index, String fallback) {
        return has(index) ? args[index] : fallback;
    }

    /** All arguments from {@code index} to the end joined with spaces (for names or messages with spaces). */
    public String joined(int fromIndex) {
        if (!has(fromIndex)) {
            throw new CommandFailure(UsageFailure.KEY);
        }
        return String.join(" ", Arrays.copyOfRange(args, fromIndex, args.length));
    }

    /** Argument {@code index} as a whole number between min and max (inclusive). */
    public int integer(int index, int min, int max) {
        String text = arg(index);
        int value;
        try {
            value = Integer.parseInt(text.trim());
        } catch (NumberFormatException notANumber) {
            throw new CommandFailure("generic.not-a-number", "input", text);
        }
        if (value < min || value > max) {
            throw new CommandFailure("generic.number-out-of-range", "min", String.valueOf(min), "max", String.valueOf(max));
        }
        return value;
    }

    /** Argument {@code index} as a decimal number between min and max. Accepts "1.5" and "1,5". */
    public double decimal(int index, double min, double max) {
        String text = arg(index);
        double value;
        try {
            value = Double.parseDouble(text.trim().replace(',', '.'));
        } catch (NumberFormatException notANumber) {
            throw new CommandFailure("generic.not-a-number", "input", text);
        }
        if (Double.isNaN(value) || value < min || value > max) {
            throw new CommandFailure("generic.number-out-of-range", "min", String.valueOf(min), "max", String.valueOf(max));
        }
        return value;
    }

    /** Argument {@code index} as an online player (exact name first, then a unique partial match). */
    public Player onlinePlayer(int index) {
        String name = arg(index);
        Player exact = Bukkit.getPlayerExact(name);
        Player found = exact != null ? exact : Bukkit.getPlayer(name);
        if (found == null) {
            throw new CommandFailure("generic.player-not-found", "input", name);
        }
        return found;
    }

    /** Sends a message from the plugin's messages.yml (with prefix) to whoever ran the command. */
    public void reply(String key, String... pairs) {
        messages.send(sender, key, pairs);
    }

    /** Sends text that is built in code (with prefix). Prefer {@link #reply} for anything configurable. */
    public void replyText(String text, String... pairs) {
        messages.sendText(sender, text, pairs);
    }

    /** The plugin's messages, for anything more advanced than {@link #reply}. */
    public Messages messages() {
        return messages;
    }

    /** Marker used when an argument is missing: the framework turns it into the sub command's usage line. */
    static final class UsageFailure {
        static final String KEY = "__copecore_usage__";

        private UsageFailure() {
        }
    }
}
