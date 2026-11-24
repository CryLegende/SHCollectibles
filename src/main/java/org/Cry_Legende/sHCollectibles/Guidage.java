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
               List<Shortcut> shortcuts, Costs costs, Walk walk, Trail trail, Hint hint) {

    /** Passage que le plugin ne voit pas (portail, téléporteur, trou à ramper). */
    record Link(Lieu from, Lieu to, double cost) {
    }

    /** Commande qui rapproche du collectible : proposée au-dessus de la flèche quand elle fait gagner « gain » blocs. */
    record Shortcut(Lieu place, String command, double gain) {
    }

    /** Coût de chaque déplacement, en blocs de marche ; négatif = jamais. */
    record Costs(double jump, double gap, double gapDeep, double fall, double fallPerBlock, int fallFree, int highFrom, double high, double climb, double swim, double door, double leaves,
                 Leap leap, int gapMax, int fallMax) {
    }

    /**
     * Double saut : coût, et portée selon la hauteur de l'arrivée (elle baisse quand on se pose plus haut que l'élan, grandit
     * un peu quand on se pose plus bas). « narrow » : coût en plus quand l'arrivée est étroite.
     */
    record Leap(double cost, double perBlock, int height, int length, double riseLoss, double dropGain, double dropGainMax, double narrow,
                List<Boite> without) {

        boolean possible() {
            return cost >= 0;
        }

        /** Longueur la plus grande pour se poser « rise » blocs plus haut (négatif : plus bas). */
        double reach(int rise) {
            return rise > 0 ? length - rise * riseLoss : length + Math.min(dropGainMax, -rise * dropGain);
        }

        boolean allowed(int x, int y, int z) {
            for (Boite b : without) {
                if (b.contains(x, y, z)) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Suite du chemin : cases regardées, points donnés à la flèche, portée pour cliquer une tête ; temps pendant lequel le
     * chemin est gardé en l'air, et hors de toute case connue.
     */
    record Walk(int lookAhead, int points, double reach, int updateTicks, int locateDown, int locateAround, int rejoin, int airTicks, int lostTicks) {
    }

    /** Double saut annoncé à moins de « announce » blocs de l'élan, et sa ligne de points. */
    record Hint(double announce, boolean line, int intervalTicks, double gap, int max, float size, double ring, int rgb) {
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
        ConfigurationSection h = c.getConfigurationSection("double-saut");
        ConfigurationSection line = h == null ? null : h.getConfigurationSection("trace");
        List<Lieu> departures = new ArrayList<>();
        for (String text : m.getStringList("departs")) {
            departures.add(Lieu.read(text, "guidage.carte.departs"));
        }
        List<Boite> forbidden = boxes(m, "interdits", "guidage.carte.interdits");
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
                List.copyOf(departures), materials(m, "blocs-ouverts"), materials(m, "blocs-fermes"), forbidden, List.copyOf(links),
                List.copyOf(shortcuts),
                new Costs(Math.max(0, d.getDouble("saut", 0.5)), Math.max(0, d.getDouble("trou", 3)), Math.max(0, d.getDouble("trou-profond", 15)), d.getDouble("chute", 8),
                        Math.max(0, d.getDouble("chute-par-bloc", 0.5)), Math.max(1, d.getInt("chute-libre", 3)),
                        Math.max(0, d.getInt("chute-haute-des", 12)), Math.max(0, d.getDouble("chute-haute", 1)),
                        Math.max(0.1, d.getDouble("echelle", 1.5)), Math.max(0.1, d.getDouble("nage", 1.5)), d.getDouble("porte-fermee", 6),
                        Math.max(0, d.getDouble("feuillage", 4)),
                        new Leap(d.getDouble("double-saut", 40), Math.max(0, d.getDouble("double-saut-par-bloc", 2.5)),
                                Math.max(1, d.getInt("double-saut-hauteur", 4)), Math.max(1, Math.min(24, d.getInt("double-saut-longueur", 11))),
                                Math.max(0, d.getDouble("double-saut-perte-montee", 0.75)), Math.max(0, d.getDouble("double-saut-gain-descente", 0.16)),
                                Math.max(0, Math.min(12, d.getDouble("double-saut-gain-max", 5))), Math.max(0, d.getDouble("double-saut-etroit", 20)),
                                boxes(d, "sans-double-saut", "guidage.deplacements.sans-double-saut")),
                        Math.max(1, Math.min(6, d.getInt("trou-max", 4))), Math.max(1, d.getInt("chute-max", 60))),
                new Walk(Math.max(4, s.getInt("vue", 48)), Math.max(2, Math.min(40, s.getInt("points-max", 10))), Math.max(1, s.getDouble("portee-clic", 3.6)),
                        Math.max(1, s.getInt("intervalle-ticks", 5)), Math.max(1, s.getInt("recherche-dessous", 12)),
                        Math.max(0, Math.min(6, s.getInt("recherche-autour", 3))), Math.max(0, Math.min(16, s.getInt("retrouve-autour", 10))),
                        Math.max(0, s.getInt("en-l-air-ticks", 80)), Math.max(0, s.getInt("perdu-ticks", 100))),
                new Trail(t.getBoolean("actif", false), Math.max(1, t.getInt("blocs", 12)), Math.max(1, t.getInt("intervalle-ticks", 8)),
                        (float) Math.max(0.1, t.getDouble("taille", 0.8)), t.getDouble("hauteur", 0.3), Math.max(0, t.getDouble("debut", 1.5))),
                new Hint(h == null ? 8 : Math.max(0, h.getDouble("annonce", 8)), line == null || line.getBoolean("actif", true),
                        line == null ? 5 : Math.max(1, line.getInt("intervalle-ticks", 5)), line == null ? 0.8 : Math.max(0.2, line.getDouble("ecart", 0.8)),
                        line == null ? 30 : Math.max(2, Math.min(200, line.getInt("points-max", 30))), line == null ? 1f : (float) Math.max(0.1, line.getDouble("taille", 1)),
                        line == null ? 0.9 : Math.max(0, line.getDouble("cercle", 0.9)), rgb(line)));
    }

    /** Couleur de la ligne de points ; -1 : celle de la collection. */
    private static int rgb(ConfigurationSection line) {
        String hex = line == null ? "" : line.getString("couleur", "");
        return hex.isBlank() ? -1 : Actions.rgb(hex, "guidage.double-saut.trace.couleur");
    }

    private static List<Boite> boxes(ConfigurationSection c, String key, String where) {
        List<Boite> out = new ArrayList<>();
        for (String text : c.getStringList(key)) {
            out.add(Boite.read(text, where));
        }
        return List.copyOf(out);
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
