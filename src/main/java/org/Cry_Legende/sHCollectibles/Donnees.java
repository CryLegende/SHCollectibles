package org.Cry_Legende.sHCollectibles;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Trouvailles d'un joueur connecté. Lues par les placeholders depuis n'importe quel fil. */
final class Donnees {

    private final Map<String, Set<Integer>> finds = new ConcurrentHashMap<>();
    private final Set<String> completed = ConcurrentHashMap.newKeySet();
    private LocalDate trackedDay;
    private Set<String> tracked = Set.of();

    Set<Integer> finds(String collection) {
        return finds.computeIfAbsent(collection, k -> ConcurrentHashMap.newKeySet());
    }

    /** Sans rien créer : vide si le joueur n'a rien trouvé dans la collection. */
    Set<Integer> read(String collection) {
        return finds.getOrDefault(collection, Set.of());
    }

    Set<String> completed() {
        return completed;
    }

    /** Collections suivies au détecteur ce jour-là. */
    synchronized Set<String> tracked(LocalDate day) {
        return day.equals(trackedDay) ? tracked : Set.of();
    }

    synchronized void track(LocalDate day, Set<String> collections) {
        trackedDay = day;
        tracked = Set.copyOf(collections);
    }
}
