package us.copestudios.copecore.prompt;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import us.copestudios.copecore.CopeCore;
import us.copestudios.copecore.scheduler.Tasks;
import us.copestudios.copecore.text.Messages;
import us.copestudios.copecore.text.Text;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.logging.Level;

/**
 * "Type the answer in chat" questions, used by every editor (KOTH settings, boss editor, arena setup, giveaways).
 *
 * <h2>Why this exists</h2>
 * <p>Chat messages arrive on a separate thread. CSKoth and CSSPVP created KOTHs/arenas and saved files directly
 * inside the chat event, which is not allowed and can corrupt data. Here, the answer is captured on the chat
 * thread but your code ALWAYS runs on the main thread, so you can safely touch players, worlds and files.</p>
 *
 * <h2>What you get</h2>
 * <ul>
 *   <li>The player's menu is closed before the question, and the answer is hidden from public chat.</li>
 *   <li>"cancel" (or any word from config.yml) cancels; your {@code onCancel} runs (usually re-opening the menu).</li>
 *   <li>Invalid input (a word where a number was expected) asks again instead of failing.</li>
 *   <li>Unanswered questions time out (config.yml: prompts.timeout-seconds).</li>
 *   <li>Quitting the server, or the plugin being disabled, cleans up the question.</li>
 * </ul>
 *
 * <h2>Examples</h2>
 * <pre>{@code
 * ChatPrompts prompts = CopeCore.prompts();
 *
 * prompts.text(player, "<yellow>Name of the new KOTH?",
 *         name -> koths.create(name),         // main thread
 *         () -> menu.open());                 // cancelled / timed out
 *
 * prompts.integer(player, "<yellow>Capture time in seconds?", 10, 3600,
 *         seconds -> { koth.setCaptureTime(seconds); menu.open(); },
 *         menu::open);
 *
 * prompts.yesNo(player, "<yellow>Can players drop items in this arena?",
 *         allowed -> arena.setDrops(allowed), menu::open);
 *
 * prompts.here(player, "<yellow>Go to the arena spawn point.",
 *         location -> arena.setSpawn(location), menu::open);
 * }</pre>
 */
public final class ChatPrompts implements Listener {

    /** One waiting question. */
    private static final class Pending {
        final Plugin owner;
        final String question;
        /** Returns null if the answer was accepted, or an error message (MiniMessage) to show before asking again. */
        final Function<String, String> handler;
        final Runnable onCancel;
        BukkitTask timeout;

        Pending(Plugin owner, String question, Function<String, String> handler, Runnable onCancel) {
            this.owner = owner;
            this.question = question;
            this.handler = handler;
            this.onCancel = onCancel;
        }
    }

    private final CopeCore core;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public ChatPrompts(CopeCore core) {
        this.core = core;
    }

    // =====================================================================
    //  Asking
    // =====================================================================

    /**
     * Asks for any text (a name, a command, a message...). The text is passed exactly as typed.
     * Remember to use {@link Text#escape(String)} if you later show it inside another message.
     */
    public void text(Player player, String question, Consumer<String> onAnswer, Runnable onCancel) {
        ask(callingPlugin(), player, question, input -> {
            onAnswer.accept(input);
            return null;
        }, onCancel);
    }

    /** Asks for a whole number between min and max (inclusive). */
    public void integer(Player player, String question, int min, int max, IntConsumer onAnswer, Runnable onCancel) {
        ask(callingPlugin(), player, question, input -> {
            try {
                int value = Integer.parseInt(input.trim());
                if (value < min || value > max) {
                    return rangeError(min, max);
                }
                onAnswer.accept(value);
                return null;
            } catch (NumberFormatException notANumber) {
                return rangeError(min, max);
            }
        }, onCancel);
    }

