package org.Cry_Legende.sHCollectibles;

import java.util.ArrayList;
import java.util.List;

/**
 * Chemin d'un joueur vers un collectible : longueur à pied, point que la flèche doit montrer maintenant (le plus loin où
 * l'on va tout droit), et cases suivantes pour la trace au sol.
 */
record Trajet(double length, Lieu point, List<Lieu> trail) {

    /** Arrêt de sécurité du parcours d'un chemin. */
    private static final int MAX_STEPS = 20_000;

    /** null si aucun chemin ne part de cette case. */
    static Trajet from(Reseau r, Reseau.Champ c, int node, String world, double px, double py, double pz, Guidage cfg) {
        if (!c.reaches(node)) {
            return null;
        }
        int look = cfg.walk().lookAhead();
        int[] path = new int[look + 1];
        int count = 0;
        double length = Math.hypot(r.x(node) + 0.5 - px, r.z(node) + 0.5 - pz);
        int at = node;
        for (int i = 0; i < MAX_STEPS; i++) {
            if (count <= look) {
                path[count++] = at;
            }
            int next = r.next(c, at);
            if (next < 0) {
                break;
            }
            length += step(r, at, next);
            at = next;
        }
        int end = flatEnd(r, path, count);
        int chosen;
        if (end == 0 && count > 1) {
            // Échelle, chute ou saut juste devant : on montre où il mène
            chosen = jumpEnd(r, path, count);
        } else {
            chosen = straight(r, path, end, px, py, pz, cfg.walk().minAhead());
            if (chosen == end && end + 1 < count && near(r, path[end], px, pz)) {
                chosen = jumpEnd(r, path, count, end);
            }
        }
        List<Lieu> trail = new ArrayList<>();
        double along = 0;
        for (int i = 1; i < count && trail.size() < cfg.trail().blocks(); i++) {
            along += step(r, path[i - 1], path[i]);
            if (along >= cfg.trail().start()) {
                trail.add(new Lieu(world, r.x(path[i]) + 0.5, r.y(path[i]) + cfg.trail().height(), r.z(path[i]) + 0.5));
            }
        }
        Lieu point = new Lieu(world, r.x(path[chosen]) + 0.5, r.y(path[chosen]) + 0.5, r.z(path[chosen]) + 0.5);
        return new Trajet(length, point, trail);
    }

    /** Chutes, sauts par-dessus un trou et doubles sauts d'un chemin complet. */
    static int[] jumps(Reseau r, Reseau.Champ c, int node) {
        int[] out = new int[3];
        for (int i = 0; i < MAX_STEPS; i++) {
            int next = r.next(c, node);
            if (next < 0) {
                break;
            }
            int dy = r.y(next) - r.y(node);
            int flat = Math.max(Math.abs(r.x(next) - r.x(node)), Math.abs(r.z(next) - r.z(node)));
            if (flat > 0 && dy > 1 || flat > 4) {
                out[2]++;
            } else if (flat > 1) {
                out[1]++;
            } else if (flat > 0 && dy < -3) {
                out[0]++;
            }
            node = next;
        }
        return out;
    }

    private static double step(Reseau r, int a, int b) {
        double dx = r.x(a) - r.x(b);
        double dy = r.y(a) - r.y(b);
        double dz = r.z(a) - r.z(b);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Pas à pas normal : une case à côté, une marche au plus. */
    private static boolean plain(Reseau r, int a, int b) {
        int dx = Math.abs(r.x(a) - r.x(b));
        int dz = Math.abs(r.z(a) - r.z(b));
        return dx <= 1 && dz <= 1 && dx + dz > 0 && Math.abs(r.y(a) - r.y(b)) <= 1;
    }

    /** Dernière case du chemin atteinte seulement en marchant. */
    private static int flatEnd(Reseau r, int[] path, int count) {
        int end = 0;
        while (end + 1 < count && plain(r, path[end], path[end + 1])) {
            end++;
        }
        return end;
    }

    private static int jumpEnd(Reseau r, int[] path, int count) {
        return jumpEnd(r, path, count, 0);
    }

    /** Fin de l'échelle, de la chute ou du saut qui commence à cette case. */
    private static int jumpEnd(Reseau r, int[] path, int count, int from) {
        int end = from + 1;
        while (end + 1 < count && r.x(path[end]) == r.x(path[end + 1]) && r.z(path[end]) == r.z(path[end + 1])) {
            end++;
        }
        return Math.min(end, count - 1);
    }

    private static boolean near(Reseau r, int node, double px, double pz) {
        return Math.hypot(r.x(node) + 0.5 - px, r.z(node) + 0.5 - pz) < 1.5;
    }

    /** Case la plus loin où l'on va tout droit, mais jamais trop près du joueur. */
    private static int straight(Reseau r, int[] path, int end, double px, double py, double pz, double minAhead) {
        int least = 0;
        while (least < end && Math.hypot(r.x(path[least]) + 0.5 - px, r.z(path[least]) + 0.5 - pz) < minAhead) {
            least++;
        }
        int low = least;
        int high = end;
        if (low >= high || ok(r, path[high], px, py, pz)) {
            return high;
        }
        while (high - low > 1) {
            int mid = low + high >>> 1;
            if (ok(r, path[mid], px, py, pz)) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return low;
    }

    private static boolean ok(Reseau r, int node, double px, double py, double pz) {
        return r.map.straight(px, py, pz, r.x(node), r.y(node), r.z(node));
    }
}
