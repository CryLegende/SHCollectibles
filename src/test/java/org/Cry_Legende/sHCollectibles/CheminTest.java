package org.Cry_Legende.sHCollectibles;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Petits mondes dessinés : un sol plein à y = 0, des murs, et ce qu'on y marche. */
class CheminTest {

    private static final int N = 24;
    private static final int H = 16;

    private static final class Monde {
        final byte[] cells = new byte[N * N * H];
        final List<Boite> forbidden = new ArrayList<>();
        final List<Guidage.Link> links = new ArrayList<>();
        Guidage cfg = config("");

        Monde() {
            box(0, 0, 0, N - 1, 0, N - 1, Blocs.PLEIN);
        }

        Monde box(int x1, int y1, int z1, int x2, int y2, int z2, byte kind) {
            for (int x = x1; x <= x2; x++) {
                for (int y = y1; y <= y2; y++) {
                    for (int z = z1; z <= z2; z++) {
                        cells[(y * N + z) * N + x] = kind;
                    }
                }
            }
            return this;
        }

        Reseau build(int x, int y, int z) {
            Carte map = new Carte(0, 0, 0, N, H, N, cells, cfg.costs(), forbidden);
            return Reseau.build(map, List.of(new int[]{x, y, z}), links, "w");
        }
    }

    private static Guidage config(String moves) {
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.loadFromString("carte: {}\ndeplacements: {" + moves + "}\nsuivi: {}\ntrace: {}\n");
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
        // Les sections vides ne sont pas gardées par YAML : on les crée
        for (String s : List.of("carte", "deplacements", "suivi", "trace")) {
            if (!y.isConfigurationSection(s)) {
                y.createSection(s);
            }
        }
        return Guidage.read(y);
    }

    private static Reseau.Champ to(Reseau r, int x, int y, int z) {
        return r.field(List.of(r.locate(x + 0.5, y, z + 0.5, 2, 0)));
    }

    private static Trajet path(Monde m, Reseau r, Reseau.Champ c, double x, double y, double z) {
        int node = r.locate(x, y, z, 12, 3);
        return node < 0 ? null : Trajet.from(r, c, node, "w", x, y, z, m.cfg);
    }

    private static double cost(Reseau r, Reseau.Champ c, double x, double y, double z) {
        return c.cost(r.locate(x, y, z, 12, 3));
    }

    @Test
    void droitDevant() {
        Monde m = new Monde();
        Reseau r = m.build(2, 1, 2);
        Reseau.Champ c = to(r, 20, 1, 2);
        Trajet t = path(m, r, c, 2.5, 1, 2.5);
        assertEquals(18, t.length(), 0.1);
        assertEquals(new Lieu("w", 20.5, 1, 2.5), t.point(), "tout droit : la flèche montre l'arrivée");
        assertEquals("w 2.5 1.0 2.5 20.5 1.0 2.5", t.route());
    }

    @Test
    void unGrosMurFaitFaireLeTour() {
        Monde m = new Monde().box(10, 1, 0, 14, 10, 20, Blocs.PLEIN);
        Reseau r = m.build(2, 1, 5);
        Reseau.Champ c = to(r, 20, 1, 5);
        Trajet t = path(m, r, c, 2.5, 1, 5.5);
        assertTrue(t.length() > 38, "le tour par le passage du bout, " + t.length());
        assertTrue(t.point().z() > 14, "la flèche part vers le passage, pas vers le mur, " + t.point());
        Trajet derriere = path(m, r, c, 9.5, 1, 5.5);
        assertTrue(derriere.length() > 25, "collé au mur, 10 blocs à vol d'oiseau, mais loin à pied " + derriere.length());
    }

    @Test
    void unEscalierMeneAuDessus() {
        Monde m = new Monde().box(12, 4, 0, N - 1, 4, N - 1, Blocs.PLEIN);
        for (int i = 0; i < 4; i++) {
            m.box(8 + i, 1 + i, 18, 8 + i, 1 + i, 18, Blocs.ESC);
        }
        Reseau r = m.build(2, 1, 2);
        Reseau.Champ c = to(r, 20, 5, 2);
        Trajet t = path(m, r, c, 14.5, 1, 2.5);
        assertNotNull(t);
        assertTrue(t.length() > 30, "juste dessous, il faut d'abord aller à l'escalier, " + t.length());
        assertTrue(t.point().z() > 8, "vers l'escalier, " + t.point());
    }

