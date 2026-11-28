package org.Cry_Legende.sHCollectibles;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeurreTest {

    /** Anneau de cases autour d'une tête, une par degré. */
    private static List<Leurre.Spot> ring(double distance) {
        List<Leurre.Spot> out = new ArrayList<>();
        for (int d = 0; d < 360; d++) {
            out.add(new Leurre.Spot(d, Math.toRadians(d) - Math.PI, distance));
        }
        return out;
    }

    @Test
    void pointsDansDesDirectionsEcartees() {
        int[] picked = Leurre.pick(ring(4), 3, 5, Leurre.seed("Orbes", 2));
        assertEquals(3, picked.length);
        for (int i = 0; i < 3; i++) {
            for (int j = i + 1; j < 3; j++) {
                double gap = Leurre.gap(Math.toRadians(picked[i]), Math.toRadians(picked[j]));
                assertTrue(gap > Math.toRadians(110), "écart " + Math.toDegrees(gap));
            }
        }
    }

    @Test
    void memesPointsACalculRefait() {
        int[] a = Leurre.pick(ring(4), 3, 5, Leurre.seed("Orbes", 2));
        int[] b = Leurre.pick(ring(4), 3, 5, Leurre.seed("Orbes", 2));
        assertEquals(List.of(a[0], a[1], a[2]), List.of(b[0], b[1], b[2]));
    }

    @Test
    void moinsDeCasesQueDePoints() {
        List<Leurre.Spot> two = List.of(new Leurre.Spot(7, 0, 4), new Leurre.Spot(9, 1, 4));
        assertEquals(2, Leurre.pick(two, 3, 5, 1).length);
        assertEquals(0, Leurre.pick(List.of(), 3, 5, 1).length);
    }

    @Test
    void unJoueurTombeToujoursSurLeMemePoint() {
        UUID p = UUID.fromString("5f6d2a9e-1111-4c1b-9c39-0c7e5a1b2c3d");
        int first = Leurre.index(p, "Slimes", 3, 3);
        for (int i = 0; i < 10; i++) {
            assertEquals(first, Leurre.index(p, "Slimes", 3, 3));
        }
    }

    @Test
    void lesJoueursSeRepartissentSurLesPoints() {
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 40; i++) {
            int k = Leurre.index(UUID.nameUUIDFromBytes(("j" + i).getBytes()), "Slimes", 3, 3);
            assertTrue(k >= 0 && k < 3);
            seen.add(k);
        }
        assertEquals(3, seen.size());
    }

    @Test
    void decalageSansCarteALaBonneDistance() {
        Lieu head = new Lieu("spawn", 10.5, 70.5, -4.5);
        Lieu off = Leurre.offset(head, UUID.nameUUIDFromBytes("x".getBytes()), "Cuivres", 1, 5);
        assertEquals(5, Math.hypot(off.x() - head.x(), off.z() - head.z()), 1e-9);
        assertEquals(head.y(), off.y());
    }
}
