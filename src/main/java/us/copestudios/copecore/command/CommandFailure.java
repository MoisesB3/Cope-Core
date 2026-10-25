package us.copestudios.copecore.command;

/**
 * Throw this inside a command to stop it and show the player a message from messages.yml.
 *
 * <pre>{@code
 * if (!koths.exists(name)) {
 *     throw new CommandFailure("koth.not-found", "koth", name);
 * }
 * }</pre>
 *
 * <p>It's much tidier than "send message + return true" after every check. The command framework catches it
 * and sends the message with the plugin's prefix. It is not an error, so nothing is logged.</p>
 */
public final class CommandFailure extends RuntimeException {

    private final String messageKey;
    private final String[] pairs;

    /**
     * @param messageKey key in the plugin's messages.yml (or CopeCore's, e.g. "generic.no-permission")
     * @param pairs      placeholder name/value pairs
     */
    public CommandFailure(String messageKey, String... pairs) {
        // No stack trace: this is normal control flow, not a crash, so building one would be wasted work.
        super(messageKey, null, false, false);
        this.messageKey = messageKey;
        this.pairs = pairs;
    }

    public String messageKey() {
        return messageKey;
    }

    public String[] pairs() {
        return pairs;
    }
}
