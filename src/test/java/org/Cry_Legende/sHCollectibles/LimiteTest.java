package org.Cry_Legende.sHCollectibles;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LimiteTest {

    @Test
    void uneCollectionParJourEtLaMemeSeReprend() {
        Limite l = new Limite(1, 0);
        assertTrue(l.allows(Set.of(), "Baies"));
        assertTrue(l.allows(Set.of("Baies"), "Baies"));
        assertFalse(l.allows(Set.of("Baies"), "Cuivres"));
        assertTrue(new Limite(2, 0).allows(Set.of("Baies"), "Cuivres"));
    }

    @Test
    void zeroSansLimite() {
        Limite l = new Limite(0, 0);
        assertFalse(l.limited());
        assertTrue(l.allows(Set.of("Baies", "Cuivres"), "Orbes"));
    }

    @Test
    void collectiblesGuidesParJour() {
        Limite l = new Limite(1, 0, 3);
        assertTrue(l.limited());
        assertFalse(l.full(2));
        assertTrue(l.full(3));
        assertEquals(1, l.itemsLeft(2));
        assertEquals(0, l.itemsLeft(5));
        assertFalse(new Limite(1, 0, 0).full(50));
        assertEquals(-1, new Limite(1, 0, 0).itemsLeft(2));
        // Sans limite de collections, celle des collectibles compte encore
        Limite items = new Limite(0, 0, 3);
        assertTrue(items.limited());
        assertTrue(items.allows(Set.of("Baies", "Cuivres"), "Orbes"));
    }

    @Test
    void leJourChangeALHeureReglee() {
        LocalDateTime night = LocalDateTime.of(2026, 11, 24, 3, 30);
        assertEquals(LocalDate.of(2026, 11, 24), new Limite(1, 0).day(night));
        assertEquals(LocalDate.of(2026, 11, 23), new Limite(1, 6).day(night));
        assertEquals(LocalDate.of(2026, 11, 24), new Limite(1, 6).day(night.plusHours(3)));
    }

    @Test
    void minutesAvantLeJourSuivant() {
        assertEquals(150, new Limite(1, 6).minutesLeft(LocalDateTime.of(2026, 11, 24, 3, 30)));
        assertEquals(1, new Limite(1, 0).minutesLeft(LocalDateTime.of(2026, 11, 24, 23, 59, 30)));
        assertEquals(1440, new Limite(1, 6).minutesLeft(LocalDateTime.of(2026, 11, 24, 6, 0)));
    }
}
