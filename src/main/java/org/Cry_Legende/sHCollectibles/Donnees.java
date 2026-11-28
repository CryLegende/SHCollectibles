package org.Cry_Legende.sHCollectibles;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Trouvailles d'un joueur connecté. Lues par les placeholders depuis n'importe quel fil. */
final class Donnees {

    private final Map<String, Set<Integer>> finds = new ConcurrentHashMap<>();
    private final Set<String> completed = ConcurrentHashMap.newKeySet();
    private LocalDate trackedDay;
    private Set<String> tracked = Set.of();
    private LocalDate guidedDay;
    private Set<String> guided = Set.of();

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

    /** Clé d'un collectible dans la liste des guidés. */
    static String key(String collection, int number) {
        return collection + "/" + number;
    }

    /** Collectibles vers lesquels le détecteur a mené le joueur ce jour-là (« collection/numéro »). */
    synchronized Set<String> guided(LocalDate day) {
        return day.equals(guidedDay) ? guided : Set.of();
    }

    synchronized void setGuided(LocalDate day, Set<String> keys) {
        guidedDay = day;
        guided = Set.copyOf(keys);
    }

    /** Ajoute un collectible guidé ; faux s'il l'était déjà ce jour-là. */
    synchronized boolean guide(LocalDate day, String key) {
        Set<String> now = new HashSet<>(guided(day));
        if (!now.add(key)) {
            return false;
        }
        setGuided(day, now);
        return true;
    }
}