    /**
     * Asks for a number between min and max. Accepts "1.5" and "1,5".
     *
     * @param wholeNumber round the answer to a whole number
     */
    public void decimal(Player player, String question, double min, double max, boolean wholeNumber,
                        DoubleConsumer onAnswer, Runnable onCancel) {
        ask(callingPlugin(), player, question, input -> {
            try {
                double value = Double.parseDouble(input.trim().replace(',', '.'));
                if (Double.isNaN(value) || Double.isInfinite(value) || value < min || value > max) {
                    return rangeError(trim(min), trim(max));
                }
                onAnswer.accept(wholeNumber ? Math.round(value) : value);
                return null;
            } catch (NumberFormatException notANumber) {
                return rangeError(trim(min), trim(max));
            }
        }, onCancel);
    }

    /** Asks a yes/no question. Accepts yes/no, y/n, true/false, si/sí. */
    public void yesNo(Player player, String question, Consumer<Boolean> onAnswer, Runnable onCancel) {
        ask(callingPlugin(), player, question, input -> {
            String answer = input.trim().toLowerCase(Locale.ROOT);
            switch (answer) {
                case "yes", "y", "true", "si", "sí", "on" -> onAnswer.accept(true);
                case "no", "n", "false", "off" -> onAnswer.accept(false);
                default -> {
                    return messages().format("prompt.invalid-yes-no");
                }
            }
            return null;
        }, onCancel);
    }

    /**
     * Asks the player to walk to a spot and type "here" (config.yml: prompts.here-words).
     * The player's location at that moment is passed on. Used for arena corners, spawns, KOTH points...
     */
    public void here(Player player, String question, Consumer<Location> onAnswer, Runnable onCancel) {
        String hereWord = firstWord("prompts.here-words", "here");
        String fullQuestion = question + "\n" + messages().format("prompt.here-hint", "here", hereWord);
        ask(callingPlugin(), player, fullQuestion, input -> {
            if (!isWord(input, "prompts.here-words", "here")) {
                return messages().format("prompt.invalid-here", "here", hereWord,
                        "cancel", firstWord("prompts.cancel-words", "cancel"));
            }
            // We're on the main thread here, so reading the location is safe.
            onAnswer.accept(player.getLocation().clone());
            return null;
        }, onCancel);
    }

    /** True if the player has an unanswered question. */
    public boolean isWaiting(Player player) {
        return pending.containsKey(player.getUniqueId());
    }

    /** Cancels the player's question (runs its onCancel). */
    public void cancel(Player player) {
        Pending removed = pending.remove(player.getUniqueId());
        if (removed != null) {
            stopTimeout(removed);
            runSafely(removed, removed.onCancel);
        }
    }

    // =====================================================================
    //  Core logic
    // =====================================================================

