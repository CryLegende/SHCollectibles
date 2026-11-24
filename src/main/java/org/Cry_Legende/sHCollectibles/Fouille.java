package org.Cry_Legende.sHCollectibles;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Zone de fouille (detecteur.fouille) : près du collectible, la flèche laisse la place à une barre qui dit seulement si l'on
 * chauffe. Niveaux du plus loin au plus proche.
 */
record Fouille(boolean enabled, double afterLeap, int fadeIn, int stay, int fadeOut, Actions.Son sound, boolean bar, BossBar.Overlay overlay,
               double margin, List<Level> levels) {

    /** Niveau affiché à moins de « distance » blocs à marcher. */
    record Level(String id, double distance, BossBar.Color color, float fill) {
    }

    private static final List<Level> DEFAULT = List.of(new Level("tiede", 1000, BossBar.Color.BLUE, 0.33f),
            new Level("chaud", 14, BossBar.Color.YELLOW, 0.66f), new Level("brulant", 7, BossBar.Color.RED, 1f));

    static Fouille read(ConfigurationSection c) {
        ConfigurationSection t = c == null ? null : c.getConfigurationSection("titre");
        ConfigurationSection b = c == null ? null : c.getConfigurationSection("barre");
        ConfigurationSection n = c == null ? null : c.getConfigurationSection("niveaux");
        return new Fouille(c == null || c.getBoolean("actif", true), c == null ? 5 : c.getDouble("apres-double-saut", 5),
                t == null ? 5 : Math.max(0, t.getInt("apparition", 5)), t == null ? 45 : Math.max(0, t.getInt("duree", 45)),
                t == null ? 15 : Math.max(0, t.getInt("disparition", 15)),
                Actions.Son.read(c == null ? "minecraft:item.spyglass.use;1;1" : c.getString("son", "minecraft:item.spyglass.use;1;1"), "detecteur.fouille.son"),
                b == null || b.getBoolean("actif", true), overlay(b == null ? "NOTCHED_6" : b.getString("style", "NOTCHED_6")),
                c == null ? 1.5 : Math.max(0, c.getDouble("marge", 1.5)), n == null ? DEFAULT : levels(n));
    }

    private static List<Level> levels(ConfigurationSection n) {
        List<Level> out = new ArrayList<>();
        for (String id : n.getKeys(false)) {
            String where = "detecteur.fouille.niveaux." + id;
            double fill = n.getDouble(id + ".remplissage", 1);
            if (fill < 0 || fill > 1) {
                throw new IllegalArgumentException(where + ".remplissage : de 0 à 1");
            }
            out.add(new Level(id, n.getDouble(id + ".distance", 1000), color(n.getString(id + ".couleur", "BLUE"), where), (float) fill));
        }
        if (out.isEmpty()) {
            throw new IllegalArgumentException("detecteur.fouille.niveaux : un niveau au moins");
        }
        out.sort(Comparator.comparingDouble(Level::distance).reversed());
        return List.copyOf(out);
    }

    private static BossBar.Color color(String name, String where) {
        try {
            return BossBar.Color.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(where + ".couleur : couleur de barre inconnue « " + name + " »");
        }
    }

    private static BossBar.Overlay overlay(String name) {
        try {
            return BossBar.Overlay.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("detecteur.fouille.barre.style : style inconnu « " + name + " »");
        }
    }

    /** Niveau à cette distance ; le niveau actuel (-1 : aucun) est gardé jusqu'à « margin » blocs au-delà de sa limite. */
    int level(double distance, int current) {
        int level = 0;
        for (int i = 1; i < levels.size(); i++) {
            double limit = levels.get(i).distance() + (current >= i ? margin : 0);
            if (distance <= limit) {
                level = i;
            }
        }
        return level;
    }
}
