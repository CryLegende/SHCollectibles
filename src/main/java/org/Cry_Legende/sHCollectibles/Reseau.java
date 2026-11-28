package org.Cry_Legende.sHCollectibles;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Toutes les cases où l'on peut arriver à pied depuis les départs, et les déplacements entre elles. Immuable une fois
 * construit : les chemins vers chaque collectible s'y calculent hors du fil principal.
 */
final class Reseau {

    /** Chemins vers un collectible : coût restant et prochain déplacement de chaque case. */
    static final class Champ {
        private static final char FAR = 0xFFFF;
        private static final int ARRIVED = 0xFF;
        private static final double UNIT = 0.25;

        private final char[] cost;
        private final byte[] next;

        private Champ(int n) {
            cost = new char[n];
            next = new byte[n];
            Arrays.fill(cost, FAR);
        }

        boolean reaches(int node) {
            return node >= 0 && cost[node] != FAR;
        }

        double cost(int node) {
            return cost[node] * UNIT;
        }
    }

    final Carte map;
    private final int[] cells;
    private final int[] keys;
    private final int[] values;
    private final int[] start;
    private final int[] to;
    private final float[] costs;
    private final byte[] kinds;
    private final int[] backStart;
    private final int[] backFrom;
    private final int[] backEdge;

    private Reseau(Carte map, int[] cells, int[] keys, int[] values, int[] start, int[] to, float[] costs, byte[] kinds) {
        this.map = map;
        this.cells = cells;
        this.keys = keys;
        this.values = values;
        this.start = start;
        this.to = to;
        this.costs = costs;
        this.kinds = kinds;
        int n = cells.length;
        backStart = new int[n + 1];
        for (int t : to) {
            backStart[t + 1]++;
        }
        for (int i = 0; i < n; i++) {
            backStart[i + 1] += backStart[i];
        }
        int[] at = Arrays.copyOf(backStart, n);
        backFrom = new int[to.length];
        backEdge = new int[to.length];
        for (int from = 0; from < n; from++) {
            for (int e = start[from]; e < start[from + 1]; e++) {
                int slot = at[to[e]]++;
                backFrom[slot] = from;
                backEdge[slot] = e;
            }
        }
    }

    int size() {
        return cells.length;
    }

    int edges() {
        return to.length;
    }

    /** Explore la carte depuis les départs (cases x, y, z). */
    static Reseau build(Carte map, List<int[]> departures, List<Guidage.Link> links, String world) {
        Builder b = new Builder(map, links, world);
        for (int[] d : departures) {
            int cell = nearest(map, d[0], d[1], d[2], 12, 3);
            if (cell >= 0) {
                b.node(cell);
            }
        }
        for (int i = 0; i < b.count; i++) {
            b.expand(i);
        }
        return new Reseau(map, Arrays.copyOf(b.cells, b.count), b.keys, b.values, Arrays.copyOf(b.start, b.count + 1),
                Arrays.copyOf(b.to, b.edges), Arrays.copyOf(b.costs, b.edges), Arrays.copyOf(b.kinds, b.edges));
    }

    private static final class Builder implements Carte.Moves {
        private static final int MAX_MOVES = 250;

        private final Carte map;
        private final List<Guidage.Link> links;
        private final String world;
        private int[] cells = new int[1 << 14];
        private int[] keys = new int[1 << 16];
        private int[] values = new int[1 << 16];
        private int[] start = new int[(1 << 14) + 1];
        private int[] to = new int[1 << 17];
        private float[] costs = new float[1 << 17];
        private byte[] kinds = new byte[1 << 17];
        private int count;
        private int edges;
        private int first;

        private Builder(Carte map, List<Guidage.Link> links, String world) {
            this.map = map;
            this.links = links;
            this.world = world;
        }

        private int node(int cell) {
            int slot = slot(keys, cell);
            if (keys[slot] == cell + 1) {
                return values[slot];
            }
            if (count * 2 > keys.length) {
                int[] oldKeys = keys;
                int[] oldValues = values;
                keys = new int[oldKeys.length * 2];
                values = new int[oldKeys.length * 2];
                for (int i = 0; i < oldKeys.length; i++) {
                    if (oldKeys[i] != 0) {
                        int s = slot(keys, oldKeys[i] - 1);
                        keys[s] = oldKeys[i];
                        values[s] = oldValues[i];
                    }
                }
                slot = slot(keys, cell);
            }
            if (count == cells.length) {
                cells = Arrays.copyOf(cells, count * 2);
                start = Arrays.copyOf(start, count * 2 + 1);
            }
            keys[slot] = cell + 1;
            values[slot] = count;
            cells[count] = cell;
            return count++;
        }

