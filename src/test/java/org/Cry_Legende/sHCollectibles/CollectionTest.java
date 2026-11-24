package org.Cry_Legende.sHCollectibles;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectionTest {

    private static final String YAML = """
            nom: "Orbes"
            objet: "Orbe"
            couleur: "&a"
            particules:
              couleur: "#00FFFF"
              hauteur: 0.7
            trouve:
              chat:
                - "bravo {trouves}/{total}"
              son: "minecraft:block.lava.pop;20;2"
              commandes:
                - "give {joueur} x 1"
            collectibles:
              1:
                lieu: "spawn -9 42 54"
                etapes:
                  - "-8 67 78"
                  - "-33 50 60"
              3:
                lieu: "spawn -6.0 55.0 55.0"
                trouve:
                  commandes:
                    - "autre"
            """;

    private static Collection read(String yaml) {
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.loadFromString(yaml);
        } catch (InvalidConfigurationException e) {
            throw new IllegalArgumentException(e);
        }
        return Collection.read("Orbes", y, "thesearch.search.{collection}");
    }

    @Test
    void lecture() {
        Collection c = read(YAML);
        assertEquals("&aOrbes", c.coloredName());
        assertEquals("thesearch.search.Orbes", c.permission());
        assertEquals(2, c.total());
        assertEquals(0x00FFFF, c.particles().rgb());
        assertEquals(0x55FF55, c.trailColor(), "couleur du jeu");
        assertEquals("", c.items().get(1).zone());
        Collection.Item one = c.items().get(1);
        assertEquals(new Lieu("spawn", -8.5, 42.5, 54.5), one.center());
        assertEquals(2, one.steps().size());
        assertEquals(new Lieu("spawn", -32.5, 50.5, 60.5), one.steps().get(1));
        assertEquals("give {joueur} x 1", c.actionsFor(one).commands().get(0));
        assertEquals("autre", c.actionsFor(c.items().get(3)).commands().get(0));
        assertEquals(2f, c.found().sound().pitch());
        assertNull(c.found().items());
    }

    @Test
    void permissionVideOuvreATous() {
        assertEquals("", read(YAML + "permission: \"\"\n").permission());
    }

    @Test
    void erreursNommees() {
        assertTrue(assertThrows(IllegalArgumentException.class, () -> read(YAML.replace("spawn -9 42 54", "spawn -9 42")))
                .getMessage().contains("collectibles.1.lieu"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> read(YAML.replace("#00FFFF", "#ZZ")))
                .getMessage().contains("particules.couleur"));
    }

    @Test
    void compteMontre() {
        Collection c = read(YAML);
        assertEquals(0, c.shown(Set.of(), false));
        assertEquals(1, c.shown(Set.of(3), false));
        assertEquals(2, c.shown(Set.of(1, 3), false));
        assertEquals(2, c.shown(Set.of(), true), "complète : tout est compté");
        assertEquals(1, c.shown(Set.of(1, 2), false), "le 2 n'existe plus : il manque toujours une tête");
        assertEquals(1, c.shown(Set.of(2), false), "une tête retirée depuis reste comptée");
        assertTrue(c.hasAll(Set.of(1, 2, 3)));
        assertFalse(c.hasAll(Set.of(1, 2)));
    }
}
