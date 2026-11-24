package org.Cry_Legende.sHCollectibles;

import java.util.List;

/**
 * Monde vu par quelqu'un qui marche : une catégorie de bloc par case, et ce qui en découle (tenir debout, déplacements
 * possibles et leur coût). Immuable : lue depuis n'importe quel fil.
 */
final class Carte {

    /** Sortes de déplacement. */
    static final byte WALK = 0;
    static final byte GAP = 1;
    static final byte FALL = 2;
    static final byte LEAP = 3;
    static final byte LINK = 4;

    /** Reçoit les déplacements possibles depuis une case. */
    interface Moves {
        void add(int x, int y, int z, double cost, byte kind);
    }

    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] CORNERS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    /** Directions essayées pour un double saut. */
    private static final double[][] FLIGHTS = new double[16][2];
    /** Blocs gardés devant soi en se posant, longueur d'une arrivée sûre, et arrivées plus basses par direction. */
    private static final double SAFE = 1.5;
    private static final double WIDE = 2.5;
    private static final int MAX_LOWER = 3;

    /** Vol d'un double saut tick par tick : hauteur et avancée (course, saut, second saut au sommet). */
    private static final double[] FLIGHT_UP = new double[200];
    private static final double[] FLIGHT_ON = new double[200];
    private static final int FLIGHT_TOP;
    /** À partir de cette chute, le vol est vérifié case par case. */
    private static final int CHECKED_DROP = 4;

    static {
        for (int i = 0; i < FLIGHTS.length; i++) {
            FLIGHTS[i][0] = Math.cos(i * Math.PI * 2 / FLIGHTS.length);
            FLIGHTS[i][1] = Math.sin(i * Math.PI * 2 / FLIGHTS.length);
        }
        double speed = 0.48;
        double up = 0.42;
        double y = 0;
        double on = 0;
        int top = 0;
        for (int t = 0; t < FLIGHT_UP.length; t++) {
            if (t == 6) {
                speed = 1;
                up = 0.8;
            }
            speed += 0.025;
            on += speed;
            y += up;
            FLIGHT_UP[t] = y;
            FLIGHT_ON[t] = on;
            if (y > FLIGHT_UP[top]) {
                top = t;
            }
            up = (up - 0.08) * 0.98;
            speed *= 0.91;
        }
        FLIGHT_TOP = top;
    }

    final int x0;
    final int y0;
    final int z0;
    final int nx;
    final int ny;
    final int nz;
    private final byte[] cells;
    private final Guidage.Costs costs;
    private final List<Boite> forbidden;

    Carte(int x0, int y0, int z0, int nx, int ny, int nz, byte[] cells, Guidage.Costs costs, List<Boite> forbidden) {
        this.x0 = x0;
        this.y0 = y0;
        this.z0 = z0;
        this.nx = nx;
        this.ny = ny;
        this.nz = nz;
        this.cells = cells;
        this.costs = costs;
        this.forbidden = forbidden;
    }

    boolean inside(int x, int y, int z) {
        return x >= x0 && x < x0 + nx && y >= y0 && y < y0 + ny && z >= z0 && z < z0 + nz;
    }

    int cell(int x, int y, int z) {
        return ((y - y0) * nz + (z - z0)) * nx + (x - x0);
    }

    int cellX(int cell) {
        return cell % nx + x0;
    }

    int cellY(int cell) {
        return cell / nx / nz + y0;
    }

    int cellZ(int cell) {
        return cell / nx % nz + z0;
    }

    byte at(int x, int y, int z) {
        if (x < x0 || x >= x0 + nx || z < z0 || z >= z0 + nz) {
            return Blocs.PLEIN;
        }
        return y < y0 || y >= y0 + ny ? Blocs.VIDE : cells[((y - y0) * nz + (z - z0)) * nx + (x - x0)];
    }

    /** Une partie du corps passe dans cette case. */
    boolean free(int x, int y, int z) {
        byte b = at(x, y, z);
        return b == Blocs.VIDE || b == Blocs.GRIMPE || b == Blocs.EAU
                || (b == Blocs.PORTE || b == Blocs.TRAPPE) && costs.door() >= 0;
    }

    /** Le corps tient avec les pieds dans cette case. */
    boolean body(int x, int y, int z) {
        byte feet = at(x, y, z);
        if (feet == Blocs.BAS) {
            return free(x, y + 1, z) && (free(x, y + 2, z) || at(x, y + 2, z) == Blocs.TOIT);
        }
        if (feet == Blocs.ESC) {
            return free(x, y + 1, z) && free(x, y + 2, z);
        }
        if (!free(x, y, z)) {
            return false;
        }
        // Dans l'eau, on nage à plat dans un passage d'un bloc
        return feet == Blocs.EAU || free(x, y + 1, z);
    }

    boolean stand(int x, int y, int z) {
        if (!inside(x, y, z) || !body(x, y, z)) {
            return false;
        }
        byte feet = at(x, y, z);
        if (feet != Blocs.GRIMPE && feet != Blocs.EAU && feet != Blocs.BAS && feet != Blocs.ESC) {
            byte under = at(x, y - 1, z);
            if (y <= y0 || under == Blocs.VIDE || under == Blocs.EAU || under == Blocs.PORTE) {
                return false;
            }
        }
        for (Boite b : forbidden) {
            if (b.contains(x, y, z)) {
                return false;
            }
        }
        return true;
    }

    /** Coût en plus de l'endroit où l'on marche. */
    double ground(int x, int y, int z) {
        return at(x, y - 1, z) == Blocs.FEUILLE ? costs.leaves() : 0;
    }

    private double doors(int x, int y, int z) {
        byte a = at(x, y, z);
        byte b = at(x, y + 1, z);
        return a == Blocs.PORTE || b == Blocs.PORTE || a == Blocs.TRAPPE || b == Blocs.TRAPPE ? costs.door() : 0;
    }

    /** Déplacements possibles depuis une case où l'on tient debout. */
    void moves(int x, int y, int z, Moves out) {
        byte feet = at(x, y, z);
        if (feet == Blocs.GRIMPE || feet == Blocs.EAU) {
            double cost = feet == Blocs.GRIMPE ? costs.climb() : costs.swim();
            if (stand(x, y + 1, z)) {
                out.add(x, y + 1, z, cost, WALK);
            }
            if (stand(x, y - 1, z)) {
                out.add(x, y - 1, z, cost, WALK);
            }
        } else {
            byte under = at(x, y - 1, z);
            if ((under == Blocs.GRIMPE || under == Blocs.EAU) && stand(x, y - 1, z)) {
                out.add(x, y - 1, z, under == Blocs.GRIMPE ? costs.climb() : costs.swim(), WALK);
            } else if (under == Blocs.TRAPPE && costs.door() >= 0) {
                int landing = fall(x, y - 1, z);
                if (landing != Integer.MIN_VALUE) {
                    out.add(x, landing, z, 1 + costs.door() + fallCost(y - landing), fallKind(y - landing));
                }
            }
        }
        for (int[] d : SIDES) {
            side(x, y, z, d[0], d[1], out);
        }
        if (costs.leap().possible() && costs.leap().allowed(x, y, z)) {
            for (double[] d : FLIGHTS) {
                doubleJump(x, y, z, d[0], d[1], out);
            }
        }
        for (int[] d : CORNERS) {
            int tx = x + d[0];
            int tz = z + d[1];
            if (body(tx, y, z) && body(x, y, tz) && stand(tx, y, tz)) {
                out.add(tx, y, tz, Math.sqrt(2) + doors(tx, y, tz), WALK);
            }
        }
    }

    private void side(int x, int y, int z, int dx, int dz, Moves out) {
        int tx = x + dx;
        int tz = z + dz;
        boolean open = body(tx, y, tz);
        if (open) {
            if (stand(tx, y, tz)) {
                out.add(tx, y, tz, 1 + doors(tx, y, tz), WALK);
            } else {
                int landing = fall(tx, y, tz);
                if (landing != Integer.MIN_VALUE && (y - landing <= costs.fallFree() || costs.fall() >= 0)) {
                    out.add(tx, landing, tz, 1 + fallCost(y - landing) + doors(tx, landing, tz), fallKind(y - landing));
                }
                gap(x, y, z, dx, dz, out);
            }
        } else if (free(x, y + 2, z) && at(tx, y, tz) != Blocs.HAUT && stand(tx, y + 1, tz)) {
            out.add(tx, y + 1, tz, 1 + costs.jump() + doors(tx, y + 1, tz), WALK);
        }
    }

    /** Hauteur où l'on arrive en tombant dans cette colonne ; MIN_VALUE si rien n'arrête la chute. */
    private int fall(int x, int y, int z) {
        for (int at = y; at >= y0 && y - at <= costs.fallMax(); at--) {
            if (!body(x, at, z)) {
                return Integer.MIN_VALUE;
            }
            if (stand(x, at, z)) {
                return at;
            }
        }
        return Integer.MIN_VALUE;
    }

    private double fallCost(int height) {
        return height <= costs.fallFree() ? 0 : costs.fall() + drop(height);
    }

    /** Hauteur perdue : une grande chute se voit mal d'en haut, elle coûte de plus en plus. */
    private double drop(int height) {
        return height <= costs.fallFree() ? 0 : height * costs.fallPerBlock() + Math.max(0, height - costs.highFrom()) * costs.high();
    }

    private byte fallKind(int height) {
        return height <= costs.fallFree() ? WALK : FALL;
    }

    /** Saut par-dessus un trou, au même niveau, un bloc plus bas ou plus haut. */
    private void gap(int x, int y, int z, int dx, int dz, Moves out) {
        if (!free(x, y + 2, z)) {
            return;
        }
        boolean deep = false;
        for (int k = 2; k <= costs.gapMax(); k++) {
            int bx = x + dx * (k - 1);
            int bz = z + dz * (k - 1);
            if (!free(bx, y, bz) || !free(bx, y + 1, bz) || !free(bx, y + 2, bz)) {
                return;
            }
            int bottom = fall(bx, y, bz);
            deep |= bottom == Integer.MIN_VALUE || y - bottom > costs.fallFree();
            int tx = x + dx * k;
            int tz = z + dz * k;
            for (int dy : new int[]{0, -1, 1}) {
                if ((dy < 1 || k < costs.gapMax()) && stand(tx, y + dy, tz) && free(tx, y + 2, tz)) {
                    out.add(tx, y + dy, tz, k + costs.gap() + (deep ? costs.gapDeep() : 0) + doors(tx, y + dy, tz), GAP);
                    return;
                }
            }
        }
    }

    /**
     * Double saut dans une direction : montée droite, vol à plat au sommet, descente. Seulement là où un saut simple ne
     * suffit pas. La portée dépend de la hauteur de l'arrivée (Guidage.Leap).
     */
    private void doubleJump(int x, int y, int z, double ux, double uz, Moves out) {
        int top = 0;
        while (top < costs.leap().height() && free(x, y + top + 2, z)) {
            top++;
        }
        for (int apex = top; apex >= 2; apex--) {
            if (fly(x, y, z, ux, uz, apex, apex == top, out)) {
                return;
            }
        }
    }

    /**
     * Vrai si un vol à cette hauteur se pose aussi haut, ou plus haut, que son départ. Une arrivée est une suite de cases de
     * même hauteur : on s'y pose un peu après son bord, et une arrivée étroite coûte plus.
     */
    private boolean fly(int x, int y, int z, double ux, double uz, int apex, boolean down, Moves out) {
        Guidage.Leap leap = costs.leap();
        double max = leap.length() + leap.dropGainMax();
        int cx = x;
        int cz = z;
        boolean hole = false;
        int lower = down ? 0 : MAX_LOWER;
        // Arrivée en cours : 0 aucune, 1 à hauteur ou plus haut, 2 plus bas ; « done » : déjà retenue
        int run = 0;
        boolean done = false;
        double from = 0;
        double to = 0;
        int level = 0;
        int px = 0;
        int py = 0;
        int pz = 0;
        double pk = 0;
        for (double k = 0.5; k <= max; k += 0.5) {
            int tx = (int) Math.floor(x + 0.5 + ux * k);
            int tz = (int) Math.floor(z + 0.5 + uz * k);
            if (tx == cx && tz == cz) {
                to = run != 0 ? k : to;
                continue;
            }
            if (!passes(tx, y + apex, tz) || tx != cx && tz != cz && !(passes(tx, y + apex, cz) && passes(cx, y + apex, tz))) {
                break;
            }
            cx = tx;
            cz = tz;
            int landing = land(tx, y + apex, tz);
            if (landing == Integer.MIN_VALUE || landing < y - 1) {
                hole = true;
            }
            int kind = 0;
            if (landing != Integer.MIN_VALUE && k <= leap.reach(landing - y)) {
                if (landing - y >= 2 || landing >= y - 1 && hole && k > costs.gapMax()) {
                    kind = 1;
                } else if (landing >= y - 1 && hole) {
                    // Un saut simple suffit
                    break;
                } else if (landing < y - 1 && lower < MAX_LOWER && k > costs.gapMax() && (y - landing <= costs.fallFree() || costs.fall() >= 0)
                        && clear(x, y, z, ux, uz, k, landing)) {
                    kind = 2;
                }
            }
            if (kind != 0 && kind == run && Math.abs(landing - level) <= 1) {
                to = k;
                level = landing;
                if (!done && k <= from + SAFE) {
                    px = tx;
                    py = landing;
                    pz = tz;
                    pk = k;
                }
            } else {
                if (run != 0 && !done) {
                    leap(px, py, pz, pk, y, to - from < 1, out);
                    if (run == 1) {
                        return true;
                    }
                    lower++;
                }
                run = kind;
                done = false;
                from = to = k;
                level = landing;
                px = tx;
                py = landing;
                pz = tz;
                pk = k;
            }
            if (run != 0 && !done && to - from >= WIDE) {
                leap(px, py, pz, pk, y, false, out);
                if (run == 1) {
                    return true;
                }
                done = true;
                lower++;
            }
        }
        if (run != 0 && !done) {
            leap(px, py, pz, pk, y, to - from < 1, out);
            return run == 1;
        }
        return false;
    }

    private void leap(int tx, int ty, int tz, double k, int y, boolean narrow, Moves out) {
        Guidage.Leap leap = costs.leap();
        out.add(tx, ty, tz, leap.cost() + k * leap.perBlock() + (narrow ? leap.narrow() : 0) + doors(tx, ty, tz)
                + drop(y - ty), LEAP);
    }

    /** Vrai si rien n'arrête un vol qui se pose nettement plus bas, à « k » blocs. */
    private boolean clear(int x, int y, int z, double ux, double uz, double k, int landing) {
        if (y - landing < CHECKED_DROP) {
            return true;
        }
        int end = FLIGHT_TOP;
        while (end < FLIGHT_UP.length - 1 && FLIGHT_UP[end] > landing - y) {
            end++;
        }
        for (int t = 0; t < end; t++) {
            double on = k * FLIGHT_ON[t] / FLIGHT_ON[end];
            double up = y + FLIGHT_UP[t];
            int fx = (int) Math.floor(x + 0.5 + ux * on);
            int fz = (int) Math.floor(z + 0.5 + uz * on);
            int fy = (int) Math.floor(up);
            if (fx == x && fz == z && fy <= y + 1) {
                continue;
            }
            if (up - landing > 1 && !(free(fx, fy, fz) && free(fx, fy + 1, fz))) {
                return false;
            }
        }
        return true;
    }

    private boolean passes(int x, int y, int z) {
        return free(x, y, z) && free(x, y + 1, z);
    }

    /** Hauteur où l'on se pose en descendant une colonne ; MIN_VALUE si rien n'arrête la chute. */
    private int land(int x, int from, int z) {
        for (int at = from; at >= y0 && from - at <= costs.fallMax() + costs.leap().height(); at--) {
            if (!body(x, at, z)) {
                return Integer.MIN_VALUE;
            }
            if (stand(x, at, z)) {
                return at;
            }
        }
        return Integer.MIN_VALUE;
    }

    /** Demi-largeur d'un joueur. */
    private static final double HALF = 0.3;

    /** Vrai si l'on va tout droit d'un point à une case en marchant (marches et petites descentes comprises). */
    boolean straight(double fromX, double fromY, double fromZ, int toX, int toY, int toZ) {
        double tx = toX + 0.5;
        double tz = toZ + 0.5;
        double dx = tx - fromX;
        double dz = tz - fromZ;
        double length = Math.hypot(dx, dz);
        int steps = Math.max(1, (int) Math.ceil(length / 0.4));
        int cx = (int) Math.floor(fromX);
        int cz = (int) Math.floor(fromZ);
        int y = (int) Math.floor(fromY);
        // Épaules : un mur longé ne gêne pas, un coin qui dépasse arrête
        double sideX = length < 1e-6 ? 0 : -dz / length * HALF;
        double sideZ = length < 1e-6 ? 0 : dx / length * HALF;
        boolean leftFree = shoulder(fromX + sideX, y, fromZ + sideZ);
        boolean rightFree = shoulder(fromX - sideX, y, fromZ - sideZ);
        for (int i = 1; i <= steps; i++) {
            double atX = fromX + dx * i / steps;
            double atZ = fromZ + dz * i / steps;
            int nextX = (int) Math.floor(atX);
            int nextZ = (int) Math.floor(atZ);
            boolean left = shoulder(atX + sideX, y, atZ + sideZ);
            boolean right = shoulder(atX - sideX, y, atZ - sideZ);
            if (i < steps && (leftFree && !left || rightFree && !right)) {
                return false;
            }
            leftFree = left;
            rightFree = right;
            if (nextX == cx && nextZ == cz) {
                continue;
            }
            if (nextX != cx && nextZ != cz && !(body(nextX, y, cz) && body(cx, y, nextZ))) {
                return false;
            }
            if (stand(nextX, y, nextZ)) {
                // même niveau
            } else if (stand(nextX, y + 1, nextZ) && free(cx, y + 2, cz)) {
                y++;
            } else {
                int down = 1;
                while (down <= costs.fallFree() && body(nextX, y - down + 1, nextZ) && !stand(nextX, y - down, nextZ)) {
                    down++;
                }
                if (down > costs.fallFree() || !stand(nextX, y - down, nextZ) || !body(nextX, y, nextZ)) {
                    return false;
                }
                y -= down;
            }
            cx = nextX;
            cz = nextZ;
        }
        return Math.abs(y - toY) <= 1;
    }

    private boolean shoulder(double x, int y, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        return body(bx, y, bz) || stand(bx, y + 1, bz);
    }

    /** Rien d'opaque entre deux points (le dernier bloc, la tête, ne compte pas). */
    boolean sight(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = bx - ax;
        double dy = by - ay;
        double dz = bz - az;
        int steps = Math.max(2, (int) Math.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz) / 0.2));
        int hx = (int) Math.floor(bx);
        int hy = (int) Math.floor(by);
        int hz = (int) Math.floor(bz);
        for (int i = 1; i < steps; i++) {
            int x = (int) Math.floor(ax + dx * i / steps);
            int y = (int) Math.floor(ay + dy * i / steps);
            int z = (int) Math.floor(az + dz * i / steps);
            if (x == hx && y == hy && z == hz) {
                return true;
            }
            byte b = at(x, y, z);
            if (b == Blocs.PLEIN || b == Blocs.FEUILLE || b == Blocs.TOIT || b == Blocs.ESC || b == Blocs.HAUT) {
                return false;
            }
        }
        return true;
    }
}
