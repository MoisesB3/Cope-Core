package us.copestudios.copecore.reward;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import us.copestudios.copecore.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;

/**
 * A bundle of rewards: items (each with a chance and an amount range), console commands, and messages.
 * Used for KOTH captures, boss kills, giveaways, duel wins... Built from Cope Bosses' reward system.
 *
 * <h2>In a config file</h2>
 * <pre>
 * rewards:
 *   items:
 *     - item: {...saved by the in-game editor...}
 *       amount: 1-3          # a fixed number (2) or a range (1-3)
 *       chance: 0.5          # 0.0 - 1.0 (here: 50%)
 *   commands:                # run by the console. Placeholders: &lt;player&gt;, {player}, %player%
 *     - "eco give &lt;player&gt; 1000"
 *   messages:                # sent to the player who gets the rewards (MiniMessage)
 *     - "&lt;green&gt;You got your rewards!"
 *   broadcast:               # sent to everyone
 *     - "&lt;gold&gt;&lt;player&gt; &lt;yellow&gt;won the KOTH!"
 * </pre>
 *
 * <h2>In code</h2>
 * <pre>{@code
 * RewardSet rewards = RewardSet.load(config.getConfigurationSection("rewards"), getLogger());
 * rewards.give(player, random, "koth", kothName);     // extra placeholders are optional
 *
 * // From an editor menu:
 * rewards.addItem(new ItemReward(handItem, 1, 1, 1.0));
 * rewards.save(config.createSection("rewards"));
 * }</pre>
 *
 * <p>Items that don't fit in the player's inventory are dropped at their feet instead of disappearing
 * (CSKoth lost them).</p>
 */
public final class RewardSet {

    /**
     * One possible item.
     *
     * @param item   the item (its amount is ignored; min/max decide it)
     * @param min    smallest amount given
     * @param max    biggest amount given
     * @param chance 0.0 - 1.0 chance to be given at all
     */
    public record ItemReward(ItemStack item, int min, int max, double chance) {

        public ItemReward {
            item = item.clone();
            min = Math.max(1, min);
            max = Math.max(min, max);
            chance = Math.max(0, Math.min(1, chance));
        }
    }

    private final List<ItemReward> items = new ArrayList<>();
    private final List<String> commands = new ArrayList<>();
    private final List<String> messages = new ArrayList<>();
    private final List<String> broadcast = new ArrayList<>();

    // =====================================================================
    //  Loading / saving
    // =====================================================================

    /**
     * Reads a reward section. Broken entries are skipped with a warning in the console.
     *
     * @param section the "rewards" section (null = empty rewards)
     * @param log     where to print warnings (your plugin's getLogger())
     */
    public static RewardSet load(ConfigurationSection section, Logger log) {
        RewardSet rewards = new RewardSet();
        if (section == null) {
            return rewards;
        }
        int index = 0;
        for (Map<?, ?> raw : section.getMapList("items")) {
            index++;
            Object itemObject = raw.get("item");
            if (!(itemObject instanceof ItemStack item)) {
                log.warning("Reward item #" + index + " in '" + section.getCurrentPath() + "' is missing or invalid - skipped.");
                continue;
            }
            int[] range = parseRange(raw.get("amount"), item.getAmount());
            double chance = raw.get("chance") instanceof Number number ? number.doubleValue() : 1.0;
            rewards.items.add(new ItemReward(item, range[0], range[1], chance));
        }
        rewards.commands.addAll(section.getStringList("commands"));
        rewards.messages.addAll(section.getStringList("messages"));
        rewards.broadcast.addAll(section.getStringList("broadcast"));
        return rewards;
    }

