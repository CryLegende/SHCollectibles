package org.Cry_Legende.sHDetecteur;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChoixTest {

    private static final Choix.Point A = new Choix.Point(1, 0, 64, 0);
    private static final Choix.Point B = new Choix.Point(2, 100, 64, 0);

    private static int id(List<Choix.Point> left, int current, double x) {
        return Choix.target(left, current, x, 64, 0, 20).id();
    }

    @Test
    void lePlusProcheAuDepart() {
        assertEquals(1, id(List.of(A, B), -1, 30));
        assertEquals(2, id(List.of(A, B), -1, 70));
    }

    @Test
    void laHauteurCompte() {
        Choix.Point dessous = new Choix.Point(3, 0, 10, 0);
        assertEquals(2, Choix.target(List.of(dessous, new Choix.Point(2, 30, 64, 0)), -1, 0, 64, 0, 20).id());
    }

    @Test
    void laCibleEstGardeeTantQueLAutreNEstPasNettementPlusProche() {
        assertEquals(1, id(List.of(A, B), 1, 55), "10 blocs d'écart : gardée");
        assertEquals(2, id(List.of(A, B), 1, 70), "40 blocs d'écart : changée");
    }

    @Test
    void cibleTrouveeDoncSuivante() {
        assertEquals(2, id(List.of(B), 1, 0));
    }

    @Test
    void plusRien() {
        assertNull(Choix.target(List.of(), 1, 0, 64, 0, 20));
    }

    @Test
    void procheAvecMarge() {
        assertFalse(Choix.near(false, 15, 15, 20));
        assertTrue(Choix.near(false, 14.9, 15, 20));
        assertTrue(Choix.near(true, 20, 15, 20));
        assertFalse(Choix.near(true, 20.1, 15, 20));
        assertTrue(Choix.near(true, 14, 15, 10), "loin plus petit que proche : proche sert de limite");
    }
}
