package org.Cry_Legende.sHCollectibles;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Une collection : un fichier de collections/. « id » est le nom du fichier, gardé avec les trouvailles. */
record Collection(String id, String name, String object, String color, boolean enabled, String permission, boolean detector,
                  Particles particles, Actions found, Actions complete, Map<Integer, Item> items) {

    /** Point de couleur autour du collectible, ou qui tourne autour à « orbit » bloc ; null = aucun. */
    record Particles(int rgb, double height, double heightMax, double orbit) {
    }

    /**
     * Collectible : « place » est la case du bloc, « steps » les étapes du détecteur (l'entrée de la zone en dernier)
     * avant de viser le collectible lui-même, « found » remplace les actions de la collection (null = celles-ci).
     */
    record Item(int id, Lieu place, List<Lieu> steps, Actions found) {
        Lieu center() {
            return place.centered();
        }
    }

    String coloredName() {
        return color + name;
    }

    String coloredObject() {
        return color + object;
    }

    int total() {
        return items.size();
    }

    Actions actionsFor(Item item) {
        return item.found() == null ? found : item.found();
    }

    static Collection read(String id, ConfigurationSection c, String defaultPermission) {
        Map<Integer, Item> items = new TreeMap<>();
        ConfigurationSection all = c.getConfigurationSection("collectibles");
        if (all != null) {
            for (String key : all.getKeys(false)) {
                ConfigurationSection s = all.getConfigurationSection(key);
                String where = "collectibles." + key;
                int n;
                try {
                    n = Integer.parseInt(key);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(where + " : un numéro est attendu");
                }
                if (s == null) {
                    throw new IllegalArgumentException(where + " : section attendue");
                }
                Lieu place = Lieu.read(s.getString("lieu"), where + ".lieu");
                List<Lieu> steps = new ArrayList<>();
                for (String step : s.getStringList("etapes")) {
                    steps.add(Lieu.read(step, place.world(), where + ".etapes").centered());
                }
                items.put(n, new Item(n, place, List.copyOf(steps),
                        s.isConfigurationSection("trouve") ? Actions.read(s.getConfigurationSection("trouve"), where + ".trouve") : null));
            }
        }
        String object = c.getString("objet", id);
        return new Collection(id, c.getString("nom", id), object, c.getString("couleur", "&f"), c.getBoolean("actif", true),
                c.getString("permission", defaultPermission.replace("{collection}", id)), c.getBoolean("detecteur", true),
                particles(c.getConfigurationSection("particules")),
                Actions.read(c.getConfigurationSection("trouve"), "trouve"), Actions.read(c.getConfigurationSection("complet"), "complet"),
                Collections.unmodifiableMap(items));
    }

    private static Particles particles(ConfigurationSection s) {
        if (s == null) {
            return null;
        }
        double height = s.getDouble("hauteur", 0.7);
        return new Particles(Actions.rgb(s.getString("couleur", "#FFFFFF"), "particules.couleur"), height,
                Math.max(height, s.getDouble("hauteur-max", height)), Math.max(0, s.getDouble("orbite", 0)));
    }

    /**
     * Nombre montré au joueur. Un collectible retiré depuis reste compté à ceux qui l'avaient, sans faire croire que tout
     * est trouvé : il manque toujours un collectible tant que la collection n'est pas complète.
     */
    int shown(Set<Integer> finds, boolean completed) {
        if (completed) {
            return total();
        }
        return Math.max(0, Math.min(finds.size(), hasAll(finds) ? total() : total() - 1));
    }

    boolean hasAll(Set<Integer> finds) {
        return !items.isEmpty() && finds.containsAll(items.keySet());
    }
}
