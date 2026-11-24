package org.Cry_Legende.sHCollectibles;

import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** config.yml et collections/, lus une fois par chargement ; tout est immuable ensuite. */
record Reglages(String server, Db db, Set<GameMode> refusedModes, String alias, double particleDistance, int particleTicks,
                String otherWorldCommand, double near, double far, double margin, double stepRadius,
                Actions.Son noise, int noiseTicks, Map<String, Collection> collections) {

    record Db(String host, int port, String database, String user, String password) {
    }

    /** Distance à laquelle le bruit s'entend encore (16 blocs par unité de volume, 16 au moins). */
    double noiseRange() {
        return 16.0 * Math.max(1f, noise.volume());
    }

    /** Collection par son nom, sans tenir compte de la casse ; null si elle n'existe pas. */
    Collection find(String id) {
        Collection exact = collections.get(id);
        if (exact != null) {
            return exact;
        }
        for (Collection c : collections.values()) {
            if (c.id().equalsIgnoreCase(id)) {
                return c;
            }
        }
        return null;
    }

    static String defaultPermission(ConfigurationSection c) {
        return c.getString("permission", "shcollectibles.collection.{collection}");
    }

    static Reglages read(ConfigurationSection c, Map<String, Collection> collections) {
        ConfigurationSection b = need(c, "base-de-donnees");
        ConfigurationSection p = need(c, "particules");
        ConfigurationSection d = need(c, "detecteur");
        ConfigurationSection dist = need(d, "distances");
        Set<GameMode> modes = EnumSet.noneOf(GameMode.class);
        for (String m : c.getStringList("modes-refuses")) {
            try {
                modes.add(GameMode.valueOf(m.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("modes-refuses : mode de jeu inconnu « " + m + " »");
            }
        }
        String server = c.getString("serveur", "").trim();
        if (server.isEmpty() || server.length() > 16) {
            throw new IllegalArgumentException("serveur : un nom de 16 caractères au plus est attendu");
        }
        return new Reglages(server,
                new Db(b.getString("hote", "127.0.0.1"), b.getInt("port", 3306), b.getString("base", "minecraft"),
                        b.getString("utilisateur", ""), b.getString("mot-de-passe", "")),
                modes, c.getString("alias-placeholders", "").trim(), positive(p, "distance", 20), Math.max(1, p.getInt("intervalle-ticks", 5)),
                d.getString("commande-autre-monde", "").trim(), positive(dist, "proche", 15), positive(dist, "loin", 20),
                positive(dist, "changement", 20), positive(dist, "etape", 4),
                Actions.Son.read(d.getString("bruit.son"), "detecteur.bruit.son"), Math.max(10, d.getInt("bruit.intervalle-ticks", 20)),
                Collections.unmodifiableMap(collections));
    }

    private static double positive(ConfigurationSection s, String key, double fallback) {
        double v = s.getDouble(key, fallback);
        if (v < 0) {
            throw new IllegalArgumentException(s.getCurrentPath() + "." + key + " : nombre positif attendu");
        }
        return v;
    }

    private static ConfigurationSection need(ConfigurationSection c, String key) {
        ConfigurationSection s = c.getConfigurationSection(key);
        if (s == null) {
            throw new IllegalArgumentException(key + " : section manquante");
        }
        return s;
    }
}
