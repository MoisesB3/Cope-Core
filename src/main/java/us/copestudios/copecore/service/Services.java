package us.copestudios.copecore.service;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;

import java.util.Optional;

/**
 * Lets plugins offer features to each other WITHOUT depending on each other directly.
 * A thin, friendly layer over Bukkit's ServicesManager (the same system Vault uses).
 *
 * <h2>How plugins talk to each other</h2>
 * <ol>
 *   <li>Cope Bosses publishes an interface, e.g. {@code BossService { boolean spawn(String id, Location at); }}</li>
 *   <li>Cope Bosses provides it: {@code Services.provide(this, BossService.class, new BossServiceImpl(...));}</li>
 *   <li>Cope Nights (with {@code softdepend: [CopeBosses]}) looks it up during a blood moon:
 *   <pre>{@code
 *   Services.find(BossService.class).ifPresent(bosses -> bosses.spawn("hunter", location));
 *   }</pre>
 *   If Cope Bosses isn't installed, {@code find} returns empty and Cope Nights simply skips the boss.</li>
 * </ol>
 *
 * <p>Services are removed automatically by Bukkit when the providing plugin is disabled.</p>
 */
public final class Services {

    private Services() {
        // static helper only
    }

    /** Makes an implementation available to other plugins. */
    public static <T> void provide(Plugin owner, Class<T> type, T implementation) {
        Bukkit.getServicesManager().register(type, implementation, owner, ServicePriority.Normal);
    }

    /** Finds an implementation another plugin provided, or empty if none is installed/enabled. */
    public static <T> Optional<T> find(Class<T> type) {
        RegisteredServiceProvider<T> registration = Bukkit.getServicesManager().getRegistration(type);
        return registration == null ? Optional.empty() : Optional.ofNullable(registration.getProvider());
    }

    /** Removes a service you provided (normally not needed, it happens on disable). */
    public static <T> void withdraw(Plugin owner, Class<T> type) {
        find(type).ifPresent(implementation -> {
            RegisteredServiceProvider<T> registration = Bukkit.getServicesManager().getRegistration(type);
            if (registration != null && registration.getPlugin().equals(owner)) {
                Bukkit.getServicesManager().unregister(type, implementation);
            }
        });
    }
}
