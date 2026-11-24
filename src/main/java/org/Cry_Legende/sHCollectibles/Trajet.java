package org.Cry_Legende.sHCollectibles;

import java.util.ArrayList;
import java.util.List;

/**
 * Chemin d'un joueur vers un collectible : longueur à pied, ce qui reste à marcher après chaque double saut, points à suivre
 * (du joueur vers l'avant, entre deux points on va tout droit), prochain double saut, et cases suivantes pour la trace au sol.
 */
record Trajet(double length, List<Double> afterLeaps, List<Lieu> points, Leap leap, List<Lieu> trail) {

    /** Prochain double saut : blocs jusqu'à l'élan, d'où, et où l'on se pose. */
    record Leap(double away, Lieu from, Lieu to, double rise) {

        /** Sommet du vol, « rise » blocs au-dessus du plus haut de ses deux bouts : la flèche et la ligne passent par là. */
        Lieu top() {
            return new Lieu(from.world(), (from.x() + to.x()) / 2, Math.max(from.y(), to.y()) + rise, (from.z() + to.z()) / 2);
        }

        /** Points du vol, du départ à l'arrivée : une courbe par le sommet, « max » points au plus. */
        List<Lieu> line(double gap, int max) {
            Lieu top = top();
            // Point de contrôle : la courbe passe par le sommet en son milieu
            double cx = 2 * top.x() - (from.x() + to.x()) / 2;
            double cy = 2 * top.y() - (from.y() + to.y()) / 2;
            double cz = 2 * top.z() - (from.z() + to.z()) / 2;
            int n = (int) Math.max(2, Math.min(max, Math.ceil((from.distance(top) + top.distance(to)) / gap)));
            List<Lieu> out = new ArrayList<>(n);
            for (int i = 1; i <= n; i++) {
                double t = i / (double) n;
                double a = (1 - t) * (1 - t);
                double b = 2 * t * (1 - t);
                double c = t * t;
                out.add(new Lieu(from.world(), a * from.x() + b * cx + c * to.x(), a * from.y() + b * cy + c * to.y() + 0.2,
                        a * from.z() + b * cz + c * to.z()));
            }
            return out;
        }
    }

    /** Arrêt de sécurité du parcours d'un chemin. */
    private static final int MAX_STEPS = 20_000;

    /** Vrai si un double saut mène encore à « blocks » blocs du collectible au moins. */
    boolean leapLeft(double blocks) {
        for (double left : afterLeaps) {
            if (left >= blocks) {
                return true;
            }
        }
        return false;
    }

    /** Point que l'on atteint en premier, là où l'on va maintenant. */
    Lieu point() {
        return points.get(Math.min(1, points.size() - 1));
    }

    /** « monde x y z x y z … » : ce que lit la flèche. */
    String route() {
        StringBuilder b = new StringBuilder(points.getFirst().world());
        for (Lieu l : points) {
            b.append(' ').append(tenth(l.x())).append(' ').append(tenth(l.y())).append(' ').append(tenth(l.z()));
        }
        return b.toString();
    }

    private static double tenth(double v) {
        return Math.round(v * 10) / 10.0;
    }

    /** null si aucun chemin ne part de cette case. */
    static Trajet from(Reseau r, Reseau.Champ c, int node, String world, double px, double py, double pz, Guidage cfg) {
        if (!c.reaches(node)) {
            return null;
        }
        int look = cfg.walk().lookAhead();
        int[] path = new int[look + 1];
        byte[] kinds = new byte[look + 1];
        int count = 0;
        List<Double> landed = new ArrayList<>(2);
        Leap leap = null;
        double length = Math.hypot(r.x(node) + 0.5 - px, r.z(node) + 0.5 - pz);
        int at = node;
        for (int i = 0; i < MAX_STEPS; i++) {
            int next = r.next(c, at);
            byte kind = r.kind(c, at);
            if (count <= look) {
                kinds[count] = kind;
                path[count++] = at;
            }
            if (next < 0) {
                break;
            }
            boolean jump = kind == Carte.LEAP;
            if (jump) {
                if (leap == null && count <= look) {
                    leap = new Leap(length, place(r, world, at, 0), place(r, world, next, 0), cfg.hint().top());
                }
            }
            length += step(r, at, next);
            if (jump) {
                landed.add(length);
            }
            at = next;
        }
        for (int i = 0; i < landed.size(); i++) {
            landed.set(i, length - landed.get(i));
        }
        return new Trajet(length, landed, points(r, path, kinds, count, world, px, py, pz, cfg.walk().points(), cfg.hint().top()), leap,
                trail(r, path, count, world, cfg.trail()));
    }

