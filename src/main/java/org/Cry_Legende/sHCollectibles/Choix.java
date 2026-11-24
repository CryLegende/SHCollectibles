package org.Cry_Legende.sHCollectibles;

import java.util.List;

/** Choix du collectible à montrer, étape vers laquelle aller, et état « tout près ». */
final class Choix {

    /** Écart de hauteur toléré avec le collectible pour dire que le joueur est déjà dans sa zone. */
    private static final double LEVEL = 8;
    /** Passer une étape : à cette hauteur près (une entrée au sol se passe en marchant dessus). */
    private static final double STEP_HEIGHT = 4;
    /** Être déjà plus loin qu'une étape : presque à la hauteur de la suivante, et nettement plus près d'elle. */
    private static final double BEYOND_HEIGHT = 3;
    private static final double BEYOND = 0.6;
    /** Revenir à l'entrée : ressorti sous le ciel à plus de cette distance d'elle, sans être en chemin vers le collectible. */
    private static final double OUTSIDE = 16;
    /** Revenir à une étape : s'être éloigné de la suivante de plus que cette étape, à cette marge près. */
    private static final double BACK = 10;

    /** Collectible à trouver, avec la longueur du trajet qui reste (par ses étapes). */
    record Candidate(Collection.Item item, double length) {
    }

    private Choix() {
    }

    /**
     * Le trajet le plus court ; la cible actuelle est gardée tant qu'aucune autre n'est plus proche d'au moins « margin »
     * blocs, pour que la flèche n'hésite pas entre deux. null si la liste est vide.
     */
    static Candidate target(List<Candidate> left, int current, double margin) {
        Candidate nearest = null;
        Candidate kept = null;
        for (Candidate c : left) {
            if (nearest == null || c.length() < nearest.length()) {
                nearest = c;
            }
            if (c.item().id() == current) {
                kept = c;
            }
        }
        return kept != null && kept.length() - nearest.length() < margin ? kept : nearest;
    }

    /** Tout près en dessous de « near » ; une fois tout près, il faut dépasser « far » pour que la flèche revienne. */
    static boolean near(boolean wasNear, double distance, double near, double far) {
        return wasNear ? distance <= Math.max(near, far) : distance < near;
    }

    /**
     * Étape à montrer (0 à n-1), ou n quand toutes sont passées. « passed » : étapes déjà passées ; « covered » : le joueur
     * est à l'abri du ciel (dans une zone, une maison, sous terre).
     */
    static int step(Collection.Item item, int passed, double x, double y, double z, boolean covered, double radius) {
        List<Lieu> steps = item.steps();
        int n = steps.size();
        if (n == 0) {
            return 0;
        }
        Lieu entrance = steps.get(n - 1);
        Lieu goal = item.center();
        boolean towards = Math.abs(goal.y() - y) <= LEVEL && goal.distance(x, y, z) < 0.8 * entrance.distance(goal);
        if (covered && towards) {
            return n;
        }
        int i = Math.min(passed, n);
        if (i == n && !covered && !towards && entrance.distance(x, y, z) > OUTSIDE) {
            i--;
        }
        while (i > 0 && i < n && steps.get(i).distance(x, y, z) > steps.get(i - 1).distance(steps.get(i)) + BACK) {
            i--;
        }
        while (i < n) {
            Lieu step = steps.get(i);
            boolean reached = Math.hypot(step.x() - x, step.z() - z) <= radius && Math.abs(step.y() - y) <= STEP_HEIGHT;
            boolean beyond = i + 1 < n && Math.abs(steps.get(i + 1).y() - y) <= BEYOND_HEIGHT
                    && steps.get(i + 1).distance(x, y, z) < BEYOND * step.distance(steps.get(i + 1));
            if (!reached && !beyond) {
                break;
            }
            i++;
        }
        return i;
    }

    /** Longueur du trajet qui reste à partir de l'étape donnée. */
    static double length(Collection.Item item, int step, double x, double y, double z) {
        List<Lieu> steps = item.steps();
        Lieu at = step < steps.size() ? steps.get(step) : item.center();
        double total = at.distance(x, y, z);
        for (int i = step; i < steps.size(); i++) {
            Lieu next = i + 1 < steps.size() ? steps.get(i + 1) : item.center();
            total += steps.get(i).distance(next);
        }
        return total;
    }
}
