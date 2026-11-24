package org.Cry_Legende.sHCollectibles;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Section « guidage » de config.yml : la carte des chemins à pied, ce que coûte chaque déplacement, la trace au sol. */
record Guidage(boolean enabled, int startDelaySeconds, int margin, int marginBelow, int marginAbove, int chunksPerTick, int blocksPerTick,
               List<Lieu> departures, Set<Material> open, Set<Material> closed, List<Boite> forbidden, List<Link> links,
               List<Shortcut> shortcuts, Costs costs, Walk walk, Trail trail) {

    /** Passage que le plugin ne voit pas (portail, téléporteur, trou à ramper). */
    record Link(Lieu from, Lieu to, double cost) {
    }

    /** Commande qui rapproche du collectible : proposée au-dessus de la flèche quand elle fait gagner « gain » blocs. */
    record Shortcut(Lieu place, String command, double gain) {
    }

    /** Coût de chaque déplacement, en blocs de marche ; négatif = jamais. */
    record Costs(double jump, double gap, double fall, double fallPerBlock, int fallFree, double climb, double swim, double door,
                 double doubleJump, int doubleJumpHeight, int doubleJumpLength, int gapMax, int fallMax) {
    }

    /** Suite du chemin : distance de vue, éloignement minimal du point montré, portée pour cliquer une tête. */
    record Walk(int lookAhead, double minAhead, double reach, int updateTicks, int locateDown, int locateAround) {
    }

    /** Points de couleur au sol, le long du chemin. */
    record Trail(boolean enabled, int blocks, int intervalTicks, float size, double height, double start) {
    }

    static Guidage read(ConfigurationSection c) {
        if (c == null) {
            throw new IllegalArgumentException("guidage : section manquante");
        }
        ConfigurationSection m = need(c, "carte");
        ConfigurationSection d = need(c, "deplacements");
        ConfigurationSection s = need(c, "suivi");
        ConfigurationSection t = need(c, "trace");
        List<Lieu> departures = new ArrayList<>();
        for (String text : m.getStringList("departs")) {
            departures.add(Lieu.read(text, "guidage.carte.departs"));
        }
        List<Boite> forbidden = new ArrayList<>();
        for (String text : m.getStringList("interdits")) {
            forbidden.add(Boite.read(text, "guidage.carte.interdits"));
        }
        List<Link> links = new ArrayList<>();
        ConfigurationSection l = c.getConfigurationSection("liens");
        if (l != null) {
            for (String id : l.getKeys(false)) {
                String where = "guidage.liens." + id;
                links.add(new Link(Lieu.read(l.getString(id + ".de"), where + ".de"), Lieu.read(l.getString(id + ".vers"), where + ".vers"),
                        Math.max(0.1, l.getDouble(id + ".cout", 5))));
            }
        }
        List<Shortcut> shortcuts = new ArrayList<>();
        ConfigurationSection r = c.getConfigurationSection("raccourcis");
        if (r != null) {
            for (String id : r.getKeys(false)) {
                String where = "guidage.raccourcis." + id;
                shortcuts.add(new Shortcut(Lieu.read(r.getString(id + ".lieu"), where + ".lieu"), r.getString(id + ".commande", id),
                        Math.max(0, r.getDouble(id + ".gain", 60))));
            }
        }
        return new Guidage(c.getBoolean("actif", true), Math.max(1, m.getInt("delai-demarrage-secondes", 60)), Math.max(0, m.getInt("marge", 40)),
                Math.max(0, m.getInt("marge-dessous", 20)), Math.max(0, m.getInt("marge-dessus", 30)), Math.max(1, m.getInt("troncons-par-tick", 4)),
                Math.max(1, m.getInt("blocs-par-tick", 400)),
                List.copyOf(departures), materials(m, "blocs-ouverts"), materials(m, "blocs-fermes"), List.copyOf(forbidden), List.copyOf(links),
                List.copyOf(shortcuts),
                new Costs(Math.max(0, d.getDouble("saut", 0.5)), Math.max(0, d.getDouble("trou", 1.5)), d.getDouble("chute", 20),
                        Math.max(0, d.getDouble("chute-par-bloc", 0.5)), Math.max(1, d.getInt("chute-libre", 3)),
                        Math.max(0.1, d.getDouble("echelle", 1.5)), Math.max(0.1, d.getDouble("nage", 1.5)), d.getDouble("porte-fermee", 6),
                        d.getDouble("double-saut", 120), Math.max(1, d.getInt("double-saut-hauteur", 4)),
                        Math.max(1, d.getInt("double-saut-longueur", 9)), Math.max(1, Math.min(6, d.getInt("trou-max", 4))),
                        Math.max(1, d.getInt("chute-max", 60))),
                new Walk(Math.max(4, s.getInt("vue", 48)), Math.max(0, s.getDouble("avance-min", 3)), Math.max(1, s.getDouble("portee-clic", 3.6)),
                        Math.max(1, s.getInt("intervalle-ticks", 5)), Math.max(1, s.getInt("recherche-dessous", 12)),
                        Math.max(0, Math.min(6, s.getInt("recherche-autour", 3)))),
                new Trail(t.getBoolean("actif", false), Math.max(1, t.getInt("blocs", 12)), Math.max(1, t.getInt("intervalle-ticks", 8)),
                        (float) Math.max(0.1, t.getDouble("taille", 0.8)), t.getDouble("hauteur", 0.3), Math.max(0, t.getDouble("debut", 1.5))));
    }

    private static Set<Material> materials(ConfigurationSection c, String key) {
        Set<Material> out = EnumSet.noneOf(Material.class);
        for (String name : c.getStringList(key)) {
            Material m = Material.matchMaterial(name);
            if (m == null || !m.isBlock()) {
                throw new IllegalArgumentException("guidage.carte." + key + " : bloc inconnu « " + name + " »");
            }
            out.add(m);
        }
        return out;
    }

    private static ConfigurationSection need(ConfigurationSection c, String key) {
        ConfigurationSection s = c.getConfigurationSection(key);
        if (s == null) {
            throw new IllegalArgumentException("guidage." + key + " : section manquante");
        }
        return s;
    }
}
