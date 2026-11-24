package org.Cry_Legende.sHCollectibles;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/** Place dans un monde, gardée par son nom : le monde peut être chargé plus tard. */
record Lieu(String world, double x, double y, double z) {

    /** « monde x y z ». */
    static Lieu read(String text, String where) {
        String[] p = text == null ? new String[0] : text.trim().split("\\s+");
        if (p.length != 4) {
            throw new IllegalArgumentException(where + " : « monde x y z » attendu");
        }
        return new Lieu(p[0], number(p[1], where), number(p[2], where), number(p[3], where));
    }

    /** « x y z » dans le monde donné. */
    static Lieu read(String text, String world, String where) {
        String[] p = text == null ? new String[0] : text.trim().split("\\s+");
        if (p.length != 3) {
            throw new IllegalArgumentException(where + " : « x y z » attendu");
        }
        return new Lieu(world, number(p[0], where), number(p[1], where), number(p[2], where));
    }

    private static double number(String text, String where) {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(where + " : nombre illisible « " + text + " »");
        }
    }

    World loaded() {
        return Bukkit.getWorld(world);
    }

    /** null si le monde n'est pas chargé. */
    Location location() {
        World w = loaded();
        return w == null ? null : new Location(w, x, y, z);
    }

    Lieu centered() {
        return new Lieu(world, Math.floor(x) + 0.5, Math.floor(y) + 0.5, Math.floor(z) + 0.5);
    }

    double distance(double px, double py, double pz) {
        double dx = x - px;
        double dy = y - py;
        double dz = z - pz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    double distance(Lieu o) {
        return distance(o.x, o.y, o.z);
    }

    String text() {
        return world + " " + x + " " + y + " " + z;
    }
}