        private void expand(int i) {
            first = edges;
            start[i] = edges;
            int x = map.cellX(cells[i]);
            int y = map.cellY(cells[i]);
            int z = map.cellZ(cells[i]);
            map.moves(x, y, z, this);
            for (Guidage.Link l : links) {
                if (l.from().world().equals(world) && l.to().world().equals(world) && l.from().distance(x + 0.5, y, z + 0.5) <= l.radius()) {
                    int cell = nearest(map, (int) Math.floor(l.to().x()), (int) Math.floor(l.to().y()), (int) Math.floor(l.to().z()), 4, 2);
                    if (cell >= 0) {
                        add(cell, l.cost(), Carte.LINK);
                    }
                }
            }
            start[i + 1] = edges;
        }

        @Override
        public void add(int x, int y, int z, double cost, byte kind) {
            add(map.cell(x, y, z), cost + map.ground(x, y, z), kind);
        }

        private void add(int cell, double cost, byte kind) {
            if (edges - first >= MAX_MOVES) {
                return;
            }
            int target = node(cell);
            if (edges == to.length) {
                to = Arrays.copyOf(to, edges * 2);
                costs = Arrays.copyOf(costs, edges * 2);
                kinds = Arrays.copyOf(kinds, edges * 2);
            }
            kinds[edges] = kind;
            to[edges] = target;
            costs[edges++] = (float) cost;
        }
    }

    private static int slot(int[] keys, int cell) {
        int mask = keys.length - 1;
        int slot = (cell * 0x9E3779B9) >>> 7 & mask;
        while (keys[slot] != 0 && keys[slot] != cell + 1) {
            slot = slot + 1 & mask;
        }
        return slot;
    }

    private int index(int cell) {
        int slot = slot(keys, cell);
        return keys[slot] == 0 ? -1 : values[slot];
    }