    @Test
    void uneEchelleEstMonteeEtDescendue() {
        Monde m = new Monde().box(0, 8, 0, N - 1, 8, N - 1, Blocs.PLEIN).box(5, 1, 5, 5, 8, 5, Blocs.GRIMPE);
        m.cfg = config("double-saut: -1");
        Reseau r = m.build(2, 1, 2);
        assertTrue(to(r, 20, 9, 20).reaches(r.locate(2.5, 1, 2.5, 2, 0)), "monte");
        Reseau.Champ down = to(r, 2, 1, 2);
        Trajet t = path(m, r, down, 5.5, 9, 6.5);
        assertNotNull(t, "descend");
        assertTrue(t.point().y() < 3 || t.point().x() == 5.5 && t.point().z() == 5.5, "vers le trou de l'échelle, " + t.point());
    }

    @Test
    void chuteEtRetourImpossible() {
        Monde m = new Monde().box(0, 1, 0, 10, 7, N - 1, Blocs.PLEIN);
        m.cfg = config("double-saut: -1");
        Reseau r = m.build(2, 8, 2);
        assertTrue(to(r, 20, 1, 2).reaches(r.locate(2.5, 8, 2.5, 2, 0)), "on saute en bas");
        assertFalse(to(r, 2, 8, 2).reaches(r.locate(20.5, 1, 2.5, 2, 0)), "on ne remonte pas");
        m.cfg = config("double-saut: -1, chute: -1");
        assertEquals(-1, m.build(2, 8, 2).locate(20.5, 1, 2.5, 2, 0), "chute interdite : le bas n'est pas dans le réseau");
    }

    @Test
    void doubleSautSeulementSIlLeFaut() {
        Monde m = new Monde().box(10, 1, 0, N - 1, 3, N - 1, Blocs.PLEIN);
        Reseau r = m.build(2, 1, 2);
        assertTrue(cost(r, to(r, 20, 4, 2), 2.5, 1, 2.5) > 55, "coûteux mais possible");
        m.box(9, 1, 20, 9, 1, 20, Blocs.PLEIN).box(9, 2, 20, 9, 2, 20, Blocs.VIDE).box(10, 2, 20, 10, 3, 20, Blocs.VIDE).box(11, 3, 20, 11, 3, 20, Blocs.VIDE);
        Reseau s = m.build(2, 1, 2);
        assertTrue(cost(s, to(s, 20, 4, 18), 2.5, 1, 18.5) < 30, "un escalier existe : il est préféré");
    }

    @Test
    void unDoubleSautNeTraversePasUnMur() {
        Monde m = new Monde().box(10, 1, 0, 10, H - 1, N - 1, Blocs.PLEIN);
        Reseau r = m.build(2, 1, 2);
        assertEquals(-1, r.locate(20.5, 1, 2.5, 2, 0));
    }

    @Test
    void doubleSautLongEtEnBiais() {
        Monde m = new Monde().box(6, 0, 0, N - 1, 0, N - 1, Blocs.VIDE).box(13, 0, 8, 16, 0, 12, Blocs.PLEIN);
        m.cfg = config("double-saut-longueur: 9");
        Reseau r = m.build(2, 1, 4);
        assertTrue(r.locate(14.5, 1, 10.5, 2, 0) >= 0, "plateforme à 8 blocs, en biais");
        m.cfg = config("double-saut-longueur: 5");
        assertEquals(-1, m.build(2, 1, 4).locate(14.5, 1, 10.5, 2, 0), "trop loin");
        Monde high = new Monde().box(6, 0, 0, N - 1, 0, N - 1, Blocs.VIDE).box(13, 4, 0, 16, 4, N - 1, Blocs.PLEIN);
        high.cfg = config("double-saut-longueur: 9, double-saut-perte-montee: 1");
        assertEquals(-1, high.build(2, 1, 4).locate(14.5, 5, 10.5, 2, 0), "4 blocs plus haut : 5 blocs de long au plus");
        high.cfg = config("double-saut-longueur: 12, double-saut-perte-montee: 0.75");
        assertTrue(high.build(2, 1, 4).locate(14.5, 5, 10.5, 2, 0) >= 0, "9 blocs de long : atteint");
    }

    /** Une falaise à y 10, le vide, et un pont 8 blocs plus bas. */
    private static Monde falaise() {
        Monde m = new Monde().box(0, 0, 0, N - 1, 0, N - 1, Blocs.VIDE).box(0, 10, 0, 5, 10, N - 1, Blocs.PLEIN)
                .box(13, 2, 0, 20, 2, N - 1, Blocs.PLEIN);
        m.cfg = config("double-saut-longueur: 11");
        return m;
    }

