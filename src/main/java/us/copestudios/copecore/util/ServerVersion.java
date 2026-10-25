package us.copestudios.copecore.util;

import org.bukkit.Bukkit;

/**
 * Tells you which Minecraft version the server runs, so a plugin can switch behaviour where Minecraft
 * changed (for example particle names in 1.20.5 or attribute names in 1.21.2).
 *
 * <pre>{@code
 * if (ServerVersion.isAtLeast(1, 20, 5)) {
 *     // new particle names
 * }
 * String name = ServerVersion.display(); // "1.21.1"
 * }</pre>
 */
public final class ServerVersion {

    /** Parsed once: [major, minor, patch], e.g. 1.21.1 -> {1, 21, 1}; 1.20 -> {1, 20, 0}. */
    private static final int[] VERSION = parse(Bukkit.getBukkitVersion());

    private ServerVersion() {
        // static helper only
    }

    /** True if the server is this version or newer. */
    public static boolean isAtLeast(int major, int minor, int patch) {
        if (VERSION[0] != major) {
            return VERSION[0] > major;
        }
        if (VERSION[1] != minor) {
            return VERSION[1] > minor;
        }
        return VERSION[2] >= patch;
    }

    /** e.g. 21 for 1.21.x */
    public static int minor() {
        return VERSION[1];
    }

    /** e.g. 1 for 1.21.1 */
    public static int patch() {
        return VERSION[2];
    }

    /** "1.21.1" */
    public static String display() {
        return VERSION[0] + "." + VERSION[1] + (VERSION[2] > 0 ? "." + VERSION[2] : "");
    }

    /** "1.21.1-R0.1-SNAPSHOT" -> {1, 21, 1}. Unknown formats give {1, 20, 0} (our minimum). */
    static int[] parse(String bukkitVersion) {
        int[] result = {1, 20, 0};
        if (bukkitVersion == null) {
            return result;
        }
        String numbers = bukkitVersion.split("-")[0];
        String[] parts = numbers.split("\\.");
        for (int i = 0; i < Math.min(3, parts.length); i++) {
            try {
                result[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException ignored) {
                // keep the default for this part
            }
        }
        if (parts.length < 3) {
            result[2] = 0;
        }
        return result;
    }
}
