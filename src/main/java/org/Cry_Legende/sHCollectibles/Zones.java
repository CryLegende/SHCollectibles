package org.Cry_Legende.sHCollectibles;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** zones.yml : des boîtes par zone. Un collectible appartient à la première zone qui le contient, ou à sa « zone ». */
record Zones(Map<String, List<Boite>> boxes, Map<String, List<Recherche.Found>> items) {

    static Zones read(ConfigurationSection c, java.util.Collection<Collection> collections) {
        Map<String, List<Boite>> boxes = new LinkedHashMap<>();
        Map<String, List<Recherche.Found>> items = new LinkedHashMap<>();
        ConfigurationSection s = c.getConfigurationSection("zones");
        if (s != null) {
            for (String id : s.getKeys(false)) {
                List<Boite> list = new ArrayList<>();
                for (String text : s.getStringList(id)) {
                    list.add(Boite.read(text, "zones." + id));
                }
                boxes.put(id, List.copyOf(list));
                items.put(id, new ArrayList<>());
            }
        }
        for (Collection col : collections) {
            for (Collection.Item i : col.items().values()) {
                String zone = zone(boxes, i);
                if (zone != null) {
                    items.computeIfAbsent(zone, k -> new ArrayList<>()).add(new Recherche.Found(col, i));
                }
            }
        }
        return new Zones(Collections.unmodifiableMap(boxes), Collections.unmodifiableMap(items));
    }

    private static String zone(Map<String, List<Boite>> boxes, Collection.Item i) {
        if (!i.zone().isEmpty()) {
            return i.zone();
        }
        Lieu l = i.place();
        for (Map.Entry<String, List<Boite>> en : boxes.entrySet()) {
            for (Boite b : en.getValue()) {
                if (b.contains(l.world(), l.x(), l.y(), l.z())) {
                    return en.getKey();
                }
            }
        }
        return null;
    }

    /** Collectibles ouverts de la zone. */
    int total(String zone) {
        int n = 0;
        for (Recherche.Found f : items.getOrDefault(zone, List.of())) {
            if (f.collection().enabled()) {
                n++;
            }
        }
        return n;
    }

    int found(String zone, Donnees d) {
        int n = 0;
        for (Recherche.Found f : items.getOrDefault(zone, List.of())) {
            if (f.collection().enabled() && d != null && (d.completed().contains(f.collection().id())
                    || d.read(f.collection().id()).contains(f.item().id()))) {
                n++;
            }
        }
        return n;
    }
}
