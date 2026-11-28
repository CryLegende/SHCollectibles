package org.Cry_Legende.sHCollectibles;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Collections différentes qu'un joueur peut suivre par jour (0 = sans limite), collectibles vers lesquels le détecteur le
 * mène par jour (0 = sans limite), et heure où le jour change.
 */
record Limite(int perDay, int hour, int items) {

    Limite(int perDay, int hour) {
        this(perDay, hour, 0);
    }

    boolean limited() {
        return perDay > 0 || items > 0;
    }

    LocalDate day(LocalDateTime now) {
        return now.minusHours(hour).toLocalDate();
    }

    /** Minutes avant le jour suivant, arrondies vers le haut. */
    long minutesLeft(LocalDateTime now) {
        return (Duration.between(now, day(now).plusDays(1).atStartOfDay().plusHours(hour)).toSeconds() + 59) / 60;
    }

    /** Une collection déjà suivie dans la journée se reprend librement. */
    boolean allows(Set<String> used, String collection) {
        return perDay <= 0 || used.contains(collection) || used.size() < perDay;
    }

    /** Vrai quand le détecteur a déjà mené le joueur à tous les collectibles de sa journée. */
    boolean full(int guided) {
        return items > 0 && guided >= items;
    }

    /** Collectibles que le détecteur peut encore montrer aujourd'hui (-1 = sans limite). */
    int itemsLeft(int guided) {
        return items <= 0 ? -1 : Math.max(0, items - guided);
    }
}