    @Test
    void doubleSautVersUnPontPlusBas() {
        Monde m = falaise();
        Reseau r = m.build(2, 11, 12);
        int deck = r.locate(17.5, 3, 12.5, 2, 0);
        assertTrue(deck >= 0, "le pont est atteint");
        Reseau.Champ c = to(r, 19, 3, 12);
        Trajet t = path(m, r, c, 1.5, 11, 12.5);
        assertEquals(1, t.afterLeaps().size());
        assertTrue(t.afterLeaps().getFirst() > 3, "il reste à marcher sur le pont, " + t.afterLeaps());
        assertTrue(t.leapLeft(3));
        assertFalse(t.leapLeft(15), "un saut qui se pose tout près fait partie de la fouille");
        assertNotNull(t.leap());
        assertTrue(t.leap().from().x() > 3 && t.leap().from().y() == 11, "élan au bord, " + t.leap());
        assertTrue(t.leap().to().x() >= 14.5, "arrivée un peu après le bord du pont, " + t.leap());
        assertTrue(t.leap().away() > 0);
        double highest = t.points().stream().mapToDouble(Lieu::y).max().orElseThrow();
        assertEquals(14, highest, 1e-9, "le chemin passe par le sommet du vol");
        assertEquals(3, t.points().getLast().y(), 1e-9);
        List<Lieu> line = t.leap().line(0.8, 30);
        assertTrue(line.size() > 10 && line.size() <= 30);
        assertEquals(t.leap().to().x(), line.getLast().x(), 1e-9);
        assertTrue(line.stream().mapToDouble(Lieu::y).max().orElseThrow() > 13, "la ligne passe par le haut");
    }

    @Test
    void pasDeDoubleSautDansUneBoiteOuIlEstCoupe() {
        Monde m = falaise();
        m.cfg = config("sans-double-saut: ['w 0 0 0 23 15 23']");
        assertEquals(-1, m.build(2, 11, 12).locate(17.5, 3, 12.5, 2, 0));
    }

    @Test
    void unVolBarreParUnMatNEstPasPropose() {
        Monde m = falaise().box(10, 0, 0, 10, H - 1, N - 1, Blocs.HAUT);
        assertEquals(-1, m.build(2, 11, 12).locate(17.5, 3, 12.5, 2, 0));
    }

    @Test
    void uneArriveeEtroiteCoutePlus() {
        Monde wide = falaise();
        Monde thin = falaise().box(13, 2, 0, 20, 2, N - 1, Blocs.VIDE).box(13, 2, 0, 13, 2, N - 1, Blocs.PLEIN);
        Reseau a = wide.build(2, 11, 12);
        Reseau b = thin.build(2, 11, 12);
        double large = a.field(List.of(a.locate(14.5, 3, 12.5, 2, 0))).cost(a.locate(2.5, 11, 12.5, 2, 0));
        double narrow = b.field(List.of(b.locate(13.5, 3, 12.5, 2, 0))).cost(b.locate(2.5, 11, 12.5, 2, 0));
        assertTrue(narrow > large + 10, narrow + " contre " + large);
    }

    @Test
    void leHautDesArbresEstEvite() {
        Monde m = new Monde().box(6, 0, 0, 14, 0, N - 4, Blocs.FEUILLE);
        Reseau r = m.build(2, 1, 4);
        Trajet t = path(m, r, to(r, 20, 1, 4), 2.5, 1, 4.5);
        assertTrue(t.length() > 35, "contourne les feuilles, " + t.length());
        m.cfg = config("feuillage: 0");
        Reseau s = m.build(2, 1, 4);
        assertEquals(18, path(m, s, to(s, 20, 1, 4), 2.5, 1, 4.5).length(), 0.1);
    }

    @Test
    void unJoueurHorsDuReseauRetrouveLePlusProche() {
        Monde m = new Monde().box(0, 8, 0, 2, 8, 2, Blocs.PLEIN);
        m.cfg = config("double-saut: -1");
        Reseau r = m.build(12, 1, 12);
        assertEquals(-1, r.locate(1.5, 9, 1.5, 4, 1), "sur un toit inaccessible");
        int near = r.near(1.5, 9, 1.5, 10);
        assertTrue(near >= 0 && r.y(near) == 1, "le sol en dessous");
        assertEquals(-1, r.near(1.5, 9, 1.5, 4));
    }

