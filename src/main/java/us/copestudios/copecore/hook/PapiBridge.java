package us.copestudios.copecore.hook;

import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import us.copestudios.copecore.CopeCore;

/**
 * The only class in CopeCore that uses PlaceholderAPI's classes. It's never loaded unless PlaceholderAPI is
 * installed (see {@link PlaceholderService#hook()}), which is what keeps CopeCore working without it.
 */
final class PapiBridge {

    private static CopeExpansion expansion;

    private PapiBridge() {
    }

    static boolean register(CopeCore core, PlaceholderService service) {
        expansion = new CopeExpansion(core, service);
        return expansion.register();
    }

    static void unregister() {
        if (expansion != null) {
            expansion.unregister();
            expansion = null;
        }
    }

    static String apply(Player player, String text) {
        return PlaceholderAPI.setPlaceholders(player, text);
    }

    /** The %cope_...% expansion itself. */
    private static final class CopeExpansion extends PlaceholderExpansion {

        private final CopeCore core;
        private final PlaceholderService service;

        CopeExpansion(CopeCore core, PlaceholderService service) {
            this.core = core;
            this.service = service;
        }

        @Override
        public String getIdentifier() {
            return "cope";
        }

        @Override
        public String getAuthor() {
            return "Cope Studios";
        }

        @Override
        public String getVersion() {
            return core.getDescription().getVersion();
        }

        /** Keep the expansion when someone runs /papi reload (we're registered by a plugin, not downloaded). */
        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public String onRequest(OfflinePlayer player, String params) {
            return service.resolve(player, params);
        }
    }
}
