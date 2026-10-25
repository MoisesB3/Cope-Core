package us.copestudios.copecore.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns times into readable text and back. Every plugin had its own "minutes + ":" + seconds" code;
 * this is the shared version.
 *
 * <pre>{@code
 * TimeFormat.duration(125_000)      // "2m 5s"
 * TimeFormat.duration(3_725_000)    // "1h 2m 5s"
 * TimeFormat.clock(125)             // "2:05"   (seconds -> m:ss, good for scoreboards)
 * TimeFormat.parse("1h30m")         // 5_400_000 (milliseconds), or -1 if invalid
 * }</pre>
 */
public final class TimeFormat {

    /** Matches pieces like "1d", "2h", "30m", "15s" in any order, e.g. "1h30m" or "2d 4h". */
    private static final Pattern PIECE = Pattern.compile("(\\d+)\\s*([dhms])");

    private TimeFormat() {
        // static helper only
    }

    /**
     * Milliseconds -> "1d 2h 3m 4s". Zero parts are skipped; anything under a second shows "0s".
     */
    public static String duration(long millis) {
        long totalSeconds = Math.max(0, (millis + 999) / 1000); // round UP so "0.2s left" shows "1s"
        long days = totalSeconds / 86_400;
        long hours = (totalSeconds % 86_400) / 3_600;
        long minutes = (totalSeconds % 3_600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder out = new StringBuilder();
        if (days > 0) {
            out.append(days).append("d ");
        }
        if (hours > 0) {
            out.append(hours).append("h ");
        }
        if (minutes > 0) {
            out.append(minutes).append("m ");
        }
        if (seconds > 0 || out.length() == 0) {
            out.append(seconds).append("s");
        }
        return out.toString().trim();
    }

    /** Seconds -> "m:ss" (or "h:mm:ss" when an hour or more). */
    public static String clock(long totalSeconds) {
        long s = Math.max(0, totalSeconds);
        long hours = s / 3600;
        long minutes = (s % 3600) / 60;
        long seconds = s % 60;
        if (hours > 0) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
    }

    /**
     * Reads a time typed by a player or written in a config.
     * <ul>
     *   <li>"90" (just a number) = 90 seconds</li>
     *   <li>"1h30m", "2d 4h", "45s", "10m" (any combination of d/h/m/s)</li>
     * </ul>
     *
     * @return milliseconds, or -1 if the text is not a valid time
     */
    public static long parse(String text) {
        if (text == null) {
            return -1;
        }
        String input = text.trim().toLowerCase(Locale.ROOT);
        if (input.isEmpty()) {
            return -1;
        }
        if (input.chars().allMatch(Character::isDigit)) {
            try {
                return Long.parseLong(input) * 1000L;
            } catch (NumberFormatException tooBig) {
                return -1;
            }
        }
        Matcher matcher = PIECE.matcher(input);
        long total = 0;
        int matchedLength = 0;
        while (matcher.find()) {
            long amount = Long.parseLong(matcher.group(1));
            total += switch (matcher.group(2)) {
                case "d" -> amount * 86_400_000L;
                case "h" -> amount * 3_600_000L;
                case "m" -> amount * 60_000L;
                default -> amount * 1_000L;
            };
            matchedLength += matcher.group().length();
        }
        // Reject text that had other junk in it, like "5x" or "abc".
        int meaningful = input.replace(" ", "").length();
        return matchedLength > 0 && matchedLength >= meaningful ? total : -1;
    }
}
