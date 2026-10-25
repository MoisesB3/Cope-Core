package us.copestudios.copecore.player;

import us.copestudios.copecore.util.TimeFormat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player cooldowns, identified by a name you choose ("rtp:desert", "enderpearl", "kit:daily").
 * Create one per plugin and keep it in a field.
 *
 * <pre>{@code
 * private final Cooldowns cooldowns = new Cooldowns();
 *
 * // The simplest way: "may they use it? if yes, start the cooldown".
 * if (!cooldowns.tryUse(player.getUniqueId(), "rtp:" + zone, 60_000)) {
 *     messages.send(player, "generic.on-cooldown",
 *             "time", cooldowns.remainingText(player.getUniqueId(), "rtp:" + zone));
 *     return;
 * }
 * }</pre>
 *
 * <p>This is also what CSSPVP's "notch apple delay" and "ender pearl delay" settings were missing.</p>
 *
 * <p>Thread-safe. Expired entries are removed when they're checked, so no cleanup task is needed.
 * Cooldowns are kept in memory only (they reset on restart).</p>
 */
public final class Cooldowns {

    /** player -> (cooldown name -> time it ends, in System.currentTimeMillis()) */
    private final Map<UUID, Map<String, Long>> ends = new ConcurrentHashMap<>();

    /** Starts (or restarts) a cooldown. */
    public void set(UUID player, String name, long durationMillis) {
        if (durationMillis <= 0) {
            clear(player, name);
            return;
        }
        ends.computeIfAbsent(player, id -> new ConcurrentHashMap<>())
                .put(name, System.currentTimeMillis() + durationMillis);
    }

    /** True while the cooldown is still running. */
    public boolean isActive(UUID player, String name) {
        return remainingMillis(player, name) > 0;
    }

    /** Milliseconds left, or 0 if not on cooldown. */
    public long remainingMillis(UUID player, String name) {
        Map<String, Long> playerCooldowns = ends.get(player);
        if (playerCooldowns == null) {
            return 0;
        }
        Long end = playerCooldowns.get(name);
        if (end == null) {
            return 0;
        }
        long left = end - System.currentTimeMillis();
        if (left <= 0) {
            playerCooldowns.remove(name); // expired: tidy up
            if (playerCooldowns.isEmpty()) {
                ends.remove(player);
            }
            return 0;
        }
        return left;
    }

    /** Seconds left, rounded up (shows 1 instead of 0 when 0.3 seconds are left). */
    public int remainingSeconds(UUID player, String name) {
        return (int) ((remainingMillis(player, name) + 999) / 1000);
    }

    /** Time left as readable text, e.g. "1m 5s". */
    public String remainingText(UUID player, String name) {
        return TimeFormat.duration(remainingMillis(player, name));
    }

    /**
     * If the cooldown is NOT running, starts it and returns true ("go ahead").
     * If it IS running, changes nothing and returns false ("not yet").
     */
    public boolean tryUse(UUID player, String name, long durationMillis) {
        if (isActive(player, name)) {
            return false;
        }
        set(player, name, durationMillis);
        return true;
    }

    /** Ends one cooldown early. */
    public void clear(UUID player, String name) {
        Map<String, Long> playerCooldowns = ends.get(player);
        if (playerCooldowns != null) {
            playerCooldowns.remove(name);
        }
    }

    /** Ends every cooldown of a player (for example when they leave an arena). */
    public void clearAll(UUID player) {
        ends.remove(player);
    }

    /** Ends every cooldown of every player. */
    public void reset() {
        ends.clear();
    }
}