    @Test
    void passageEntreDeuxDemiDalles() {
        Monde m = new Monde().box(10, 1, 0, 10, H - 1, N - 1, Blocs.PLEIN).box(10, 1, 5, 10, 1, 5, Blocs.BAS)
                .box(10, 2, 5, 10, 2, 5, Blocs.VIDE).box(10, 3, 5, 10, 3, 5, Blocs.TOIT);
        assertTrue(m.build(2, 1, 5).locate(20.5, 1, 5.5, 2, 0) >= 0, "dalle basse dessous, dalle haute dessus : 2 blocs");
        m.box(10, 3, 5, 10, 3, 5, Blocs.PLEIN);
        assertEquals(-1, m.build(2, 1, 5).locate(20.5, 1, 5.5, 2, 0), "un bloc plein dessus : 1 bloc et demi");
    }

    @Test
    void trouFranchi() {
        Monde m = new Monde().box(10, 0, 0, 12, 0, N - 1, Blocs.VIDE);
        m.cfg = config("double-saut: -1");
        assertTrue(m.build(2, 1, 2).locate(20.5, 1, 2.5, 2, 0) >= 0, "3 blocs");
        Monde wide = new Monde().box(10, 0, 0, 13, 0, N - 1, Blocs.VIDE);
        wide.cfg = config("double-saut: -1");
        assertEquals(-1, wide.build(2, 1, 2).locate(20.5, 1, 2.5, 2, 0), "4 blocs : trop large");
    }

    @Test
    void porteFermeeDalleEtBarriere() {
        Monde m = new Monde().box(10, 1, 0, 10, 8, N - 1, Blocs.PLEIN).box(10, 1, 5, 10, 2, 5, Blocs.PORTE);
        m.cfg = config("double-saut: -1");
        Reseau r = m.build(2, 1, 5);
        double through = cost(r, to(r, 20, 1, 5), 2.5, 1, 5.5);
        assertEquals(18 + 6, through, 0.5, "la porte coûte 6");
        m.cfg = config("double-saut: -1, porte-fermee: -1");
        assertEquals(-1, m.build(2, 1, 5).locate(20.5, 1, 5.5, 2, 0), "porte infranchissable");
        Monde fence = new Monde().box(10, 1, 0, 10, 1, N - 1, Blocs.HAUT);
        fence.cfg = config("double-saut: -1");
        assertEquals(-1, fence.build(2, 1, 5).locate(20.5, 1, 5.5, 2, 0), "une barrière ne se saute pas");
        Monde slab = new Monde().box(10, 1, 0, 10, 1, N - 1, Blocs.BAS);
        assertTrue(slab.build(2, 1, 5).locate(20.5, 1, 5.5, 2, 0) >= 0, "une dalle se monte");
    }

    @Test
    void interditEtLien() {
        Monde m = new Monde();
        m.forbidden.add(new Boite("w", 8, 0, 0, 12, H, 20));
        Reseau r = m.build(2, 1, 5);
        assertTrue(cost(r, to(r, 20, 1, 5), 2.5, 1, 5.5) > 30, "l'arène est contournée");
        Monde walled = new Monde().box(10, 1, 0, 10, H - 1, N - 1, Blocs.PLEIN);
        walled.links.add(new Guidage.Link(new Lieu("w", 5, 1, 5), new Lieu("w", 18, 1, 5), 4));
        Reseau s = walled.build(2, 1, 5);
        assertTrue(to(s, 20, 1, 5).reaches(s.locate(2.5, 1, 5.5, 2, 0)), "par le portail déclaré");
        assertFalse(to(s, 2, 1, 5).reaches(s.locate(20.5, 1, 5.5, 2, 0)), "un seul sens");
    }

    @Test
    void joueurEnLAirOuSurUnMeuble() {
        Monde m = new Monde();
        Reseau r = m.build(2, 1, 2);
        Reseau.Champ c = to(r, 20, 1, 2);
        assertNotNull(path(m, r, c, 5.5, 6.3, 2.5), "en plein saut ou en chute");
        assertNull(path(m, r, c, 500, 1, 500), "hors de la carte");
    }

    @Test
    void teteAtteinteEtVue() {
        Monde m = new Monde().box(12, 1, 12, 12, 1, 12, Blocs.BAS).box(16, 1, 14, 18, 4, 18, Blocs.PLEIN).box(17, 2, 16, 17, 2, 16, Blocs.BAS);
        Reseau r = m.build(2, 1, 2);
        assertFalse(r.arrivals(new Lieu("w", 12, 1, 12), 3.6, 1.62).isEmpty(), "tête posée au sol");
        assertTrue(r.arrivals(new Lieu("w", 17, 2, 16), 3.6, 1.62).isEmpty(), "tête murée");
        assertTrue(r.closest(new Lieu("w", 17, 2, 16)) >= 0);
    }
}
