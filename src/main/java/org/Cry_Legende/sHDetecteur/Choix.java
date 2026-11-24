package org.Cry_Legende.sHDetecteur;

import java.util.List;

/** Choix du collectible à montrer et état « tout près ». */
final class Choix {

    /** Collectible pas encore trouvé, dans le monde du joueur. */
    record Point(int id, double x, double y, double z) {

        double distance(double px, double py, double pz) {
            double dx = x - px;
            double dy = y - py;
            double dz = z - pz;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    private Choix() {
    }

    /**
     * Le plus proche ; la cible actuelle est gardée tant qu'aucun autre n'est plus proche d'au moins « margin » blocs,
     * pour que la flèche n'hésite pas entre deux collectibles. null si la liste est vide.
     */
    static Point target(List<Point> left, int current, double px, double py, double pz, double margin) {
        Point nearest = null;
        Point kept = null;
        double best = Double.MAX_VALUE;
        for (Point p : left) {
            double d = p.distance(px, py, pz);
            if (d < best) {
                best = d;
                nearest = p;
            }
            if (p.id() == current) {
                kept = p;
            }
        }
        return kept != null && kept.distance(px, py, pz) - best < margin ? kept : nearest;
    }

    /** Tout près en dessous de « near » ; une fois tout près, il faut dépasser « far » pour que la flèche revienne. */
    static boolean near(boolean wasNear, double distance, double near, double far) {
        return wasNear ? distance <= Math.max(near, far) : distance < near;
    }
}
