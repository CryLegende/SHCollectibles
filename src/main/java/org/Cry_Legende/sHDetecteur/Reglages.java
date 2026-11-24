package org.Cry_Legende.sHDetecteur;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** config.yml lu une fois par chargement ; tout est immuable ensuite. */
record Reglages(String otherWorldCommand, double near, double far, double margin, int noiseTicks,
                Map<String, Collectible> collections) {

    /** Collection avec un détecteur ; « id » est le nom de la recherche TheSearch. */
    record Collectible(String id, String color, String name, String object, String sound, float volume, float pitch) {

        String coloredName() {
            return color + name;
        }

        String coloredObject() {
            return color + object;
        }

        /** Distance à laquelle le bruit s'entend encore (16 blocs par unité de volume, 16 au moins). */
        double range() {
            return 16.0 * Math.max(1f, volume);
        }
    }

    /** Collection par son nom, sans tenir compte de la casse ; null si elle n'a pas de détecteur. */
    Collectible find(String id) {
        Collectible exact = collections.get(id);
        if (exact != null) {
            return exact;
        }
        for (Collectible c : collections.values()) {
            if (c.id().equalsIgnoreCase(id)) {
                return c;
            }
        }
        return null;
    }

    static Reglages read(ConfigurationSection c) {
        ConfigurationSection d = need(c, "distances");
        ConfigurationSection b = need(c, "bruit");
        ConfigurationSection all = need(c, "collections");
        float volume = (float) b.getDouble("volume", 2);
        float pitch = (float) b.getDouble("hauteur", 1);
        Map<String, Collectible> out = new LinkedHashMap<>();
        for (String id : all.getKeys(false)) {
            ConfigurationSection s = all.getConfigurationSection(id);
            if (s == null) {
                throw new IllegalArgumentException("collections." + id + " : section attendue");
            }
            out.put(id, new Collectible(id, s.getString("couleur", "&f"), s.getString("nom", id), s.getString("objet", id),
                    s.getString("bruit", ""), (float) s.getDouble("volume", volume), (float) s.getDouble("hauteur", pitch)));
        }
        return new Reglages(c.getString("commande-autre-monde", "").trim(), positive(d, "proche", 15), positive(d, "loin", 20),
                positive(d, "changement", 20), Math.max(10, b.getInt("intervalle-ticks", 40)),
                Collections.unmodifiableMap(out));
    }

    private static double positive(ConfigurationSection d, String key, double fallback) {
        double v = d.getDouble(key, fallback);
        if (v < 0) {
            throw new IllegalArgumentException("distances." + key + " : nombre positif attendu");
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
