package org.Cry_Legende.sHCollectibles;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FouilleTest {

    private static Fouille read(String yaml) {
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.loadFromString(yaml);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
        return Fouille.read(y.getConfigurationSection("fouille"));
    }

    @Test
    void niveauxSansHesitation() {
        Fouille f = Fouille.read(null);
        assertEquals(0, f.level(20, -1));
        assertEquals(1, f.level(13.9, 0));
        assertEquals(1, f.level(15.4, 1), "gardé un peu au-delà");
        assertEquals(0, f.level(15.6, 1));
        assertEquals(2, f.level(6, 1));
        assertEquals(2, f.level(8.4, 2));
        assertEquals(1, f.level(8.6, 2));
        assertEquals(0, f.level(50, 2));
    }

    @Test
    void unSeulNiveauNAidePas() {
        Fouille f = read("fouille:\n  niveaux:\n    ici: {distance: 1000, couleur: white, remplissage: 1}\n");
        assertEquals(0, f.level(1, -1));
        assertEquals(0, f.level(20, 0));
    }

    @Test
    void lesNiveauxSontRangesEtVerifies() {
        Fouille f = read("fouille:\n  niveaux:\n    b: {distance: 5, couleur: RED}\n    a: {distance: 100}\n");
        assertEquals("a", f.levels().get(0).id());
        assertEquals(1, f.level(3, -1));
        assertThrows(IllegalArgumentException.class, () -> read("fouille:\n  niveaux:\n    a: {couleur: marron}\n"));
        assertThrows(IllegalArgumentException.class, () -> read("fouille:\n  niveaux:\n    a: {remplissage: 2}\n"));
        assertThrows(IllegalArgumentException.class, () -> read("fouille:\n  barre: {style: RONDE}\n"));
        assertTrue(read("fouille: {actif: false}").levels().size() == 3);
    }
}