    /** Writes the rewards into a section (overwriting what was there). */
    public void save(ConfigurationSection section) {
        List<Map<String, Object>> itemMaps = new ArrayList<>();
        for (ItemReward reward : items) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("item", reward.item());
            map.put("amount", reward.min() == reward.max() ? (Object) reward.min() : reward.min() + "-" + reward.max());
            map.put("chance", reward.chance());
            itemMaps.add(map);
        }
        section.set("items", itemMaps);
        section.set("commands", new ArrayList<>(commands));
        section.set("messages", new ArrayList<>(messages));
        section.set("broadcast", new ArrayList<>(broadcast));
    }

    // =====================================================================
    //  Giving
    // =====================================================================

    /**
     * Gives everything to the player: rolls each item's chance and amount, runs the commands, sends the messages.
     * Must be called on the main thread.
     *
     * @param extraPairs more placeholders for commands/messages, e.g. "koth", "castle"
     *                   (&lt;player&gt; is always filled in)
     * @return the items that were actually given (after chances), e.g. to show "you got: ..."
     */
    public List<ItemStack> give(Player player, Random random, String... extraPairs) {
        String[] pairs = withPlayer(player, extraPairs);
        List<ItemStack> given = new ArrayList<>();

        for (ItemReward reward : items) {
            if (random.nextDouble() > reward.chance()) {
                continue;
            }
            int amount = reward.min() + (reward.max() > reward.min() ? random.nextInt(reward.max() - reward.min() + 1) : 0);
            ItemStack stack = reward.item().clone();
            stack.setAmount(amount);
            given.add(stack.clone());
            // addItem returns what didn't fit: drop it instead of losing it.
            for (ItemStack leftover : player.getInventory().addItem(stack).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
        for (String command : commands) {
            String line = Text.replace(command, pairs);
            if (line.startsWith("/")) {
                line = line.substring(1);
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line);
        }
        for (String message : messages) {
            Text.send(player, Text.replace(message, pairs));
        }
        for (String message : broadcast) {
            Text.broadcast(Text.replace(message, pairs));
        }
        return given;
    }

    // =====================================================================
    //  Editing (for in-game editors)
    // =====================================================================

    public List<ItemReward> items() {
        return Collections.unmodifiableList(items);
    }

    public List<String> commands() {
        return Collections.unmodifiableList(commands);
    }

    public List<String> messages() {
        return Collections.unmodifiableList(messages);
    }

    public List<String> broadcasts() {
        return Collections.unmodifiableList(broadcast);
    }

    public void addItem(ItemReward reward) {
        items.add(reward);
    }

    public void setItem(int index, ItemReward reward) {
        items.set(index, reward);
    }

    public void removeItem(int index) {
        if (index >= 0 && index < items.size()) {
            items.remove(index);
        }
    }

    public void addCommand(String command) {
        commands.add(command);
    }

    public void removeCommand(int index) {
        if (index >= 0 && index < commands.size()) {
            commands.remove(index);
        }
    }

    public void addMessage(String message) {
        messages.add(message);
    }

    public void addBroadcast(String message) {
        broadcast.add(message);
    }

    /** True if there is nothing to give. */
    public boolean isEmpty() {
        return items.isEmpty() && commands.isEmpty() && messages.isEmpty() && broadcast.isEmpty();
    }

    // =====================================================================
    //  Helpers
    // =====================================================================

    /** "3" -> {3,3}; "1-5" -> {1,5}; 2 (number) -> {2,2}; anything else -> {fallback, fallback}. */
    public static int[] parseRange(Object value, int fallback) {
        int safeFallback = Math.max(1, fallback);
        if (value instanceof Number number) {
            int n = Math.max(1, number.intValue());
            return new int[]{n, n};
        }
        if (value instanceof String text) {
            String[] parts = text.trim().split("\\s*-\\s*");
            try {
                int a = Integer.parseInt(parts[0]);
                int b = parts.length > 1 ? Integer.parseInt(parts[1]) : a;
                return new int[]{Math.max(1, Math.min(a, b)), Math.max(1, Math.max(a, b))};
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return new int[]{safeFallback, safeFallback};
    }

    private static String[] withPlayer(Player player, String[] extra) {
        String[] pairs = new String[(extra == null ? 0 : extra.length) + 2];
        pairs[0] = "player";
        pairs[1] = player.getName();
        if (extra != null) {
            System.arraycopy(extra, 0, pairs, 2, extra.length);
        }
        return pairs;
    }
}
