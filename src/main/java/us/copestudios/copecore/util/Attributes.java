package us.copestudios.copecore.util;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;

import java.util.Locale;

/**
 * Entity attributes (max health, speed, size...) by NAME, on every version from 1.20 to 1.21.x. (Added in API 2.)
 *
 * <h2>Why</h2>
 * <p>Minecraft 1.21.2 renamed every attribute ("generic.max_health" became "max_health") and Bukkit changed
 * {@code Attribute} from an enum into an interface. Writing {@code Attribute.GENERIC_MAX_HEALTH} breaks on new
 * servers, and {@code Attribute.MAX_HEALTH} doesn't exist on old ones. Looking attributes up by key through the
 * registry works everywhere. Cope Nights and Cope Bosses (AttributeUtil) both needed this.</p>
 *
 * <pre>{@code
 * Attributes.multiplyBase(zombie, "max_health", 1.5);
 * Attributes.setBase(zombie, "movement_speed", 0.3);
 * Attributes.fillHealth(zombie);                       // after raising max health
 *
 * if (Attributes.exists("scale")) {                    // "scale" only exists on 1.20.5+
 *     Attributes.setBase(zombie, "scale", 1.8);
 * }
 * }</pre>
 *
 * <p>Write the key WITHOUT the "generic." part ("max_health", "movement_speed", "attack_damage", "scale",
 * "armor", "knockback_resistance", "follow_range"...). The old "generic.x" spelling is tried automatically.</p>
 */
public final class Attributes {

    private Attributes() {
        // static helper only
    }

    /**
     * The attribute with this key, or null if it doesn't exist on this server version.
     * "max_health" finds "minecraft:max_health" (1.21.2+) or "minecraft:generic.max_health" (older).
     */
    public static Attribute get(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String clean = key.trim().toLowerCase(Locale.ROOT).replace("minecraft:", "").replace("generic.", "");
        for (String candidate : new String[]{clean, "generic." + clean, "player." + clean, "zombie." + clean}) {
            Attribute found = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(candidate));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** True if the attribute exists on this server version (e.g. "scale" needs 1.20.5+). */
    public static boolean exists(String key) {
        return get(key) != null;
    }

    /** The entity's attribute instance, or null if the attribute doesn't exist or the entity doesn't have it. */
    public static AttributeInstance instance(LivingEntity entity, String key) {
        Attribute attribute = get(key);
        return attribute == null || entity == null ? null : entity.getAttribute(attribute);
    }

    /** The base value, or {@code fallback} if the entity doesn't have this attribute. */
    public static double base(LivingEntity entity, String key, double fallback) {
        AttributeInstance instance = instance(entity, key);
        return instance == null ? fallback : instance.getBaseValue();
    }

    /** Sets the base value. Returns false if the entity doesn't have this attribute (nothing happens). */
    public static boolean setBase(LivingEntity entity, String key, double value) {
        AttributeInstance instance = instance(entity, key);
        if (instance == null) {
            return false;
        }
        instance.setBaseValue(value);
        return true;
    }

    /** Multiplies the base value (1.5 = +50%). Returns false if the entity doesn't have this attribute. */
    public static boolean multiplyBase(LivingEntity entity, String key, double multiplier) {
        AttributeInstance instance = instance(entity, key);
        if (instance == null) {
            return false;
        }
        if (multiplier != 1.0) {
            instance.setBaseValue(instance.getBaseValue() * multiplier);
        }
        return true;
    }

    /** Sets the entity's health to its (possibly changed) max health. Call after changing max_health. */
    public static void fillHealth(LivingEntity entity) {
        AttributeInstance max = instance(entity, "max_health");
        if (max != null) {
            entity.setHealth(Math.max(1, max.getValue()));
        }
    }
}
