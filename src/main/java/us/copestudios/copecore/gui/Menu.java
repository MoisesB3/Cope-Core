package us.copestudios.copecore.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import us.copestudios.copecore.CopeCore;
import us.copestudios.copecore.scheduler.Tasks;
import us.copestudios.copecore.text.Messages;
import us.copestudios.copecore.text.Text;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Function;
import java.util.function.IntConsumer;

/**
 * Base class for every chest menu. Extend it, fill the slots in {@link #build()}, and call {@link #open()}.
 *
 * <h2>Why this is safer than the old menus</h2>
 * <ul>
 *   <li>Menus are recognised by their Java object (the menu is the inventory's "holder"), never by comparing the
 *       title text. CSKoth and CSSPVP matched titles, which breaks as soon as a title is recolored or translated,
 *       and can match a different plugin's menu with the same name.</li>
 *   <li>Each button carries its own click action, so there is no giant "which name was clicked?" if/else chain.</li>
 *   <li>Players can never take items out of a menu, drag into it, or shift-click items in, unless you allow it
 *       for specific slots with {@link #editable(int...)}.</li>
 *   <li>Opening/closing from inside a click is automatically delayed one tick (Bukkit says not to do it directly).</li>
 *   <li>A spam-click limit and a click sound come from CopeCore's config.yml.</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * public final class KothMainMenu extends Menu {
 *     private final KothManager koths;
 *
 *     public KothMainMenu(CSKoth plugin, Player viewer) {
 *         super(plugin, viewer, 3, "<gold><bold>KOTH Menu");
 *         this.koths = plugin.koths();
 *     }
 *
 *     @Override
 *     protected void build() {
 *         set(11, ItemBuilder.of(Material.EMERALD_BLOCK).name("<green>Create KOTH").build(),
 *                 click -> CopeCore.prompts().text(viewer, "<yellow>Name of the new KOTH?",
 *                         name -> { koths.create(name, viewer.getLocation()); open(); },
 *                         this::open));
 *         set(15, ItemBuilder.of(Material.BOOK).name("<aqua>List KOTHs").build(),
 *                 click -> new KothListMenu(plugin, viewer, 0).open());
 *         fillBorder();
 *         fillEmpty();
 *     }
 * }
 *
 * new KothMainMenu(plugin, player).open();
 * }</pre>
 */
public abstract class Menu implements InventoryHolder {

