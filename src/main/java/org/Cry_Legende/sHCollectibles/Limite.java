package org.Cry_Legende.sHCollectibles;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

/** Collections différentes qu'un joueur peut suivre par jour (0 = sans limite), et heure où le jour change. */
record Limite(int perDay, int hour) {

    boolean limited() {
        return perDay > 0;
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
        return !limited() || used.contains(collection) || used.size() < perDay;
    }
}
