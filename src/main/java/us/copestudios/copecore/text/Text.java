package us.copestudios.copecore.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The ONE place that turns text into colors. Every Cope plugin should use this instead of
 * ChatColor.translateAlternateColorCodes, its own hex parser, or hardcoded § codes.
 *
 * <h2>What it understands</h2>
 * <ul>
 *   <li>MiniMessage: {@code <red>}, {@code <#FF8800>}, {@code <gradient:#FF8800:#8B0000>}, {@code <rainbow>},
 *       {@code <bold>}, {@code <hover:show_text:'...'>}, {@code <click:run_command:'/spawn'>} ...</li>
 *   <li>Old codes: {@code &a}, {@code &l}, {@code &#FF8800}, {@code &x&F&F&8&8&0&0}, {@code §a}</li>
 *   <li>Placeholders written as {@code <name>}, {@code {name}} or {@code %name%}</li>
 * </ul>
 *
 * <h2>Quick examples</h2>
 * <pre>{@code
 * Text.send(player, "<gradient:#FF8800:#8B0000>Blood Moon</gradient> <gray>is rising!");
 * Text.send(player, "&aOld style works &#FF8800too");
 * meta.setDisplayName(Text.itemName("<gold><bold>Hunter's Bow"));
 * String line = Text.replace("Welcome <player>!", "player", player.getName());
 * }</pre>
 *
 * <h2>Why messages are sent as JSON components</h2>
 * <p>Spigot doesn't include Adventure, and hover/click events can't be written with § codes.
 * So a message is parsed with MiniMessage, turned into JSON, and handed to the BungeeCord chat API that
 * every Spigot, Paper, Purpur and Arclight server has. That keeps hover and click working everywhere.</p>
 *
 * <h2>Untrusted text</h2>
 * <p>Anything a player typed (chat, a sign, a renamed item) must go through {@link #escape(String)} before you
 * put it inside a message. Otherwise a player could type {@code <click:run_command:'/op me'>} and it would
 * become a real clickable command.</p>
 */
public final class Text {

    /** MiniMessage parser. Non-strict: a missing closing tag is forgiven instead of failing. */
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    /**
     * Writes components as § codes, including hex colors in the "§x§R§R§G§G§B§B" form that Spigot needs.
     * Used for item names, lore, inventory titles, titles and the console.
     */
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character(LegacyComponentSerializer.SECTION_CHAR)
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    /** Writes components as the JSON chat format that Minecraft (and the BungeeCord chat API) understands. */
    private static final GsonComponentSerializer GSON = GsonComponentSerializer.gson();

    private Text() {
        // static helper only
    }

    // =====================================================================
    //  Converting
    // =====================================================================

    /**
     * Parses text (MiniMessage and/or legacy codes) into an Adventure component.
     * <p>Internal to CopeCore: other plugins should use the String methods below, because Adventure is
     * relocated inside CopeCore's jar and its classes are not meant to be used directly.</p>
     */
    static Component component(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        return MINI.deserialize(LegacyConverter.toMiniMessage(text));
    }

    /**
     * Converts text to a § string. Use this anywhere Bukkit wants a plain String with colors:
     * scoreboards, sign lines, {@code Bukkit.createInventory} titles, boss bars, etc.
     *
     * @param text MiniMessage or legacy text
     * @return colored string, never null
     */
    public static String legacy(String text) {
        return LEGACY.serialize(component(text));
    }

    /** Same as {@link #legacy(String)} for every line of a list (for example item lore). */
    public static List<String> legacy(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(legacy(line));
        }
        return out;
    }

    /**
     * Converts text for an item's display name or lore line.
     * <p>Minecraft draws custom item names in italic unless told otherwise. Starting with §r
     * (reset) keeps names looking the way you wrote them.</p>
     */
    public static String itemName(String text) {
        return ChatColor.RESET + legacy(text);
    }

    /** {@link #itemName(String)} for every lore line. Empty lines stay empty. */
    public static List<String> itemLore(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(line == null || line.isEmpty() ? "" : itemName(line));
        }
        return out;
    }

    /**
     * Removes every color and format, giving plain text.
     * Useful for comparing names, writing to log files, or measuring length.
     */
    public static String plain(String text) {
        String stripped = ChatColor.stripColor(legacy(text));
        return stripped == null ? "" : stripped;
    }

    /**
     * Makes text safe to insert into a message by disabling any MiniMessage tags inside it.
     * ALWAYS use this for text that comes from a player (chat, item names, signs, nicknames).
     */
    public static String escape(String untrusted) {
        return untrusted == null ? "" : MINI.escapeTags(untrusted);
    }

    /**
     * Turns old color codes into MiniMessage tags (&a -> &lt;reset&gt;&lt;green&gt;, &#FF8800 -> &lt;#FF8800&gt; ...).
     * Handy when migrating an old config to MiniMessage.
     */
    public static String toMiniMessage(String legacyText) {
        return LegacyConverter.toMiniMessage(legacyText);
    }

    // =====================================================================
    //  Placeholders
    // =====================================================================

    /**
     * Replaces placeholders in a template. Placeholders are given as name/value pairs:
     * <pre>{@code
     * Text.replace("<player> captured <koth> in {time}!",
     *         "player", player.getName(),
     *         "koth", kothName,
     *         "time", "2m 5s");
     * }</pre>
     * Each name is replaced in all three styles: {@code <name>}, {@code {name}} and {@code %name%},
     * so configs from any of the old plugins keep working.
     *
     * <p>Values are inserted as-is, so they may contain colors. If a value came from a player, wrap it in
     * {@link #escape(String)} first.</p>
     *
     * @param template text containing placeholders
     * @param pairs    name1, value1, name2, value2, ... (an odd last element is ignored)
     */
    public static String replace(String template, String... pairs) {
        if (template == null) {
            return "";
        }
        if (pairs == null || pairs.length < 2) {
            return template;
        }
        String result = template;
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            String name = pairs[i];
            String value = pairs[i + 1] == null ? "" : pairs[i + 1];
            if (name == null || name.isEmpty()) {
                continue;
            }
            result = result.replace("<" + name + ">", value)
                    .replace("{" + name + "}", value)
                    .replace("%" + name + "%", value);
        }
        return result;
    }

    // =====================================================================
    //  Sending
    // =====================================================================

    /**
     * Sends a message to a player or the console. Hover and click events work for players.
     * Nothing is sent if the text is empty, so an empty message in a config means "don't send this".
     */
    public static void send(CommandSender to, String text) {
        if (to == null || text == null || text.isEmpty()) {
            return;
        }
        sendComponent(to, component(text));
    }

    /** Sends the same message to every online player and the console. */
    public static void broadcast(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Component message = component(text);
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendComponent(player, message);
        }
        sendComponent(Bukkit.getConsoleSender(), message);
    }

    /** Shows text above the player's hotbar (the action bar). */
    public static void actionBar(Player player, String text) {
        if (player == null || text == null) {
            return;
        }
        Component message = component(text);
        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, toBungee(message));
        } catch (Throwable error) {
            // Extremely unusual server: fall back to plain legacy text. Never let a message crash a feature.
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    net.md_5.bungee.api.chat.TextComponent.fromLegacyText(LEGACY.serialize(message)));
        }
    }

    /**
     * Shows a big title. Times are in ticks (20 ticks = 1 second).
     *
     * @param title    big top line (may be empty)
     * @param subtitle smaller line under it (may be empty)
     */
    public static void title(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        if (player == null) {
            return;
        }
        player.sendTitle(
                title == null || title.isEmpty() ? "" : legacy(title),
                subtitle == null || subtitle.isEmpty() ? "" : legacy(subtitle),
                fadeIn, stay, fadeOut);
    }

    // =====================================================================
    //  Internal plumbing
    // =====================================================================

    /** Sends an already-parsed component. Used by {@link Messages} so prefixes and messages keep separate styles. */
    static void sendComponent(CommandSender to, Component message) {
        if (to instanceof Player player) {
            try {
                player.spigot().sendMessage(toBungee(message));
                return;
            } catch (Throwable error) {
                // Fall through to the legacy version below.
            }
        }
        // Console (and the fallback): § codes. The console prints them as colors.
        to.sendMessage(LEGACY.serialize(message));
    }

    /** Component -> JSON -> BungeeCord components, which every Bukkit-based server can display. */
    static BaseComponent[] toBungee(Component message) {
        return ComponentSerializer.parse(GSON.serialize(message));
    }

    /** Legacy string of an already-parsed component (used by {@link Messages}). */
    static String legacy(Component message) {
        return LEGACY.serialize(message);
    }
}
