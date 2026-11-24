package org.Cry_Legende.sHCollectibles;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Lit un dossier players/ de TheSearch (un fichier <uuid>.yml par joueur). Un fichier illisible arrête tout. */
final class ImportTheSearch {

    record Result(List<Magasin.Imported> players, int finds, int completed) {
    }

    private ImportTheSearch() {
    }

    static Result read(File folder) throws Exception {
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            throw new IllegalArgumentException("dossier introuvable, " + folder.getPath());
        }
        List<Magasin.Imported> players = new ArrayList<>();
        int finds = 0;
        int completed = 0;
        for (File f : files) {
            YamlConfiguration y = new YamlConfiguration();
            y.load(f);
            UUID id = UUID.fromString(f.getName().substring(0, f.getName().length() - 4));
            Map<String, List<Integer>> byCollection = new HashMap<>();
            List<String> done = new ArrayList<>();
            ConfigurationSection searches = y.getConfigurationSection("searches");
            if (searches != null) {
                for (String name : searches.getKeys(false)) {
                    List<Integer> numbers = new ArrayList<>();
                    for (String n : searches.getStringList(name + ".finds")) {
                        numbers.add(Integer.parseInt(n.trim()));
                    }
                    byCollection.put(name, numbers);
                    finds += numbers.size();
                    if (searches.getBoolean(name + ".completed")) {
                        done.add(name);
                        completed++;
                    }
                }
            }
            players.add(new Magasin.Imported(id, y.getString("name", ""), byCollection, done));
        }
        return new Result(players, finds, completed);
    }
}
