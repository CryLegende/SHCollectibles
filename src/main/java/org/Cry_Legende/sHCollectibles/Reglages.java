package org.Cry_Legende.sHCollectibles;

import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** config.yml, zones.yml et collections/, lus une fois par chargement ; tout est immuable ensuite. */
record Reglages(String server, Db db, Set<GameMode> refusedModes, String alias, double particleDistance, int particleTicks, int orbitSteps,
                double particleSize, String otherWorldCommand, Limite limit, double near, double far, double margin, double teleport, Approx approx, Fouille search, Choix.Rules rules,
                Actions.Son noise, int noiseTicks, double noiseReach, int adminReach, int headCheckSeconds, Guidage guidage, Zones zones,
                Map<String, Collection> collections) {

    record Db(String host, int port, String database, String user, String password, int connections) {
    }

    /**
     * Flèche approchée (detecteur.approximation) : le joueur est mené vers un point à « least » à « spread » blocs à plat du
     * collectible et à « height » blocs de haut au plus, d'où l'on y est en « walk » blocs à pied au plus ; « points » par
     * collectible, chaque joueur tombe toujours sur le même.
     */
    record Approx(double spread, double least, int height, double walk, int points) {
        boolean enabled() {
            return spread > 0 && points > 0;
        }
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

    static Reglages read(ConfigurationSection c, Map<String, Collection> collections, Zones zones) {
        ConfigurationSection b = need(c, "base-de-donnees");
        ConfigurationSection p = need(c, "particules");
        ConfigurationSection d = need(c, "detecteur");
        ConfigurationSection dist = need(d, "distances");
        ConfigurationSection s = need(d, "secours");
        ConfigurationSection n = need(d, "bruit");
        ConfigurationSection lim = need(d, "limite");
        ConfigurationSection ap = d.getConfigurationSection("approximation");
        int hour = lim.getInt("heure", 0);
        if (hour < 0 || hour > 23) {
            throw new IllegalArgumentException("detecteur.limite.heure : de 0 à 23");
        }
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
                        b.getString("utilisateur", ""), b.getString("mot-de-passe", ""), Math.max(1, b.getInt("connexions", 3))),
                modes, c.getString("alias-placeholders", "").trim(), positive(p, "distance", 20), Math.max(1, p.getInt("intervalle-ticks", 5)),
                Math.max(1, p.getInt("orbite-pas", 10)), Math.max(0.1, positive(p, "taille", 1)),
                d.getString("commande-autre-monde", "").trim(), new Limite(Math.max(0, lim.getInt("par-jour", 1)), hour,
                        Math.max(0, lim.getInt("collectibles-par-jour", 0))),
                positive(dist, "proche", 22), positive(dist, "loin", 28),
                positive(dist, "changement", 20), positive(dist, "teleportation", 30), approx(ap), Fouille.read(d.getConfigurationSection("fouille")),
                new Choix.Rules(positive(s, "rayon", 4), positive(s, "hauteur", 4), positive(s, "depasse-hauteur", 3), positive(s, "depasse-part", 0.6),
                        positive(s, "zone-hauteur", 8), positive(s, "zone-part", 0.8), positive(s, "ressorti", 16), positive(s, "retour", 10),
                        s.getInt("abri-lumiere", 4)),
                Actions.Son.read(n.getString("son"), "detecteur.bruit.son"), Math.max(1, n.getInt("intervalle-ticks", 80)),
                positive(n, "portee", 10), Math.max(1, c.getInt("admin-portee", 6)), Math.max(1, d.getInt("verification-secondes", 10)), Guidage.read(c.getConfigurationSection("guidage")), zones,
                Collections.unmodifiableMap(collections));
    }

    /** Sans section : la flèche mène pile au collectible. */
    private static Approx approx(ConfigurationSection s) {
        if (s == null) {
            return new Approx(0, 0, 0, 0, 0);
        }
        double spread = positive(s, "ecart", 5);
        double least = positive(s, "ecart-min", 3);
        if (spread > 0 && least > spread) {
            throw new IllegalArgumentException("detecteur.approximation.ecart-min : pas plus que « ecart »");
        }
        return new Approx(spread, least, Math.max(0, s.getInt("hauteur", 4)), positive(s, "marche-max", 12), Math.max(0, s.getInt("points", 3)));
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
