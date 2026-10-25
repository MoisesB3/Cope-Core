package us.copestudios.copecore.text;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns old-style color codes into MiniMessage tags, so ONE parser handles every format.
 *
 * <p>Your plugins used four different color formats over time:</p>
 * <pre>
 *   &amp;a &amp;l &amp;r          classic codes            (every plugin)
 *   &amp;#FF8800             hex                      (CSSPVP, CSCombat, CSKoth)
 *   &amp;x&amp;F&amp;F&amp;8&amp;8&amp;0&amp;0   Spigot-style hex         (CZRTP's prefix)
 *   §a                  section sign             (CSCommands, CSIcons)
 * </pre>
 * <p>All of them are converted here, which means old config files keep working after a plugin
 * moves to CopeCore - nobody has to rewrite their messages.</p>
 *
 * <h2>Why every color becomes "&lt;reset&gt;&lt;color&gt;"</h2>
 * <p>In the old system a color code also turns OFF bold/italic/etc. ("&amp;l&amp;cHi" is bold,
 * "&amp;lHi &amp;cthere" makes "there" NOT bold). MiniMessage tags don't work that way, so each color
 * is written as {@code <reset><red>} to copy the old behaviour exactly.</p>
 */
final class LegacyConverter {

    /** &x&R&R&G&G&B&B (Spigot hex format). Checked before the others because it starts with "&x". */
    private static final Pattern SPIGOT_HEX = Pattern.compile(
            "&x&([0-9a-fA-F])&([0-9a-fA-F])&([0-9a-fA-F])&([0-9a-fA-F])&([0-9a-fA-F])&([0-9a-fA-F])");

    /** &#RRGGBB (the most common hex format in plugin configs). */
    private static final Pattern AMP_HEX = Pattern.compile("&#([0-9a-fA-F]{6})");

    /** A single classic code: &a, &l, &r ... */
    private static final Pattern CLASSIC = Pattern.compile("&([0-9a-fA-Fk-oK-OrR])");

    /** Classic code letter -> MiniMessage tag. */
    private static final Map<Character, String> CODES = Map.ofEntries(
            Map.entry('0', "<reset><black>"),
            Map.entry('1', "<reset><dark_blue>"),
            Map.entry('2', "<reset><dark_green>"),
            Map.entry('3', "<reset><dark_aqua>"),
            Map.entry('4', "<reset><dark_red>"),
            Map.entry('5', "<reset><dark_purple>"),
            Map.entry('6', "<reset><gold>"),
            Map.entry('7', "<reset><gray>"),
            Map.entry('8', "<reset><dark_gray>"),
            Map.entry('9', "<reset><blue>"),
            Map.entry('a', "<reset><green>"),
            Map.entry('b', "<reset><aqua>"),
            Map.entry('c', "<reset><red>"),
            Map.entry('d', "<reset><light_purple>"),
            Map.entry('e', "<reset><yellow>"),
            Map.entry('f', "<reset><white>"),
            Map.entry('k', "<obfuscated>"),
            Map.entry('l', "<bold>"),
            Map.entry('m', "<strikethrough>"),
            Map.entry('n', "<underlined>"),
            Map.entry('o', "<italic>"),
            Map.entry('r', "<reset>"));

    private LegacyConverter() {
        // static helper only
    }

    /**
     * Converts every legacy code in the text to MiniMessage. Text without legacy codes is returned unchanged,
     * and MiniMessage tags that are already in the text are left alone, so mixing both is fine.
     *
     * <p>A lone "&amp;" that is not followed by a valid code (e.g. "Tom &amp; Jerry") is not touched.</p>
     */
    static String toMiniMessage(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        // Quick exit: nothing to convert (the common case for MiniMessage configs).
        if (input.indexOf('&') < 0 && input.indexOf('§') < 0) {
            return input;
        }

        // Treat § exactly like & so both are handled by the same patterns below.
        String text = input.replace('§', '&');

        // 1) &x&R&R&G&G&B&B  ->  <reset><#RRGGBB>
        Matcher spigot = SPIGOT_HEX.matcher(text);
        StringBuilder out = new StringBuilder();
        while (spigot.find()) {
            String hex = spigot.group(1) + spigot.group(2) + spigot.group(3)
                    + spigot.group(4) + spigot.group(5) + spigot.group(6);
            spigot.appendReplacement(out, Matcher.quoteReplacement("<reset><#" + hex + ">"));
        }
        spigot.appendTail(out);
        text = out.toString();

        // 2) &#RRGGBB  ->  <reset><#RRGGBB>
        Matcher amp = AMP_HEX.matcher(text);
        out = new StringBuilder();
        while (amp.find()) {
            amp.appendReplacement(out, Matcher.quoteReplacement("<reset><#" + amp.group(1) + ">"));
        }
        amp.appendTail(out);
        text = out.toString();

        // 3) &a / &l / &r ...  ->  <reset><green> / <bold> / <reset> ...
        Matcher classic = CLASSIC.matcher(text);
        out = new StringBuilder();
        while (classic.find()) {
            char code = Character.toLowerCase(classic.group(1).charAt(0));
            classic.appendReplacement(out, Matcher.quoteReplacement(CODES.get(code)));
        }
        classic.appendTail(out);
        return out.toString();
    }
}