    /**
     * Points du chemin : d'abord le joueur, puis chaque endroit où il faut changer de direction (le plus loin où l'on va tout
     * droit), les deux bouts de chaque échelle, chute et saut, et le sommet des doubles sauts.
     */
    private static List<Lieu> points(Reseau r, int[] path, byte[] kinds, int count, String world, double px, double py, double pz, int max, double rise) {
        List<Lieu> out = new ArrayList<>();
        double ax = px;
        double ay = r.y(path[0]);
        double az = pz;
        add(out, new Lieu(world, ax, ay, az));
        if (Math.hypot(r.x(path[0]) + 0.5 - px, r.z(path[0]) + 0.5 - pz) > 1.2) {
            // Pas sur le chemin : d'abord le rejoindre
            add(out, place(r, world, path[0], 0));
            ax = r.x(path[0]) + 0.5;
            az = r.z(path[0]) + 0.5;
        }
        int i = 0;
        while (i + 1 < count && out.size() < max) {
            if (plain(r, path, kinds, i)) {
                int end = i + 1;
                while (end + 1 < count && plain(r, path, kinds, end)) {
                    end++;
                }
                i = straight(r, path, i + 1, end, ax, ay, az);
            } else {
                add(out, place(r, world, path[i], 0));
                if (kinds[i] == Carte.LINK) {
                    break;
                }
                int next = i + 1;
                if (kinds[i] == Carte.LEAP) {
                    add(out, new Leap(0, place(r, world, path[i], 0), place(r, world, path[next], 0), rise).top());
                }
                while (kinds[i] == Carte.WALK && next + 1 < count && kinds[next] == Carte.WALK && r.x(path[next]) == r.x(path[next + 1])
                        && r.z(path[next]) == r.z(path[next + 1])) {
                    next++;
                }
                i = next;
            }
            add(out, place(r, world, path[i], 0));
            ax = r.x(path[i]) + 0.5;
            ay = r.y(path[i]);
            az = r.z(path[i]) + 0.5;
        }
        if (out.size() == 1) {
            add(out, place(r, world, path[count - 1], 0));
        }
        return List.copyOf(out);
    }

    private static void add(List<Lieu> out, Lieu l) {
        if (out.isEmpty() || out.getLast().distance(l) > 0.4) {
            out.add(l);
        }
    }

    private static Lieu place(Reseau r, String world, int node, double up) {
        return new Lieu(world, r.x(node) + 0.5, r.y(node) + up, r.z(node) + 0.5);
    }

    private static List<Lieu> trail(Reseau r, int[] path, int count, String world, Guidage.Trail cfg) {
        if (!cfg.enabled()) {
            return List.of();
        }
        List<Lieu> out = new ArrayList<>();
        double along = 0;
        for (int i = 1; i < count && out.size() < cfg.blocks(); i++) {
            along += step(r, path[i - 1], path[i]);
            if (along >= cfg.start()) {
                out.add(place(r, world, path[i], cfg.height()));
            }
        }
        return out;
    }

    /** Chutes, sauts par-dessus un trou et doubles sauts d'un chemin complet. */
    static int[] jumps(Reseau r, Reseau.Champ c, int node) {
        int[] out = new int[3];
        for (int i = 0; i < MAX_STEPS; i++) {
            int next = r.next(c, node);
            if (next < 0) {
                break;
            }
            switch (r.kind(c, node)) {
                case Carte.FALL -> out[0]++;
                case Carte.GAP -> out[1]++;
                case Carte.LEAP -> out[2]++;
                default -> {
                }
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
    private static boolean plain(Reseau r, int[] path, byte[] kinds, int i) {
        int dx = Math.abs(r.x(path[i]) - r.x(path[i + 1]));
        int dz = Math.abs(r.z(path[i]) - r.z(path[i + 1]));
        return kinds[i] == Carte.WALK && dx <= 1 && dz <= 1 && dx + dz > 0 && Math.abs(r.y(path[i]) - r.y(path[i + 1])) <= 1;
    }

    /** Case la plus loin où l'on va tout droit, entre « least » et « end ». */
    private static int straight(Reseau r, int[] path, int least, int end, double ax, double ay, double az) {
        int low = least;
        int high = end;
        if (low >= high || ok(r, path[high], ax, ay, az)) {
            return high;
        }
        while (high - low > 1) {
            int mid = low + high >>> 1;
            if (ok(r, path[mid], ax, ay, az)) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return low;
    }

    private static boolean ok(Reseau r, int node, double ax, double ay, double az) {
        return r.map.straight(ax, ay, az, r.x(node), r.y(node), r.z(node));
    }
}
