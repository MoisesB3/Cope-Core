package us.copestudios.copecore.entity;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import us.copestudios.copecore.CopeCore;

/**
 * Makes entities indestructible: no damage, no burning, can't be broken (item frames, paintings), rotated,
 * emptied, or have armor taken off. Generalised from CopePortraits' frame protection so every plugin can use it
 * for decorations, NPC holograms, arena props, KOTH markers, and so on.
 *
 * <pre>{@code
 * ItemFrame frame = world.spawn(location, ItemFrame.class);
 * CopeCore.protection().protect(frame);
 *
 * if (CopeCore.protection().isProtected(entity)) { ... }
 * CopeCore.protection().unprotect(frame);   // e.g. before your plugin removes it
 * }</pre>
 *
 * <p>The mark is saved ON the entity (Persistent Data Container), so it survives restarts and chunk reloads
 * with no extra file.</p>
 */
public final class ProtectedEntities implements Listener {

    private final NamespacedKey key;

    public ProtectedEntities(CopeCore core) {
        this.key = new NamespacedKey(core, "protected");
    }

    /** Protects the entity. */
    public void protect(Entity entity) {
        entity.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
    }

    /** Removes the protection. */
    public void unprotect(Entity entity) {
        entity.getPersistentDataContainer().remove(key);
    }

    /** True if the entity is protected. */
    public boolean isProtected(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    // ---------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (isProtected(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCombust(EntityCombustEvent event) {
        if (isProtected(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        if (isProtected(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (isProtected(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (isProtected(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }
}
