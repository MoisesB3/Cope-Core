package us.copestudios.copecore.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Short, safe helpers around the Bukkit scheduler.
 *
 * <h2>The one rule to remember</h2>
 * <p>Almost everything in Bukkit (players, worlds, blocks, entities, inventories) may ONLY be touched from the
 * main server thread. Some events, like chat, run on a different thread. Touching the world from there causes
 * random errors and corrupted data - this was the bug in CSKoth's and CSSPVP's chat setup.</p>
 * <p>{@link #ensureSync(Plugin, Runnable)} is the fix: "run this on the main thread, now if we already are".</p>
 *
 * <h2>Examples</h2>
 * <pre>{@code
 * Tasks.later(plugin, 20, () -> player.sendMessage("1 second later"));
 * BukkitTask timer = Tasks.timer(plugin, 0, 20, () -> tickKoth());   // every second
 *
 * // Heavy work off the main thread, result back on it:
 * Tasks.asyncThenSync(plugin,
 *         () -> ImageIO.read(file),              // runs async (no Bukkit calls here!)
 *         image -> placePortrait(player, image)); // runs on the main thread
 * }</pre>
 *
 * <p>Every task catches and logs its own errors (with the plugin name), so one bad task never kills a timer
 * silently.</p>
 */
public final class Tasks {

    private Tasks() {
        // static helper only
    }

    /** Runs on the main thread on the next tick. */
    public static BukkitTask sync(Plugin plugin, Runnable task) {
        return Bukkit.getScheduler().runTask(plugin, safe(plugin, task));
    }

    /**
     * Runs on the main thread: immediately if we are already on it, otherwise on the next tick.
     * Use this inside chat events and async callbacks before touching players or the world.
     */
    public static void ensureSync(Plugin plugin, Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            safe(plugin, task).run();
        } else if (plugin.isEnabled()) {
            sync(plugin, task);
        }
    }

    /** Runs on a background thread. Do NOT touch players, worlds or inventories in here. */
    public static BukkitTask async(Plugin plugin, Runnable task) {
        return Bukkit.getScheduler().runTaskAsynchronously(plugin, safe(plugin, task));
    }

    /** Runs on the main thread after a delay. 20 ticks = 1 second. */
    public static BukkitTask later(Plugin plugin, long delayTicks, Runnable task) {
        return Bukkit.getScheduler().runTaskLater(plugin, safe(plugin, task), delayTicks);
    }

    /** Same as {@link #later} but in seconds, which is easier to read. */
    public static BukkitTask laterSeconds(Plugin plugin, double seconds, Runnable task) {
        return later(plugin, Math.max(0, Math.round(seconds * 20)), task);
    }

    /**
     * Repeats on the main thread. Keep the returned task and call {@code task.cancel()} to stop it
     * (and always cancel your timers in onDisable).
     *
     * @param delayTicks  wait before the first run
     * @param periodTicks time between runs (20 = every second)
     */
    public static BukkitTask timer(Plugin plugin, long delayTicks, long periodTicks, Runnable task) {
        return Bukkit.getScheduler().runTaskTimer(plugin, safe(plugin, task), delayTicks, periodTicks);
    }

    /** Repeats on a background thread. Same rule as {@link #async}: no Bukkit world/player calls. */
    public static BukkitTask asyncTimer(Plugin plugin, long delayTicks, long periodTicks, Runnable task) {
        return Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, safe(plugin, task), delayTicks, periodTicks);
    }

    /**
     * Does slow work in the background (reading files, web requests, image processing) and then hands the
     * result to the main thread. If the plugin was disabled in the meantime, the second step is skipped.
     *
     * @param work   runs async - must not use the Bukkit API
     * @param result runs on the main thread with whatever {@code work} returned
     */
    public static <T> void asyncThenSync(Plugin plugin, Supplier<T> work, Consumer<T> result) {
        async(plugin, () -> {
            T value = work.get();
            if (plugin.isEnabled()) {
                sync(plugin, () -> result.accept(value));
            }
        });
    }

    /** Wraps a task so an exception is logged with the plugin's name instead of disappearing. */
    private static Runnable safe(Plugin plugin, Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (Throwable error) {
                plugin.getLogger().log(Level.SEVERE, "A scheduled task threw an error", error);
            }
        };
    }
}
