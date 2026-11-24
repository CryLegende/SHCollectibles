package org.Cry_Legende.sHCollectibles;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChoixTest {

    private static Lieu l(double x, double y, double z) {
        return new Lieu("spawn", x, y, z);
    }

    private static Collection.Item item(int id, Lieu place, Lieu... steps) {
        return new Collection.Item(id, place, List.of(steps), null);
    }

    /** Slime au fond de la grotte : d'abord la descente du quai, puis la bouche de la grotte. */
    private static final Collection.Item SLIME = item(1, l(-49, 18, 18), l(-8, 67, 78), l(-72, 52, 49));
    /** Orbe sous le hall, entrée par le puits. */
    private static final Collection.Item ORBE = item(3, l(-6, 55, 55), l(-8, 67, 78), l(-33, 50, 60));

    private static int step(Collection.Item i, int passed, double x, double y, double z, boolean covered) {
        return Choix.step(i, passed, x, y, z, covered, 4);
    }

    @Test
    void depuisLeSpawnLaFlecheVaALaDescente() {
        assertEquals(0, step(SLIME, 0, 0, 63, 0, false));
        assertEquals(0, step(ORBE, 0, 0, 63, 0, false));
    }

    @Test
    void justeAuDessusDuCollectibleOnVaQuandMemeALEntree() {
        assertEquals(0, step(ORBE, 0, -7, 68, 55, true), "dans le hall, à l'abri, 13 blocs au-dessus");
        assertEquals(0, step(SLIME, 0, -49, 59, 18, false), "dans la rue au-dessus de la grotte");
    }

    @Test
    void unePremiereEtapePasseePuisLaSuivante() {
        assertEquals(1, step(SLIME, 0, -8, 66, 77, false));
        assertEquals(1, step(SLIME, 1, -30, 56, 74, false));
        assertEquals(2, step(SLIME, 1, -72, 52, 50, false));
    }

    @Test
    void dejaSurLeQuaiLaDescenteEstSautee() {
        assertEquals(1, step(SLIME, 0, -50, 51, 67, false));
    }

    @Test
    void dejaDansLaZoneTouteEtapeEstSautee() {
        assertEquals(2, step(SLIME, 0, -62, 18, 1, true));
        assertEquals(1, step(SLIME, 1, -62, 18, 60, false), "pas à l'abri : on ne croit pas être dans la grotte");
    }

    @Test
    void revenuAuSpawnLesEtapesReviennent() {
        assertEquals(0, step(SLIME, 2, 0, 63, 0, false));
        assertEquals(2, step(SLIME, 2, -73, 37, 12, true), "au milieu de la grotte, rien ne revient");
        assertEquals(1, step(SLIME, 2, -60, 51, 61, false), "ressorti sur le quai : l'entrée de la grotte");
        assertEquals(2, step(SLIME, 2, -72, 50, 40, false), "encore un peu de ciel juste après l'entrée");
    }

    @Test
    void leTonneauDuQuaiEnPleinAirNeRamenePasALaDescente() {
        Collection.Item tonneau = item(2, l(-77, 51, 54), l(-8, 67, 78));
        assertEquals(1, step(tonneau, 0, -8, 68, 78, false));
        assertEquals(1, step(tonneau, 1, -43, 52, 74, false), "sur le quai, sous le ciel, en chemin");
        assertEquals(0, step(tonneau, 1, 0, 63, 0, false));
    }

    @Test
    void sansEtapeLeCollectibleLuiMeme() {
        Collection.Item cookie = item(1, l(-29, 68, -22));
        assertEquals(0, step(cookie, 0, 0, 63, 0, false));
        assertEquals(cookie.steps().size(), step(cookie, 0, 0, 63, 0, false));
    }

    @Test
    void longueurDuTrajetParLesEtapes() {
        Collection.Item i = item(1, l(9.5, 0.5, 0.5), l(0.5, 0.5, 10.5));
        assertEquals(10 + Math.hypot(9, 10), Choix.length(i, 0, 0.5, 0.5, 0.5), 1e-6);
        assertEquals(9, Choix.length(i, 1, 0.5, 0.5, 0.5), 1e-6);
    }

    @Test
    void leTrajetLePlusCourtEtCibleGardee() {
        Choix.Candidate a = new Choix.Candidate(item(1, l(0, 0, 0)), 50);
        Choix.Candidate b = new Choix.Candidate(item(2, l(0, 0, 0)), 40);
        assertEquals(2, Choix.target(List.of(a, b), -1, 20).item().id());
        assertEquals(1, Choix.target(List.of(a, b), 1, 20).item().id(), "10 blocs d'écart : gardée");
        assertEquals(2, Choix.target(List.of(a, b), 1, 5).item().id());
        assertEquals(2, Choix.target(List.of(b), 1, 20).item().id(), "trouvée : suivante");
        assertNull(Choix.target(List.of(), 1, 20));
    }

    @Test
    void procheAvecMarge() {
        assertFalse(Choix.near(false, 15, 15, 20));
        assertTrue(Choix.near(false, 14.9, 15, 20));
        assertTrue(Choix.near(true, 20, 15, 20));
        assertFalse(Choix.near(true, 20.1, 15, 20));
    }
}
