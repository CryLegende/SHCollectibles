package org.Cry_Legende.sHCollectibles;

import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Ce qui se passe quand un collectible est trouvé (ou la collection complète) : textes, son, effets, commandes. */
record Actions(List<String> chat, String title, String subtitle, int fadeIn, int stay, int fadeOut, Son sound,
               Items items, Hologram hologram, Firework firework, List<String> commands) {

    static final Actions NONE = new Actions(List.of(), "", "", 0, 0, 0, Son.NONE, null, null, null, List.of());

    /** « clé;volume;hauteur ». */
    record Son(String key, float volume, float pitch) {
        static final Son NONE = new Son("", 1f, 1f);

        static Son read(String text, String where) {
            if (text == null || text.isBlank()) {
                return NONE;
            }
            String[] p = text.split(";");
            try {
                return new Son(p[0].trim(), p.length > 1 ? Float.parseFloat(p[1].trim()) : 1f,
                        p.length > 2 ? Float.parseFloat(p[2].trim()) : 1f);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(where + " : « clé;volume;hauteur » attendu, " + text);
            }
        }
    }

    /** Objets qui jaillissent du collectible, un tous les « delay » ticks. */
    record Items(List<Material> materials, int count, int delay, double height, Son sound) {
    }

    record Hologram(List<String> lines, int ticks, double height) {
    }

    record Firework(FireworkEffect.Type type, List<Color> colors, List<Color> fade, double height) {
    }

    static Actions read(ConfigurationSection s, String where) {
        if (s == null) {
            return NONE;
        }
        ConfigurationSection t = s.getConfigurationSection("titre");
        ConfigurationSection e = s.getConfigurationSection("effets");
        return new Actions(List.copyOf(s.getStringList("chat")),
                t == null ? "" : t.getString("texte", ""), t == null ? "" : t.getString("sous-titre", ""),
                t == null ? 0 : t.getInt("apparition", 20), t == null ? 0 : t.getInt("duree", 40), t == null ? 0 : t.getInt("disparition", 20),
                Son.read(s.getString("son"), where + ".son"),
                items(e == null ? null : e.getConfigurationSection("objets"), where + ".effets.objets"),
                hologram(e == null ? null : e.getConfigurationSection("hologramme")),
                firework(e == null ? null : e.getConfigurationSection("feu-artifice"), where + ".effets.feu-artifice"),
                List.copyOf(s.getStringList("commandes")));
    }

    private static Items items(ConfigurationSection s, String where) {
        if (s == null) {
            return null;
        }
        List<Material> materials = new ArrayList<>();
        for (String name : s.getStringList("matieres")) {
            Material m = Material.matchMaterial(name);
            if (m == null || !m.isItem()) {
                throw new IllegalArgumentException(where + ".matieres : objet inconnu « " + name + " »");
            }
            materials.add(m);
        }
        if (materials.isEmpty()) {
            throw new IllegalArgumentException(where + ".matieres : liste vide");
        }
        return new Items(List.copyOf(materials), Math.max(1, Math.min(64, s.getInt("nombre", 20))), Math.max(1, s.getInt("delai", 1)),
                s.getDouble("hauteur", 0.5), Son.read(s.getString("son"), where + ".son"));
    }

    private static Hologram hologram(ConfigurationSection s) {
        if (s == null || s.getStringList("lignes").isEmpty()) {
            return null;
        }
        return new Hologram(List.copyOf(s.getStringList("lignes")), Math.max(1, s.getInt("ticks", 70)), s.getDouble("hauteur", 1.2));
    }

    private static Firework firework(ConfigurationSection s, String where) {
        if (s == null) {
            return null;
        }
        FireworkEffect.Type type;
        try {
            type = FireworkEffect.Type.valueOf(s.getString("forme", "BALL").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(where + ".forme : forme inconnue « " + s.getString("forme") + " »");
        }
        List<Color> colors = colors(s.getStringList("couleurs"), where + ".couleurs");
        if (colors.isEmpty()) {
            throw new IllegalArgumentException(where + ".couleurs : liste vide");
        }
        return new Firework(type, colors, colors(s.getStringList("fondu"), where + ".fondu"), s.getDouble("hauteur", 0.8));
    }

    private static List<Color> colors(List<String> list, String where) {
        List<Color> out = new ArrayList<>();
        for (String hex : list) {
            out.add(Color.fromRGB(rgb(hex, where)));
        }
        return List.copyOf(out);
    }

    /** « #5CFFB0 » ou « &#5CFFB0 ». */
    static int rgb(String hex, String where) {
        try {
            return Integer.parseInt(hex.trim().replace("&", "").replace("#", ""), 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(where + " : couleur illisible « " + hex + " »");
        }
    }
}
