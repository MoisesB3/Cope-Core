package us.copestudios.copecore.util;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Particles by NAME, working on every version from 1.20 to the newest 1.21.x. (Added in API 2.)
 *
 * <h2>Why</h2>
 * <p>Minecraft 1.20.5 renamed many particles. Code that writes {@code Particle.LARGE_SMOKE} doesn't even load on
 * 1.20.1 (it's called {@code SMOKE_LARGE} there), and {@code Particle.SMOKE_LARGE} doesn't exist on 1.21. Cope Nights
 * used the new names, so it would have crashed on 1.20.1 - 1.20.4. This class looks the particle up by name and
 * knows both spellings, so you can write either one.</p>
 *
 * <pre>{@code
 * Particles.spawn(player, "LARGE_SMOKE", location, 25, 0.3, 0.7, 0.3, 0.01);  // only this player sees it
 * Particles.spawn(world, "EXPLOSION_EMITTER", location, 1, 0, 0, 0, 0);       // everyone nearby sees it
 * Particle p = Particles.get("HAPPY_VILLAGER");                               // or null if it doesn't exist
 * }</pre>
 *
 * <p>Only particles that need no extra data are drawn by the spawn helpers. DUST, BLOCK, ITEM etc. need data
 * (a color, a block...) - for those, get the Particle with {@link #get} and call Bukkit yourself.</p>
 */
public final class Particles {

    /** New name (1.20.5+) -> old name (1.20 - 1.20.4). Looked up in both directions. */
    private static final Map<String, String> RENAMED = new HashMap<>();

    static {
        rename("LARGE_SMOKE", "SMOKE_LARGE");
        rename("SMOKE", "SMOKE_NORMAL");
        rename("POOF", "EXPLOSION_NORMAL");
        rename("EXPLOSION", "EXPLOSION_LARGE");
        rename("EXPLOSION_EMITTER", "EXPLOSION_HUGE");
        rename("ELDER_GUARDIAN", "MOB_APPEARANCE");
        rename("HAPPY_VILLAGER", "VILLAGER_HAPPY");
        rename("ANGRY_VILLAGER", "VILLAGER_ANGRY");
        rename("ENCHANT", "ENCHANTMENT_TABLE");
        rename("ENCHANTED_HIT", "CRIT_MAGIC");
        rename("WITCH", "SPELL_WITCH");
        rename("EFFECT", "SPELL");
        rename("INSTANT_EFFECT", "SPELL_INSTANT");
        rename("ENTITY_EFFECT", "SPELL_MOB");
        rename("FIREWORK", "FIREWORKS_SPARK");
        rename("TOTEM_OF_UNDYING", "TOTEM");
        rename("RAIN", "WATER_DROP");
        rename("SPLASH", "WATER_SPLASH");
        rename("BUBBLE", "WATER_BUBBLE");
        rename("FISHING", "WATER_WAKE");
        rename("UNDERWATER", "SUSPENDED_DEPTH");
        rename("MYCELIUM", "TOWN_AURA");
        rename("DRIPPING_WATER", "DRIP_WATER");
        rename("DRIPPING_LAVA", "DRIP_LAVA");
        rename("ITEM_SNOWBALL", "SNOWBALL");
        rename("ITEM_SLIME", "SLIME");
        rename("DUST", "REDSTONE");
        rename("ITEM", "ITEM_CRACK");
        rename("BLOCK", "BLOCK_CRACK");
    }

    private Particles() {
        // static helper only
    }

    private static void rename(String newName, String oldName) {
        RENAMED.put(newName, oldName);
        RENAMED.put(oldName, newName);
    }

    /**
     * Finds a particle by its new OR old name ("LARGE_SMOKE" and "SMOKE_LARGE" both work on every version).
     *
     * @return the particle, or null if it doesn't exist on this server version
     */
    public static Particle get(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String upper = name.trim().toUpperCase(Locale.ROOT);
        Particle found = byName(upper);
        if (found == null && RENAMED.containsKey(upper)) {
            found = byName(RENAMED.get(upper));
        }
        return found;
    }

    /** Shows a particle effect to ONE player (others don't see it). Does nothing if the particle doesn't exist. */
    public static void spawn(Player viewer, String name, Location at, int count,
                             double offsetX, double offsetY, double offsetZ, double speed) {
        Particle particle = simple(name);
        if (particle != null && viewer != null && at != null) {
            viewer.spawnParticle(particle, at, count, offsetX, offsetY, offsetZ, speed);
        }
    }

    /** Shows a particle effect to everyone near the location. Does nothing if the particle doesn't exist. */
    public static void spawn(World world, String name, Location at, int count,
                             double offsetX, double offsetY, double offsetZ, double speed) {
        Particle particle = simple(name);
        if (particle != null && world != null && at != null) {
            world.spawnParticle(particle, at, count, offsetX, offsetY, offsetZ, speed);
        }
    }

    /** True if the particle exists on this server (under either name). */
    public static boolean exists(String name) {
        return get(name) != null;
    }

    // ---------------------------------------------------------------------

    /** A particle that needs no extra data, or null. */
    private static Particle simple(String name) {
        Particle particle = get(name);
        return particle != null && particle.getDataType() == Void.class ? particle : null;
    }

    private static Particle byName(String name) {
        try {
            return Particle.valueOf(name);
        } catch (IllegalArgumentException notOnThisVersion) {
            return null;
        }
    }
}
