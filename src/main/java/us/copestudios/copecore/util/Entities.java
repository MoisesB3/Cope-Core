package us.copestudios.copecore.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Consumer;

/**
 * Spawning entities that are set up BEFORE they appear, on every version from 1.20 up.
 *
 * <h2>Why this exists</h2>
 * <p>{@code world.spawn(location, Zombie.class, zombie -> {...})} runs your code before the entity is added to the
 * world, so nobody ever sees it half set up (no flash of a visible "hidden" entity, no default name...).
 * But Bukkit changed that method's signature:</p>
 * <ul>
 *   <li>1.20 - 1.20.1: the last parameter is {@code org.bukkit.util.Consumer}</li>
 *   <li>1.20.2+: it's {@code java.util.function.Consumer} (and the old one was removed later)</li>
 * </ul>
 * <p>A plugin compiled against 1.20.1 calling the old one crashes with NoSuchMethodError on 1.21, and one compiled
 * against 1.21 crashes on 1.20.1. This helper finds whichever version the server has, once, and calls it.</p>
 *
 * <pre>{@code
 * Skeleton body = Entities.spawn(location, Skeleton.class, skeleton -> {
 *     skeleton.setAI(false);
 *     skeleton.setVisibleByDefault(false);
 * });
 * }</pre>
 */
public final class Entities {

    /** World#spawn(Location, Class, java.util.function.Consumer) - 1.20.2+ */
    private static final Method SPAWN_NEW = find("java.util.function.Consumer");
    /** World#spawn(Location, Class, org.bukkit.util.Consumer) - 1.20 / 1.20.1 */
    private static final Method SPAWN_OLD = SPAWN_NEW == null ? find("org.bukkit.util.Consumer") : null;
    /** The old org.bukkit.util.Consumer interface (null on servers that removed it). */
    private static final Class<?> OLD_CONSUMER = SPAWN_OLD == null ? null : SPAWN_OLD.getParameterTypes()[2];

    private Entities() {
        // static helper only
    }

    /**
     * Spawns an entity and runs {@code setup} on it before it's added to the world.
     *
     * @param at    where (must have a world)
     * @param type  entity class, e.g. {@code Zombie.class}
     * @param setup your changes (may be null)
     * @return the spawned entity
     */
    public static <T extends Entity> T spawn(Location at, Class<T> type, Consumer<? super T> setup) {
        World world = at.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Location has no world");
        }
        Consumer<? super T> action = setup == null ? entity -> { } : setup;
        try {
            if (SPAWN_NEW != null) {
                return type.cast(SPAWN_NEW.invoke(world, at, type, action));
            }
            if (SPAWN_OLD != null) {
                // Make an org.bukkit.util.Consumer that forwards to our java Consumer.
                Object oldConsumer = Proxy.newProxyInstance(OLD_CONSUMER.getClassLoader(), new Class<?>[]{OLD_CONSUMER},
                        (proxy, method, args) -> {
                            if (method.getName().equals("accept") && args != null && args.length == 1) {
                                action.accept(type.cast(args[0]));
                                return null;
                            }
                            // equals / hashCode / toString on the proxy itself
                            return switch (method.getName()) {
                                case "equals" -> proxy == args[0];
                                case "hashCode" -> System.identityHashCode(proxy);
                                default -> "CopeCore spawn consumer";
                            };
                        });
                return type.cast(SPAWN_OLD.invoke(world, at, type, oldConsumer));
            }
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error fatal) {
                throw fatal;
            }
            throw new IllegalStateException(cause);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException(error);
        }
        // Neither found (should never happen): spawn first, then set up in the same tick.
        T entity = world.spawn(at, type);
        action.accept(entity);
        return entity;
    }

    private static Method find(String consumerClass) {
        try {
            return World.class.getMethod("spawn", Location.class, Class.class, Class.forName(consumerClass));
        } catch (ClassNotFoundException | NoSuchMethodException missing) {
            return null;
        }
    }
}