    /**
     * The general form behind all the methods above.
     *
     * @param handler gets the typed text on the main thread; returns null if accepted, or a message to show
     *                before asking again
     */
    public void ask(Plugin owner, Player player, String question, Function<String, String> handler, Runnable onCancel) {
        Runnable cancel = onCancel == null ? () -> { } : onCancel;

        // Replace any question that was still open, without running its cancel action.
        Pending previous = pending.remove(player.getUniqueId());
        if (previous != null) {
            stopTimeout(previous);
        }

        Pending prompt = new Pending(owner, question, handler, cancel);
        pending.put(player.getUniqueId(), prompt);
        startTimeout(player, prompt);

        // Close the menu and ask on the next tick (safe even when called from inside a menu click).
        Tasks.sync(core, () -> {
            player.closeInventory();
            sendQuestion(player, prompt);
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        Pending prompt = pending.get(player.getUniqueId());
        if (prompt == null) {
            return;
        }
        // Hide the answer from other players.
        event.setCancelled(true);
        String message = event.getMessage();

        // Everything else happens on the main thread.
        Tasks.sync(core, () -> {
            if (pending.get(player.getUniqueId()) != prompt) {
                return; // answered/cancelled already (e.g. two quick messages)
            }
            if (isWord(message, "prompts.cancel-words", "cancel")) {
                pending.remove(player.getUniqueId());
                stopTimeout(prompt);
                messages().send(player, "prompt.cancelled");
                runSafely(prompt, prompt.onCancel);
                return;
            }

            String error;
            try {
                error = prompt.handler.apply(message);
            } catch (RuntimeException failure) {
                prompt.owner.getLogger().log(Level.SEVERE, "Error handling a chat prompt answer", failure);
                error = null; // don't trap the player in a loop on a plugin bug
            }

            if (error == null) {
                // Accepted. Only remove it if the handler didn't already start a NEW question.
                if (pending.get(player.getUniqueId()) == prompt) {
                    pending.remove(player.getUniqueId());
                }
                stopTimeout(prompt);
            } else {
                Text.send(player, error);
                sendQuestion(player, prompt);
                startTimeout(player, prompt); // fresh time to answer
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Pending removed = pending.remove(event.getPlayer().getUniqueId());
        if (removed != null) {
            stopTimeout(removed);
        }
    }

    /** Questions from a plugin that is being disabled are dropped (their actions would fail anyway). */
    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        pending.entrySet().removeIf(entry -> {
            if (entry.getValue().owner.equals(event.getPlugin())) {
                stopTimeout(entry.getValue());
                return true;
            }
            return false;
        });
    }

    // =====================================================================
    //  Helpers
    // =====================================================================

    private void sendQuestion(Player player, Pending prompt) {
        Messages messages = messages();
        for (String line : prompt.question.split("\n")) {
            messages.sendText(player, line);
        }
        messages.sendRaw(player, "prompt.hint", "cancel", firstWord("prompts.cancel-words", "cancel"));
    }

    private void startTimeout(Player player, Pending prompt) {
        stopTimeout(prompt);
        int seconds = core.coreConfig().get().getInt("prompts.timeout-seconds", 60);
        if (seconds <= 0) {
            return;
        }
        prompt.timeout = Tasks.laterSeconds(core, seconds, () -> {
            if (pending.remove(player.getUniqueId(), prompt)) {
                if (player.isOnline()) {
                    messages().send(player, "prompt.timed-out");
                }
                runSafely(prompt, prompt.onCancel);
            }
        });
    }

    private static void stopTimeout(Pending prompt) {
        if (prompt.timeout != null) {
            prompt.timeout.cancel();
            prompt.timeout = null;
        }
    }

    private void runSafely(Pending prompt, Runnable action) {
        if (!prompt.owner.isEnabled()) {
            return;
        }
        try {
            action.run();
        } catch (RuntimeException error) {
            prompt.owner.getLogger().log(Level.SEVERE, "Error in a chat prompt cancel action", error);
        }
    }

    private boolean isWord(String input, String configList, String fallback) {
        String typed = input.trim().toLowerCase(Locale.ROOT);
        List<String> words = core.coreConfig().get().getStringList(configList);
        if (words.isEmpty()) {
            return typed.equals(fallback);
        }
        for (String word : words) {
            if (typed.equals(word.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String firstWord(String configList, String fallback) {
        List<String> words = core.coreConfig().get().getStringList(configList);
        return words.isEmpty() ? fallback : words.get(0);
    }

    private String rangeError(Object min, Object max) {
        return messages().format("prompt.invalid-number", "min", String.valueOf(min), "max", String.valueOf(max));
    }

    private static String trim(double value) {
        return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private Messages messages() {
        return core.coreMessages();
    }

    /**
     * Works out which plugin asked the question (used to drop questions when that plugin is disabled).
     * Falls back to CopeCore itself if it can't tell.
     */
    private Plugin callingPlugin() {
        try {
            Class<?> caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                    .walk(frames -> frames
                            .map(StackWalker.StackFrame::getDeclaringClass)
                            .filter(type -> !type.getName().startsWith("us.copestudios.copecore."))
                            .findFirst()
                            .orElse(null));
            if (caller != null) {
                for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
                    if (plugin.getClass().getClassLoader() == caller.getClassLoader()) {
                        return plugin;
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // fall through
        }
        return core;
    }
}
