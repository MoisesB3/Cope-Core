package us.copestudios.copecore.player;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import us.copestudios.copecore.CopeCore;
import us.copestudios.copecore.event.PlayerStateChangeEvent;
import us.copestudios.copecore.scheduler.Tasks;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared "what is this player doing right now?" flags, readable by EVERY Cope plugin.
 * Get it with {@code CopeCore.states()}.
 *
 * <h2>Why</h2>
 * <p>Plugins need to know about each other without depending on each other. With states:</p>
 * <ul>
 *   <li>CSCombat sets {@code combat} for 15 seconds when someone is hit.</li>
 *   <li>CZRTP refuses to random-teleport anyone with {@code combat}.</li>
 *   <li>CSSPVP sets {@code in-arena} while a player is in an arena; Cope Bosses won't teleport them to a boss.</li>
 *   <li>Cope Nights checks {@code in-duel} before playing a jump scare.</li>
 * </ul>
 * <p>None of these plugins need each other's code - they only agree on the state names below.</p>
 *
 * <h2>Examples</h2>
 * <pre>{@code
 * PlayerStates states = CopeCore.states();
 *
 * states.set(player, PlayerStates.COMBAT, 15_000);   // 15 seconds, ends by itself
 * states.set(player, PlayerStates.IN_ARENA);          // until removed
 * states.remove(player, PlayerStates.IN_ARENA);
 *
 * if (states.has(player, PlayerStates.COMBAT)) {
 *     messages.send(player, "rtp.in-combat", "time", states.remainingText(player, PlayerStates.COMBAT));
 *     return;
 * }
 * }</pre>
 *
 * <p>Safe to use from any thread (events are always fired on the main thread).
 * States live in memory and reset on restart. Every start and end fires a {@link PlayerStateChangeEvent}.
 * Timed states are checked twice a second, so the "ended" event fires within half a second of expiring.</p>
 */
public final class PlayerStates {

    // ----- Agreed state names. Use these constants so a typo can't break the link between plugins. -----

    /** Player was hit in PvP recently (set by CSCombat). */
    public static final String COMBAT = "combat";
    /** Player is inside an arena (CSSPVP). */
    public static final String IN_ARENA = "in-arena";
    /** Player is in a duel (CSSPVP / CSCommands). */
    public static final String IN_DUEL = "in-duel";
    /** Player is fighting a boss (Cope Bosses). */
    public static final String BOSS_FIGHT = "boss-fight";
    /** Player is waiting for a delayed teleport (CZRTP). */
    public static final String TELEPORTING = "teleporting";
    /** Player is answering a chat prompt or using an editor menu. */
    public static final String EDITING = "editing";

    /** Value used for states without an end time. */
    private static final long FOREVER = Long.MAX_VALUE;

    private record Entry(long endsAt, String setBy) {
    }

    private final CopeCore core;
    /** player -> state name -> entry */
    private final Map<UUID, Map<String, Entry>> states = new ConcurrentHashMap<>();

    public PlayerStates(CopeCore core) {
        this.core = core;
        // Expire timed states (and fire their "ended" events) twice per second.
        Tasks.timer(core, 10, 10, this::expire);
    }

    /** Adds a state that lasts until {@link #remove} is called. */
    public void set(Player player, String state) {
        set(player, state, -1);
    }

    /**
     * Adds a state that ends by itself after {@code durationMillis}. Setting it again restarts the timer
     * (that's how combat tags refresh on every hit).
     *
     * @param durationMillis time in milliseconds, or -1 for "until removed"
     */
    public void set(Player player, String state, long durationMillis) {
        String name = normalize(state);
        long endsAt = durationMillis < 0 ? FOREVER : System.currentTimeMillis() + durationMillis;
        Map<String, Entry> playerStates = states.computeIfAbsent(player.getUniqueId(), id -> new ConcurrentHashMap<>());
        boolean isNew = !isActive(playerStates.get(name));
        playerStates.put(name, new Entry(endsAt, callerName()));
        if (isNew) {
            fire(player, name, true, playerStates.get(name).setBy());
        }
    }

