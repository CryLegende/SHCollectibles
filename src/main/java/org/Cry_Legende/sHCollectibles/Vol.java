package org.Cry_Legende.sHCollectibles;

/**
 * Vol d'un double saut, tick par tick : course, saut, second saut au sommet avec les vitesses du plugin qui le donne, puis
 * pesanteur et freinage du jeu.
 */
final class Vol {

    /** Saut normal du jeu : vitesse vers le haut, vitesse d'une course, ticks jusqu'au sommet. */
    private static final double JUMP = 0.42;
    private static final double RUN = 0.48;
    private static final int APEX = 6;
    /** Pesanteur, freinages de l'air et poussée d'un joueur qui avance en l'air. */
    private static final double GRAVITY = 0.08;
    private static final double DRAG_UP = 0.98;
    private static final double DRAG_ON = 0.91;
    private static final double PUSH = 0.025;

    private final double[] up = new double[200];
    private final double[] on = new double[200];
    private final int top;

    Vol(double power, double speed) {
        double vy = JUMP;
        double vx = RUN;
        double y = 0;
        double x = 0;
        int best = 0;
        for (int t = 0; t < up.length; t++) {
            if (t == APEX) {
                vy = power;
                vx = speed;
            }
            vx += PUSH;
            x += vx;
            y += vy;
            up[t] = y;
            on[t] = x;
            if (y > up[best]) {
                best = t;
            }
            vy = (vy - GRAVITY) * DRAG_UP;
            vx *= DRAG_ON;
        }
        top = best;
    }

    /** Tick où l'on arrive à cette hauteur en descendant. */
    int landing(double height) {
        int end = top;
        while (end < up.length - 1 && up[end] > height) {
            end++;
        }
        return end;
    }

    double up(int tick) {
        return up[tick];
    }

    /** Part du vol faite à ce tick, pour un vol qui finit à « end ». */
    double part(int tick, int end) {
        return on[tick] / on[end];
    }
}
