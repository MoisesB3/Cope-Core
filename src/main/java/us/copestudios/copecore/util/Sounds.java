package us.copestudios.copecore.util;

import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Plays sounds from config text, on every version from 1.20 to the newest 1.21.x.
 *
 * <h2>Why not just Sound.valueOf("...")?</h2>
 * <p>In Minecraft 1.21.3 Bukkit changed {@code org.bukkit.Sound} from an enum into an interface. Code that
 * was compiled against 1.20 and uses the Sound class directly can break on newer servers. This class never
 * touches the Sound class: it plays sounds by their text key ({@code "entity.player.levelup"}), which every
 * version supports, and which also works for custom resource-pack sounds (handy for Cope Nights jump scares).</p>
 *
 * <h2>Accepted formats (all of these work)</h2>
 * <pre>
 *   entity.player.levelup                  Minecraft key
 *   minecraft:entity.player.levelup        Minecraft key with namespace
 *   ENTITY_PLAYER_LEVELUP                  old Bukkit enum name (old configs keep working)
 *   copenights:scare.scream                custom resource-pack sound
 *   entity.player.levelup:0.8:1.5          ...with volume 0.8 and pitch 1.5
 * </pre>
 * <p>An empty string means "no sound", so server owners can turn any sound off in a config.</p>
 */
public final class Sounds {

    /** "ENTITY_PLAYER_LEVELUP" -> "minecraft:entity.player.levelup", built once from the server's sound list. */
    private static Map<String, String> enumNameToKey;

    private Sounds() {
        // static helper only
    }

    /** Plays a sound to one player at their own location. */
    public static void play(Player player, String spec) {
        if (player != null) {
            play(player, player.getLocation(), spec);
        }
    }

    /** Plays a sound to one player at a location (only that player hears it). */
    public static void play(Player player, Location at, String spec) {
        Parsed parsed = parse(spec);
        if (parsed != null && player != null && at != null) {
            player.playSound(at, parsed.key, parsed.volume, parsed.pitch);
        }
    }

    /** Plays a sound in the world so every nearby player hears it. */
    public static void playAt(Location at, String spec) {
        Parsed parsed = parse(spec);
        if (parsed != null && at != null && at.getWorld() != null) {
            at.getWorld().playSound(at, parsed.key, parsed.volume, parsed.pitch);
        }
    }

    /**
     * Checks a sound text, e.g. to warn about typos when a config loads.
     *
     * @return true if it's empty (= no sound) or the sound exists / is a custom namespaced sound
     */
    public static boolean isValid(String spec) {
        if (spec == null || spec.isBlank()) {
            return true;
        }
        return parse(spec) != null;
    }

    // ---------------------------------------------------------------------

    /** A sound key with its volume and pitch. */
    private record Parsed(String key, float volume, float pitch) {
    }

    private static Parsed parse(String spec) {
        if (spec == null || spec.isBlank()) {
            return null;
        }
        // Split "key:volume:pitch". Careful: the key itself may contain one ':' (the namespace).
        String text = spec.trim();
        float volume = 1f;
        float pitch = 1f;
        String[] parts = text.split(":");
        int numbersAtEnd = 0;
        for (int i = parts.length - 1; i >= 1 && numbersAtEnd < 2; i--) {
            if (isNumber(parts[i])) {
                numbersAtEnd++;
            } else {
                break;
            }
        }
        if (numbersAtEnd == 2) {
            volume = Float.parseFloat(parts[parts.length - 2]);
            pitch = Float.parseFloat(parts[parts.length - 1]);
        } else if (numbersAtEnd == 1) {
            volume = Float.parseFloat(parts[parts.length - 1]);
        }
        String key = String.join(":", java.util.Arrays.copyOf(parts, parts.length - numbersAtEnd));

        String resolved = resolveKey(key);
        return resolved == null ? null : new Parsed(resolved, volume, pitch);
    }

    /** Turns any accepted spelling into a key Minecraft understands, or null if it's unknown. */
    private static String resolveKey(String key) {
        // Custom namespace (resource pack): pass through untouched. Minecraft ignores unknown sounds safely.
        if (key.contains(":") && !key.toLowerCase(Locale.ROOT).startsWith("minecraft:")) {
            return key.toLowerCase(Locale.ROOT);
        }
        String plainKey = key.toLowerCase(Locale.ROOT).replace("minecraft:", "");

        // Already a dotted Minecraft key ("entity.player.levelup").
        if (plainKey.contains(".")) {
            return "minecraft:" + plainKey;
        }
        // Old enum style ("ENTITY_PLAYER_LEVELUP") - look it up in the server's sound list.
        return enumNames().get(key.toUpperCase(Locale.ROOT));
    }

    /**
     * Builds the enum-name lookup from the server's sound registry. The Bukkit enum names are the Minecraft keys
     * with dots turned into underscores ("block.note_block.pling" -> "BLOCK_NOTE_BLOCK_PLING"), so we can rebuild
     * them without ever loading the Sound class directly.
     */
    private static synchronized Map<String, String> enumNames() {
        if (enumNameToKey == null) {
            Map<String, String> map = new HashMap<>();
            try {
                // Iterated as plain Objects on purpose: this way the compiled code never refers to the
                // Sound type itself, which changed from enum to interface in 1.21.3.
                for (Object sound : (Iterable<?>) Registry.SOUNDS) {
                    NamespacedKey key = ((Keyed) sound).getKey();
                    map.put(key.getKey().replace('.', '_').toUpperCase(Locale.ROOT), key.toString());
                }
            } catch (Throwable error) {
                // Should never happen, but a sound must never stop a plugin from working.
            }
            enumNameToKey = map;
        }
        return enumNameToKey;
    }

    private static boolean isNumber(String text) {
        try {
            Float.parseFloat(text);
            return true;
        } catch (NumberFormatException notANumber) {
            return false;
        }
    }
}
