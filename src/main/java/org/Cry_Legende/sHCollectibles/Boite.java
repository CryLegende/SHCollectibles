package org.Cry_Legende.sHCollectibles;

/** Boîte de blocs d'un monde, bords compris. */
record Boite(String world, int x1, int y1, int z1, int x2, int y2, int z2) {

    /** « monde x1 y1 z1 x2 y2 z2 », coins dans n'importe quel ordre. */
    static Boite read(String text, String where) {
        String[] p = text == null ? new String[0] : text.trim().split("\\s+");
        if (p.length != 7) {
            throw new IllegalArgumentException(where + " : « monde x1 y1 z1 x2 y2 z2 » attendu");
        }
        int[] v = new int[6];
        for (int i = 0; i < 6; i++) {
            try {
                v[i] = (int) Math.floor(Double.parseDouble(p[i + 1]));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(where + " : nombre illisible « " + p[i + 1] + " »");
            }
        }
        return new Boite(p[0], Math.min(v[0], v[3]), Math.min(v[1], v[4]), Math.min(v[2], v[5]),
                Math.max(v[0], v[3]), Math.max(v[1], v[4]), Math.max(v[2], v[5]));
    }

    boolean contains(String inWorld, double x, double y, double z) {
        return world.equals(inWorld) && contains((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    boolean contains(int x, int y, int z) {
        return x >= x1 && x <= x2 && y >= y1 && y <= y2 && z >= z1 && z <= z2;
    }
}