    /**
     * The 28 slots inside the border of a 6-row menu (rows 2-5, columns 2-8).
     * Perfect for lists: {@code paginate(items, page, Menu.GRID, ...)}.
     */
    public static final int[] GRID = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};

    /** The plugin that made this menu. Its menus are closed automatically when it's disabled. */
    protected final Plugin plugin;
    /** The player looking at this menu. A menu belongs to exactly one player. */
    protected final Player viewer;

    private final int rows;
    private String title;
    private Inventory inventory;
    private final Map<Integer, Consumer<MenuClick>> actions = new HashMap<>();
    private final Set<Integer> editableSlots = new HashSet<>();

    /**
     * @param plugin your plugin
     * @param viewer who will see the menu
     * @param rows   1 to 6
     * @param title  MiniMessage or legacy text
     */
    protected Menu(Plugin plugin, Player viewer, int rows, String title) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.rows = Math.max(1, Math.min(6, rows));
        this.title = title == null ? "" : title;
    }

    /**
     * Put your items here with {@link #set}. It runs every time the menu is opened or refreshed, so always
     * build from the CURRENT data (that way a refresh shows new values).
     */
    protected abstract void build();

    /** Called after the player closed the menu (by pressing Esc, opening another menu, or logging out). */
    protected void onClose() {
        // Override when you need it, e.g. to save items placed in editable slots.
    }

    // =====================================================================
    //  Opening / refreshing / closing
    // =====================================================================

    /** Builds the menu and shows it. Safe to call from anywhere, including inside a click. */
    public void open() {
        if (!viewer.isOnline() || !plugin.isEnabled()) {
            return;
        }
        if (!Bukkit.isPrimaryThread() || MenuListener.isHandlingClick()) {
            Tasks.sync(plugin, this::openNow); // next tick, as Bukkit recommends
        } else {
            openNow();
        }
    }

    private void openNow() {
        if (!viewer.isOnline()) {
            return;
        }
        if (inventory == null) {
            inventory = Bukkit.createInventory(this, rows * 9, Text.legacy(title));
        }
        refresh();
        viewer.openInventory(inventory);
        CopeCore.get().menus().track(this);
    }

    /** Rebuilds every item in place (call after a value changed). Does not reopen the menu. */
    public void refresh() {
        if (inventory == null) {
            return;
        }
        inventory.clear();
        actions.clear();
        editableSlots.clear();
        try {
            build();
        } catch (RuntimeException error) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Error building menu " + getClass().getSimpleName(), error);
        }
    }

    /** Changes the title. Minecraft can't rename an open inventory, so the menu is reopened. */
    public void title(String newTitle) {
        this.title = newTitle == null ? "" : newTitle;
        this.inventory = null;
        open();
    }

    /** Closes the menu for the viewer (delayed one tick when called from a click). */
    public void close() {
        if (MenuListener.isHandlingClick()) {
            Tasks.sync(plugin, viewer::closeInventory);
        } else {
            viewer.closeInventory();
        }
    }

    @Override
    public Inventory getInventory() {
        if (inventory == null) {
            inventory = Bukkit.createInventory(this, rows * 9, Text.legacy(title));
        }
        return inventory;
    }

    public Player viewer() {
        return viewer;
    }

    public Plugin plugin() {
        return plugin;
    }

    /** Number of slots (rows × 9). */
    public int size() {
        return rows * 9;
    }

    // =====================================================================
    //  Placing items
    // =====================================================================

    /** Puts an item with a click action. Use null as the action for decoration. */
    protected void set(int slot, ItemStack item, Consumer<MenuClick> action) {
        if (slot < 0 || slot >= size()) {
            return;
        }
        getInventory().setItem(slot, item);
        if (action == null) {
            actions.remove(slot);
        } else {
            actions.put(slot, action);
        }
    }

    /** Puts an item that does nothing when clicked. */
    protected void set(int slot, ItemStack item) {
        set(slot, item, null);
    }

    /** Shortcut: a button made from a material, a name and lore lines. */
    protected void button(int slot, Material material, String name, List<String> lore, Consumer<MenuClick> action) {
        set(slot, ItemBuilder.of(material).name(name).lore(lore).hideFlags().build(), action);
    }

    /** A "Back" arrow (text from messages: generic.menu-back). */
    protected void backButton(int slot, Runnable onClick) {
        set(slot, ItemBuilder.of(Material.ARROW).name(coreMessages().format("generic.menu-back")).build(),
                click -> onClick.run());
    }

    /** A "Close" barrier (text from messages: generic.menu-close). */
    protected void closeButton(int slot) {
        set(slot, ItemBuilder.of(Material.BARRIER).name(coreMessages().format("generic.menu-close")).build(),
                click -> close());
    }

    /**
     * Lets the player put items into / take items out of these slots (e.g. a reward editor).
     * Call it inside build(). Read the items back with {@link #itemsIn(int...)} in {@link #onClose()}.
     */
    protected void editable(int... slots) {
        for (int slot : slots) {
            editableSlots.add(slot);
            actions.remove(slot);
        }
    }

    /** The real items currently in these slots (empty slots are skipped). */
    protected List<ItemStack> itemsIn(int... slots) {
        List<ItemStack> items = new ArrayList<>();
        for (int slot : slots) {
            ItemStack item = getInventory().getItem(slot);
            if (item != null && item.getType() != Material.AIR) {
                items.add(item.clone());
            }
        }
        return items;
    }

    /** Fills the outer ring with the border pane from CopeCore's config (menus.border-material). */
    protected void fillBorder() {
        ItemStack pane = pane("menus.border-material", Material.ORANGE_STAINED_GLASS_PANE);
        int size = size();
        for (int slot = 0; slot < size; slot++) {
            boolean edge = slot < 9 || slot >= size - 9 || slot % 9 == 0 || slot % 9 == 8;
            if (edge && getInventory().getItem(slot) == null && !editableSlots.contains(slot)) {
                set(slot, pane);
            }
        }
    }

    /** Fills every empty slot with the filler pane (menus.filler-material). Call it last. */
    protected void fillEmpty() {
        ItemStack pane = pane("menus.filler-material", Material.BLACK_STAINED_GLASS_PANE);
        for (int slot = 0; slot < size(); slot++) {
            if (getInventory().getItem(slot) == null && !editableSlots.contains(slot)) {
                set(slot, pane);
            }
        }
    }

    // =====================================================================
    //  Lists with pages
    // =====================================================================

    /** How many pages a list needs. */
    public static int pageCount(int entries, int perPage) {
        return Math.max(1, (entries + perPage - 1) / perPage);
    }

    /**
     * Shows one page of a list and adds previous/next arrows when needed.
     *
     * <pre>{@code
     * // inside build(), with a "page" field in your menu:
     * paginate(new ArrayList<>(koths.all()), page, Menu.GRID,
     *         koth -> ItemBuilder.of(Material.BEACON).name("<gold>" + koth.name()).build(),
     *         (koth, click) -> new KothEditMenu(plugin, viewer, koth).open(),
     *         45, 53,
     *         newPage -> new KothListMenu(plugin, viewer, newPage).open());
     * }</pre>
     *
     * @param entries      everything in the list
     * @param page         page to show, starting at 0
     * @param slots        where entries go (e.g. {@link #GRID})
     * @param icon         turns an entry into its item
     * @param onClick      what clicking an entry does
     * @param previousSlot slot of the "previous page" arrow
     * @param nextSlot     slot of the "next page" arrow
     * @param goToPage     opens the given page (usually a new instance of your menu)
     */
    protected <T> void paginate(List<T> entries, int page, int[] slots,
                                Function<T, ItemStack> icon, BiConsumer<T, MenuClick> onClick,
                                int previousSlot, int nextSlot, IntConsumer goToPage) {
        List<T> list = entries == null ? Collections.emptyList() : entries;
        int pages = pageCount(list.size(), slots.length);
        int current = Math.max(0, Math.min(page, pages - 1));

        int start = current * slots.length;
        for (int i = 0; i < slots.length && start + i < list.size(); i++) {
            T entry = list.get(start + i);
            set(slots[i], icon.apply(entry), onClick == null ? null : click -> onClick.accept(entry, click));
        }

        Messages core = coreMessages();
        String pageInfo = core.format("generic.menu-page-info",
                "page", String.valueOf(current + 1), "pages", String.valueOf(pages));
        if (current > 0) {
            set(previousSlot, ItemBuilder.of(Material.ARROW).name(core.format("generic.menu-previous-page"))
                    .lore(pageInfo).build(), click -> goToPage.accept(current - 1));
        }
        if (current < pages - 1) {
            set(nextSlot, ItemBuilder.of(Material.ARROW).name(core.format("generic.menu-next-page"))
                    .lore(pageInfo).build(), click -> goToPage.accept(current + 1));
        }
    }

    // =====================================================================
    //  Number editing (the control from Cope Bosses' editor)
    // =====================================================================

    /**
     * Standard number button behaviour, used inside a click action:
     * <ul>
     *   <li>Left click: + step &nbsp; Right click: - step &nbsp; Shift: ×10</li>
     *   <li>Q (drop key): asks the player to type an exact value in chat</li>
     * </ul>
     * The new value is clamped to min/max, passed to {@code apply}, and the menu refreshes itself.
     *
     * <pre>{@code
     * set(13, ItemBuilder.of(Material.CLOCK).name("<yellow>Capture time: <white>" + koth.captureTime() + "s")
     *                 .lore(numberHint(10)).build(),
     *         click -> adjustNumber(click, "capture time", koth.captureTime(), 10, 10, 3600, true,
     *                 value -> koth.captureTime((int) value)));
     * }</pre>
     */
    protected void adjustNumber(MenuClick click, String label, double current, double step,
                                double min, double max, boolean wholeNumber, DoubleConsumer apply) {
        if (click.isDrop()) {
            String question = coreMessages().format("prompt.number-question",
                    "label", Text.escape(label), "current", format(current),
                    "min", format(min), "max", format(max));
            CopeCore.prompts().decimal(viewer, question, min, max, wholeNumber,
                    value -> {
                        apply.accept(value);
                        open();
                    },
                    this::open);
            return;
        }
        double direction = click.isLeft() ? 1 : click.isRight() ? -1 : 0;
        if (direction == 0) {
            return;
        }
        double multiplier = click.isShift() ? 10 : 1;
        double value = Math.max(min, Math.min(max, current + direction * multiplier * step));
        apply.accept(wholeNumber ? Math.round(value) : Math.round(value * 1000.0) / 1000.0);
        refresh();
    }

    /** The lore lines explaining the number controls (from generic.menu-number-hint). */
    protected List<String> numberHint(double step) {
        List<String> lines = new ArrayList<>();
        for (String line : coreMessages().templates("generic.menu-number-hint")) {
            lines.add(Text.replace(line, "step", format(step)));
        }
        return lines;
    }

    /** 12.0 -> "12", 0.25 -> "0.25" */
    protected static String format(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    // =====================================================================
    //  Used by MenuListener
    // =====================================================================

    void handleClick(MenuClick click) {
        Consumer<MenuClick> action = actions.get(click.slot());
        if (action != null) {
            action.accept(click);
        }
    }

    boolean isEditable(int slot) {
        return editableSlots.contains(slot);
    }

    boolean hasEditableSlots() {
        return !editableSlots.isEmpty();
    }

    void fireClose() {
        onClose();
    }

    // ---------------------------------------------------------------------

    private static Messages coreMessages() {
        return CopeCore.get().coreMessages();
    }

    private static ItemStack pane(String configPath, Material fallback) {
        String name = CopeCore.get().coreConfig().get().getString(configPath, fallback.name());
        return ItemBuilder.of(ItemBuilder.material(name, fallback)).name(" ").hideFlags().build();
    }
}