    /** Case où l'on tient debout la plus proche : sous les pieds d'abord, puis autour ; -1 si rien. */
    private static int nearest(Carte map, int x, int y, int z, int down, int around) {
        for (int r = 0; r <= around; r++) {
            for (int dy = 0; dy <= down; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) == r && map.stand(x + dx, y - dy, z + dz)) {
                            return map.cell(x + dx, y - dy, z + dz);
                        }
                    }
                }
            }
        }
        return -1;
    }

    /** Case du réseau où se trouve un joueur : sous lui, puis autour ; -1 s'il est hors de la carte. */
    int locate(double px, double py, double pz, int down, int around) {
        int x = (int) Math.floor(px);
        int y = (int) Math.floor(py + 0.05);
        int z = (int) Math.floor(pz);
        for (int r = 0; r <= around; r++) {
            int best = -1;
            double bestDistance = Double.MAX_VALUE;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    for (int dy = r == 0 ? 0 : -1; dy <= (r == 0 ? down : 2); dy++) {
                        if (!map.inside(x + dx, y - dy, z + dz)) {
                            continue;
                        }
                        int node = index(map.cell(x + dx, y - dy, z + dz));
                        if (node >= 0) {
                            double d = Math.hypot(x + dx + 0.5 - px, z + dz + 0.5 - pz) + Math.abs(dy);
                            if (d < bestDistance) {
                                bestDistance = d;
                                best = node;
                            }
                        }
                    }
                }
            }
            if (best >= 0) {
                return best;
            }
        }
        return -1;
    }

    int x(int node) {
        return map.cellX(cells[node]);
    }

    int y(int node) {
        return map.cellY(cells[node]);
    }

    int z(int node) {
        return map.cellZ(cells[node]);
    }

    /** Case suivante vers le collectible ; -1 quand on y est. */
    int next(Champ c, int node) {
        int e = c.next[node] & 0xFF;
        return e == Champ.ARRIVED ? -1 : to[start[node] + e];
    }

    /** Sorte du déplacement vers la case suivante (Carte.WALK…). */
    byte kind(Champ c, int node) {
        int e = c.next[node] & 0xFF;
        return e == Champ.ARRIVED ? Carte.WALK : kinds[start[node] + e];
    }

    /** Case du réseau la plus proche d'un point, à « radius » blocs au plus ; -1 si aucune. */
    int near(double px, double py, double pz, int radius) {
        int x = (int) Math.floor(px);
        int y = (int) Math.floor(py);
        int z = (int) Math.floor(pz);
        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    double d = dx * dx + dz * dz + 2.25 * dy * dy;
                    if (d >= bestDistance || !map.inside(x + dx, y + dy, z + dz)) {
                        continue;
                    }
                    int node = index(map.cell(x + dx, y + dy, z + dz));
                    if (node >= 0) {
                        bestDistance = d;
                        best = node;
                    }
                }
            }
        }
        return best;
    }

    /** Cases d'où l'on peut cliquer une tête : à portée, et vue des yeux. */
    List<Integer> arrivals(Lieu head, double reach, double eyes) {
        int hx = (int) Math.floor(head.x());
        int hy = (int) Math.floor(head.y());
        int hz = (int) Math.floor(head.z());
        int r = (int) Math.ceil(reach);
        List<Integer> out = new ArrayList<>();
        for (int x = hx - r; x <= hx + r; x++) {
            for (int z = hz - r; z <= hz + r; z++) {
                for (int y = hy - r - 2; y <= hy + r; y++) {
                    int node = map.inside(x, y, z) ? index(map.cell(x, y, z)) : -1;
                    if (node < 0) {
                        continue;
                    }
                    double dx = x - hx;
                    double dy = y + eyes - hy - 0.4;
                    double dz = z - hz;
                    if (dx * dx + dy * dy + dz * dz <= (reach + 0.5) * (reach + 0.5)
                            && map.sight(x + 0.5, y + eyes, z + 0.5, hx + 0.5, hy + 0.3, hz + 0.5)) {
                        out.add(node);
                    }
                }
            }
        }
        return out;
    }

    /** Cases à « least » à « spread » blocs à plat d'une tête, et à « height » blocs de haut au plus. */
    List<Integer> around(Lieu head, double least, double spread, int height) {
        int hx = (int) Math.floor(head.x());
        int hy = (int) Math.floor(head.y());
        int hz = (int) Math.floor(head.z());
        int r = (int) Math.ceil(spread);
        List<Integer> out = new ArrayList<>();
        for (int x = hx - r; x <= hx + r; x++) {
            for (int z = hz - r; z <= hz + r; z++) {
                double d = Math.hypot(x - hx, z - hz);
                if (d < least || d > spread) {
                    continue;
                }
                for (int y = hy - height - 1; y <= hy + height; y++) {
                    int node = map.inside(x, y, z) ? index(map.cell(x, y, z)) : -1;
                    if (node >= 0) {
                        out.add(node);
                    }
                }
            }
        }
        return out;
    }

    /** Case du réseau la plus proche d'une tête qu'aucun chemin n'atteint ; -1 si le réseau est vide. */
    int closest(Lieu head) {
        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < cells.length; i++) {
            double d = head.distance(x(i) + 0.5, y(i), z(i) + 0.5);
            if (d < bestDistance) {
                bestDistance = d;
                best = i;
            }
        }
        return best;
    }

    /** Chemin le plus court de toutes les cases vers les cases d'arrivée. */
    Champ field(List<Integer> arrivals) {
        int n = cells.length;
        Champ c = new Champ(n);
        float[] dist = new float[n];
        Arrays.fill(dist, Float.MAX_VALUE);
        Heap heap = new Heap(n);
        for (int a : arrivals) {
            dist[a] = 0;
            c.next[a] = (byte) Champ.ARRIVED;
            heap.push(a, 0);
        }
        while (heap.size > 0) {
            float d = heap.topCost();
            int node = heap.pop();
            if (d > dist[node]) {
                continue;
            }
            for (int s = backStart[node]; s < backStart[node + 1]; s++) {
                int from = backFrom[s];
                int e = backEdge[s];
                float nd = d + costs[e];
                if (nd < dist[from]) {
                    dist[from] = nd;
                    c.next[from] = (byte) (e - start[from]);
                    heap.push(from, nd);
                }
            }
        }
        for (int i = 0; i < n; i++) {
            if (dist[i] != Float.MAX_VALUE) {
                c.cost[i] = (char) Math.min(Champ.FAR - 1, Math.round(dist[i] / Champ.UNIT));
            }
        }
        return c;
    }

    /** Tas binaire (case, coût) ; une case peut y être plusieurs fois, la moins chère sort d'abord. */
    private static final class Heap {
        private int[] nodes;
        private float[] costs;
        private int size;

        private Heap(int capacity) {
            nodes = new int[Math.max(16, capacity)];
            costs = new float[nodes.length];
        }

        private float topCost() {
            return costs[0];
        }

        private void push(int node, float cost) {
            if (size == nodes.length) {
                nodes = Arrays.copyOf(nodes, size * 2);
                costs = Arrays.copyOf(costs, size * 2);
            }
            int i = size++;
            while (i > 0) {
                int parent = i - 1 >> 1;
                if (costs[parent] <= cost) {
                    break;
                }
                nodes[i] = nodes[parent];
                costs[i] = costs[parent];
                i = parent;
            }
            nodes[i] = node;
            costs[i] = cost;
        }

        private int pop() {
            int out = nodes[0];
            int node = nodes[--size];
            float cost = costs[size];
            int i = 0;
            while (true) {
                int child = 2 * i + 1;
                if (child >= size) {
                    break;
                }
                if (child + 1 < size && costs[child + 1] < costs[child]) {
                    child++;
                }
                if (costs[child] >= cost) {
                    break;
                }
                nodes[i] = nodes[child];
                costs[i] = costs[child];
                i = child;
            }
            nodes[i] = node;
            costs[i] = cost;
            return out;
        }
    }
}
