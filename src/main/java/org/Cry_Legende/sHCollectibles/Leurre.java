package org.Cry_Legende.sHCollectibles;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Flèche approchée : au lieu du collectible, chaque joueur est mené vers un point à quelques blocs de lui. Les points d'un
 * collectible sont pris tout autour (directions bien écartées) ; un joueur tombe toujours sur le même, il ne gagne donc rien
 * à relancer le détecteur.
 */
final class Leurre {

    /** Une case possible : son numéro dans le réseau, sa direction depuis la tête (radians) et sa distance à plat. */
    record Spot(int node, double angle, double distance) {
    }

    private Leurre() {
    }

    /**
     * « count » cases aux directions les plus écartées possible, en partant d'une direction tirée de « seed » ; à direction
     * égale, la plus proche de « spread ». Moins si les cases manquent.
     */
    static int[] pick(List<Spot> spots, int count, double spread, long seed) {
        List<Spot> left = new ArrayList<>(spots);
        int n = Math.min(count, left.size());
        int[] out = new int[n];
        double base = new Random(seed).nextDouble() * 2 * Math.PI;
        for (int k = 0; k < n; k++) {
            double want = base + k * 2 * Math.PI / n;
            Spot best = null;
            double bestScore = Double.MAX_VALUE;
            for (Spot s : left) {
                double score = gap(s.angle(), want) + 0.01 * Math.abs(spread - s.distance());
                if (score < bestScore) {
                    bestScore = score;
                    best = s;
                }
            }
            out[k] = best.node();
            left.remove(best);
        }
        return out;
    }

    /** Écart entre deux directions, de 0 à π. */
    static double gap(double a, double b) {
        double d = Math.abs(a - b) % (2 * Math.PI);
        return d > Math.PI ? 2 * Math.PI - d : d;
    }

    /** Point d'un joueur parmi « count » : toujours le même pour ce joueur et ce collectible. */
    static int index(UUID player, String collection, int item, int count) {
        long h = player.getMostSignificantBits() * 31 + player.getLeastSignificantBits();
        h = h * 31 + collection.hashCode();
        h = h * 31 + item;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        return (int) Math.floorMod(h, (long) count);
    }

    /** Graine des points d'un collectible : les mêmes à chaque calcul de la carte. */
    static long seed(String collection, int item) {
        return collection.hashCode() * 1_000_003L + item;
    }

    /** Sans carte : le collectible décalé de « spread » blocs à plat, dans une direction propre au joueur. */
    static Lieu offset(Lieu center, UUID player, String collection, int item, double spread) {
        double a = index(player, collection, item, 3600) * Math.PI / 1800;
        return new Lieu(center.world(), center.x() + Math.cos(a) * spread, center.y(), center.z() + Math.sin(a) * spread);
    }
}
