package us.copestudios.copecore.gui;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import us.copestudios.copecore.text.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Builds items in one readable chain instead of the usual 6 lines of getItemMeta/setDisplayName/setItemMeta
 * per button (CSKoth's menu file was 740 lines mostly because of that).
 *
 * <pre>{@code
 * ItemStack button = ItemBuilder.of(Material.EMERALD_BLOCK)
 *         .name("<green><bold>Start KOTH")
 *         .lore("<gray>Starts the event now.", "", "<yellow>Click to start")
 *         .glow()
 *         .build();
 *
 * ItemStack head = ItemBuilder.head(player).name("<white>" + player.getName()).build();
 *
 * // Hidden data on an item (survives restarts):
 * ItemStack key = ItemBuilder.of(Material.TRIPWIRE_HOOK).name("<gold>Boss Key")
 *         .tag(plugin, "boss-key", "hunter").build();
 * String which = ItemBuilder.readTag(plugin, item, "boss-key"); // "hunter" or null
 * }</pre>
 *
 * <p>Names and lore accept MiniMessage and old color codes, and are never shown in italic.</p>
 */
public final class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta meta;

    private ItemBuilder(ItemStack item) {
        this.item = item;
        this.meta = item.getItemMeta(); // null only for AIR
    }

    /** Starts a new item of this material. */
    public static ItemBuilder of(Material material) {
        return new ItemBuilder(new ItemStack(material == null ? Material.BARRIER : material));
    }

    /** Starts from a copy of an existing item (the original is not changed). */
    public static ItemBuilder from(ItemStack existing) {
        return new ItemBuilder(existing == null ? new ItemStack(Material.BARRIER) : existing.clone());
    }

    /** A player head showing this player's skin. */
    public static ItemBuilder head(OfflinePlayer owner) {
        ItemBuilder builder = new ItemBuilder(new ItemStack(Material.PLAYER_HEAD));
        if (builder.meta instanceof SkullMeta skull && owner != null) {
            skull.setOwningPlayer(owner);
        }
        return builder;
    }

    /** Display name (MiniMessage or legacy codes). */
    public ItemBuilder name(String name) {
        if (meta != null) {
            meta.setDisplayName(Text.itemName(name));
        }
        return this;
    }

    /** Replaces the lore. Use "" for an empty line. */
    public ItemBuilder lore(String... lines) {
        return lore(Arrays.asList(lines));
    }

    /** Replaces the lore with these lines. */
    public ItemBuilder lore(List<String> lines) {
        if (meta != null) {
            meta.setLore(Text.itemLore(lines));
        }
        return this;
    }

    /** Adds lines under the existing lore (keeps what the item already had). */
    public ItemBuilder addLore(String... lines) {
        return addLore(Arrays.asList(lines));
    }

    /** Adds lines under the existing lore. */
    public ItemBuilder addLore(List<String> lines) {
        if (meta != null) {
            List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
            lore.addAll(Text.itemLore(lines));
            meta.setLore(lore);
        }
        return this;
    }

    /** Stack size (1-64, clamped to the item's real max). */
    public ItemBuilder amount(int amount) {
        item.setAmount(Math.max(1, Math.min(amount, item.getMaxStackSize())));
        return this;
    }

    /** Adds the enchanted shine without showing an enchantment in the tooltip. */
    public ItemBuilder glow() {
        return glow(true);
    }

    /**
     * Adds or removes the enchanted shine. Works on 1.20 and 1.21: it adds a hidden enchantment
     * (looked up by its key, because Bukkit renamed the enchantment fields in 1.20.5).
     */
    public ItemBuilder glow(boolean glow) {
        if (meta == null) {
            return this;
        }
        Enchantment shine = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("unbreaking"));
        if (shine == null) {
            return this;
        }
        if (glow) {
            meta.addEnchant(shine, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        } else {
            meta.removeEnchant(shine);
        }
        return this;
    }

    /** Hides every tooltip extra (enchantments, attributes, "unbreakable", potion effects...). */
    public ItemBuilder hideFlags() {
        if (meta != null) {
            meta.addItemFlags(ItemFlag.values());
        }
        return this;
    }

    /** Custom model data, for resource-pack textures. */
    public ItemBuilder customModelData(int data) {
        if (meta != null) {
            meta.setCustomModelData(data);
        }
        return this;
    }

    /** Makes the item never lose durability. */
    public ItemBuilder unbreakable(boolean unbreakable) {
        if (meta != null) {
            meta.setUnbreakable(unbreakable);
        }
        return this;
    }

    /**
     * Stores hidden text on the item (Persistent Data Container). It stays on the item through restarts,
     * renames and trades, which makes it the safe way to recognise your own items - never compare names.
     */
    public ItemBuilder tag(Plugin plugin, String key, String value) {
        if (meta != null) {
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, key), PersistentDataType.STRING, value);
        }
        return this;
    }

    /** Finishes the item. */
    public ItemStack build() {
        if (meta != null) {
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Reads hidden text stored with {@link #tag}. Returns null if the item doesn't have it. */
    public static String readTag(Plugin plugin, ItemStack item, String key) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return meta == null ? null
                : meta.getPersistentDataContainer().get(new NamespacedKey(plugin, key), PersistentDataType.STRING);
    }

    /** A material by name ("DIAMOND", "minecraft:diamond"), or the fallback if it doesn't exist on this version. */
    public static Material material(String name, Material fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        Material found = Material.matchMaterial(name.trim());
        return found == null ? fallback : found;
    }
}