    /** Removes a state. Returns false if the player didn't have it. */
    public boolean remove(Player player, String state) {
        Map<String, Entry> playerStates = states.get(player.getUniqueId());
        if (playerStates == null) {
            return false;
        }
        Entry removed = playerStates.remove(normalize(state));
        if (removed != null && isActive(removed)) {
            fire(player, normalize(state), false, removed.setBy());
            return true;
        }
        return false;
    }

    /** True if the player currently has the state. */
    public boolean has(Player player, String state) {
        Map<String, Entry> playerStates = states.get(player.getUniqueId());
        return playerStates != null && isActive(playerStates.get(normalize(state)));
    }

    /** True if the player has ANY of these states (e.g. "is busy?"). */
    public boolean hasAny(Player player, String... names) {
        for (String state : names) {
            if (has(player, state)) {
                return true;
            }
        }
        return false;
    }

    /** Milliseconds left, -1 for "until removed", or 0 if the player doesn't have it. */
    public long remainingMillis(Player player, String state) {
        Map<String, Entry> playerStates = states.get(player.getUniqueId());
        Entry entry = playerStates == null ? null : playerStates.get(normalize(state));
        if (!isActive(entry)) {
            return 0;
        }
        return entry.endsAt() == FOREVER ? -1 : entry.endsAt() - System.currentTimeMillis();
    }

    /** Time left as text ("12s", "1m 5s") or "∞" for states without an end. */
    public String remainingText(Player player, String state) {
        long left = remainingMillis(player, state);
        return left < 0 ? "∞" : us.copestudios.copecore.util.TimeFormat.duration(left);
    }

    /** Every active state of the player with its remaining milliseconds (-1 = until removed). */
    public Map<String, Long> all(Player player) {
        Map<String, Entry> playerStates = states.get(player.getUniqueId());
        if (playerStates == null) {
            return Collections.emptyMap();
        }
        Map<String, Long> result = new LinkedHashMap<>();
        for (String state : playerStates.keySet()) {
            if (has(player, state)) {
                result.put(state, remainingMillis(player, state));
            }
        }
        return result;
    }

    /** Removes every state of a player (fires the "ended" events). Called automatically on logout. */
    public void clear(Player player) {
        Map<String, Entry> playerStates = states.remove(player.getUniqueId());
        if (playerStates == null) {
            return;
        }
        for (Map.Entry<String, Entry> entry : playerStates.entrySet()) {
            if (isActive(entry.getValue())) {
                fire(player, entry.getKey(), false, entry.getValue().setBy());
            }
        }
    }

    // ---------------------------------------------------------------------

    private void expire() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Map<String, Entry> playerStates = states.get(player.getUniqueId());
            if (playerStates == null) {
                continue;
            }
            playerStates.entrySet().removeIf(entry -> {
                if (entry.getValue().endsAt() <= now) {
                    fire(player, entry.getKey(), false, entry.getValue().setBy());
                    return true;
                }
                return false;
            });
        }
    }

    private static boolean isActive(Entry entry) {
        return entry != null && entry.endsAt() > System.currentTimeMillis();
    }

    private void fire(Player player, String state, boolean started, String setBy) {
        if (Bukkit.isPrimaryThread()) {
            Bukkit.getPluginManager().callEvent(new PlayerStateChangeEvent(player, state, started, setBy));
        } else {
            Tasks.sync(core, () -> Bukkit.getPluginManager().callEvent(new PlayerStateChangeEvent(player, state, started, setBy)));
        }
    }

    private static String normalize(String state) {
        return state.trim().toLowerCase(Locale.ROOT);
    }

    /** Name of the plugin that called set(), for debugging ("who put me in combat?"). */
    private static String callerName() {
        try {
            Class<?> caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                    .walk(frames -> frames.map(StackWalker.StackFrame::getDeclaringClass)
                            .filter(type -> !type.getName().startsWith("us.copestudios.copecore."))
                            .findFirst().orElse(null));
            if (caller != null) {
                for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
                    if (plugin.getClass().getClassLoader() == caller.getClassLoader()) {
                        return plugin.getName();
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // fall through
        }
        return "unknown";
    }
}
