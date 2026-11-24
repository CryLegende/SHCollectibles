package org.Cry_Legende.sHCollectibles;

import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * Cartes des mondes qui ont des détecteurs. La lecture des tronçons est étalée sur plusieurs ticks, tout le calcul se fait
 * sur un fil à part, et les chemins vers chaque collectible sont gardés.
 */
final class Cartes {

    /** Hauteur des yeux d'un joueur debout. */
    private static final double EYES = 1.62;

    /** Chemins vers un collectible ; « reached » faux : aucune case n'y mène, ils vont à la plus proche, « end ». */
    record Route(Reseau.Champ field, boolean reached, String end) {
    }

    /** Carte d'un monde. */
    static final class Monde {
        final String name;
        volatile Reseau network;
        volatile long seconds;
        volatile boolean building;
        volatile boolean again;
        private final Map<String, Route> routes = new ConcurrentHashMap<>();
        private final Set<String> pending = ConcurrentHashMap.newKeySet();
        private int[] bounds = new int[0];

        private Monde(String name) {
            this.name = name;
        }

        int routes() {
            return routes.size();
        }
    }

    private final SHCollectibles plugin;
    private final Map<String, Monde> worlds = new ConcurrentHashMap<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SHCollectibles-Guidage");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    /** Chemins des joueurs, à part : un calcul long de carte ne les retarde pas. */
    private final ExecutorService live = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SHCollectibles-Chemins");
        t.setDaemon(true);
        return t;
    });
    private final List<BukkitTask> tasks = new ArrayList<>();
    private Guidage built;

    Cartes(SHCollectibles plugin) {
        this.plugin = plugin;
    }

    Monde get(String world) {
        return worlds.get(world);
    }

    Map<String, Monde> all() {
        return worlds;
    }

    /** Calcul long (carte, chemins vers un collectible). */
    private void run(Runnable r) {
        run(worker, r);
    }

    /** Chemin d'un joueur. */
    void runLive(Runnable r) {
        run(live, r);
    }

    private void run(ExecutorService on, Runnable r) {
        if (!on.isShutdown()) {
            on.execute(() -> {
                try {
                    r.run();
                } catch (RuntimeException e) {
                    plugin.getLogger().log(Level.WARNING, "Calcul du guidage impossible", e);
                }
            });
        }
    }

    void close() {
        tasks.forEach(BukkitTask::cancel);
        worker.shutdownNow();
        live.shutdownNow();
    }

    /** Refait les cartes dont quelque chose a changé ; « force » : toutes. */
    void refresh(boolean force) {
        Reglages s = plugin.settings();
        Guidage g = s.guidage();
        Map<String, int[]> wanted = g.enabled() ? bounds(s) : Map.of();
        worlds.keySet().retainAll(wanted.keySet());
        boolean changed = force || !g.equals(built);
        built = g;
        for (Map.Entry<String, int[]> en : wanted.entrySet()) {
            Monde m = worlds.computeIfAbsent(en.getKey(), Monde::new);
            m.routes.clear();
            m.pending.clear();
            if (changed || m.network == null || !java.util.Arrays.equals(m.bounds, en.getValue())) {
                m.bounds = en.getValue();
                read(m, g);
            } else {
                prepareRoutes(m);
            }
        }
    }

    /** Boîte de chaque monde : ses collectibles à détecteur, ses départs, ses raccourcis et ses liens, avec une marge. */
    private static Map<String, int[]> bounds(Reglages s) {
        Guidage g = s.guidage();
        Map<String, int[]> out = new HashMap<>();
        for (Collection c : s.collections().values()) {
            if (c.enabled() && c.detector()) {
                c.items().values().forEach(i -> grow(out, i.place(), true));
            }
        }
        g.departures().forEach(l -> grow(out, l, false));
        g.shortcuts().forEach(r -> grow(out, r.place(), false));
        for (Guidage.Link l : g.links()) {
            grow(out, l.from(), false);
            grow(out, l.to(), false);
        }
        out.keySet().removeIf(w -> Bukkit.getWorld(w) == null);
        for (Map.Entry<String, int[]> en : out.entrySet()) {
            World w = Bukkit.getWorld(en.getKey());
            Location spawn = w.getSpawnLocation();
            grow(out, new Lieu(en.getKey(), spawn.getX(), spawn.getY(), spawn.getZ()), false);
            int[] b = en.getValue();
            b[0] -= g.margin();
            b[2] -= g.margin();
            b[3] += g.margin();
            b[5] += g.margin();
            b[1] = Math.max(w.getMinHeight(), b[1] - g.marginBelow());
            b[4] = Math.min(w.getMaxHeight() - 1, b[4] + g.marginAbove());
        }
        return out;
    }

    private static void grow(Map<String, int[]> out, Lieu l, boolean create) {
        int x = (int) Math.floor(l.x());
        int y = (int) Math.floor(l.y());
        int z = (int) Math.floor(l.z());
        int[] b = out.get(l.world());
        if (b == null) {
            if (create) {
                out.put(l.world(), new int[]{x, y, z, x, y, z});
            }
            return;
        }
        b[0] = Math.min(b[0], x);
        b[1] = Math.min(b[1], y);
        b[2] = Math.min(b[2], z);
        b[3] = Math.max(b[3], x);
        b[4] = Math.max(b[4], y);
        b[5] = Math.max(b[5], z);
    }

    /** Lecture d'un monde : une case = le numéro de son bloc dans la palette, puis la palette est classée. */
    private static final class Reading {
        final int[] b;
        final int nx;
        final int ny;
        final int nz;
        final char[] blocks;
        final Map<BlockData, Character> numbers = new HashMap<>();
        final List<BlockData> palette = new ArrayList<>();
        final Map<Material, Integer> plain = new HashMap<>();
        int waiting;

        Reading(int[] b) {
            this.b = b;
            nx = b[3] - b[0] + 1;
            ny = b[4] - b[1] + 1;
            nz = b[5] - b[2] + 1;
            blocks = new char[Math.multiplyExact(Math.multiplyExact(nx, ny), nz)];
            palette.add(Material.AIR.createBlockData());
            numbers.put(palette.get(0), (char) 0);
        }

        char number(BlockData d) {
            return numbers.computeIfAbsent(d, k -> {
                palette.add(k);
                return (char) (palette.size() - 1);
            });
        }

        void decode(ChunkSnapshot s) {
            int cx = s.getX() << 4;
            int cz = s.getZ() << 4;
            for (int y = b[1]; y <= b[4]; y++) {
                for (int z = Math.max(cz, b[2]); z <= Math.min(cz + 15, b[5]); z++) {
                    for (int x = Math.max(cx, b[0]); x <= Math.min(cx + 15, b[3]); x++) {
                        Material m = s.getBlockType(x & 15, y, z & 15);
                        if (m.isAir()) {
                            continue;
                        }
                        int known = plain.computeIfAbsent(m, k -> {
                            BlockData d = k.createBlockData();
                            return d.getAsString().indexOf('[') < 0 ? (int) number(d) : -1;
                        });
                        blocks[((y - b[1]) * nz + z - b[2]) * nx + x - b[0]] = known >= 0 ? (char) known
                                : number(s.getBlockData(x & 15, y, z & 15));
                    }
                }
            }
        }
    }

    private void read(Monde m, Guidage g) {
        World w = Bukkit.getWorld(m.name);
        if (w == null) {
            return;
        }
        if (m.building) {
            m.again = true;
            return;
        }
        m.building = true;
        m.again = false;
        long started = System.currentTimeMillis();
        Reading r = new Reading(m.bounds.clone());
        Deque<long[]> chunks = new ArrayDeque<>();
        for (int cx = r.b[0] >> 4; cx <= r.b[3] >> 4; cx++) {
            for (int cz = r.b[2] >> 4; cz <= r.b[5] >> 4; cz++) {
                chunks.add(new long[]{cx, cz});
            }
        }
        r.waiting = chunks.size();
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (int i = 0; i < g.chunksPerTick() && !chunks.isEmpty(); i++) {
                long[] c = chunks.poll();
                w.getChunkAtAsync((int) c[0], (int) c[1], false).whenComplete((chunk, error) -> {
                    ChunkSnapshot s = chunk == null ? null : chunk.getChunkSnapshot(false, false, false);
                    run(() -> {
                        try {
                            if (s != null) {
                                r.decode(s);
                            }
                        } finally {
                            if (--r.waiting == 0) {
                                plugin.sync(() -> classify(m, g, w, r, started));
                            }
                        }
                    });
                });
            }
            if (chunks.isEmpty()) {
                task[0].cancel();
            }
        }, 1L, 1L);
        tasks.add(task[0]);
    }

    private void classify(Monde m, Guidage g, World w, Reading r, long started) {
        byte[] kinds = new byte[r.palette.size()];
        Location at = w.getSpawnLocation();
        int[] i = {0};
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (int n = 0; n < g.blocksPerTick() && i[0] < kinds.length; n++, i[0]++) {
                kinds[i[0]] = Blocs.of(r.palette.get(i[0]), at, g.open(), g.closed());
            }
            if (i[0] == kinds.length) {
                task[0].cancel();
                Location spawn = w.getSpawnLocation();
                run(() -> {
                    try {
                        build(m, g, r, kinds, spawn, started);
                    } finally {
                        m.building = false;
                    }
                });
            }
        }, 1L, 1L);
        tasks.add(task[0]);
    }

    private void build(Monde m, Guidage g, Reading r, byte[] kinds, Location spawn, long started) {
        byte[] cells = new byte[r.blocks.length];
        for (int i = 0; i < cells.length; i++) {
            cells[i] = kinds[r.blocks[i]];
        }
        List<Boite> forbidden = g.forbidden().stream().filter(b -> b.world().equals(m.name)).toList();
        Carte map = new Carte(r.b[0], r.b[1], r.b[2], r.nx, r.ny, r.nz, cells, g.costs(), forbidden);
        List<int[]> from = new ArrayList<>();
        from.add(new int[]{spawn.getBlockX(), spawn.getBlockY(), spawn.getBlockZ()});
        for (Lieu l : g.departures()) {
            if (l.world().equals(m.name)) {
                from.add(new int[]{(int) Math.floor(l.x()), (int) Math.floor(l.y()), (int) Math.floor(l.z())});
            }
        }
        for (Guidage.Shortcut s : g.shortcuts()) {
            if (s.place().world().equals(m.name)) {
                from.add(new int[]{(int) Math.floor(s.place().x()), (int) Math.floor(s.place().y()), (int) Math.floor(s.place().z())});
            }
        }
        Reseau network = Reseau.build(map, from, g.links(), m.name);
        m.network = network;
        m.routes.clear();
        m.pending.clear();
        m.building = false;
        m.seconds = (System.currentTimeMillis() - started) / 1000;
        if (m.again) {
            plugin.sync(() -> read(m, plugin.settings().guidage()));
        }
        plugin.getLogger().info("Carte de " + m.name + " prête en " + m.seconds + " s, " + network.size() + " cases, " + network.edges() + " déplacements");
        prepareRoutes(m);
    }

    /** Chemins vers tous les collectibles du monde, calculés d'avance un par un. */
    private void prepareRoutes(Monde m) {
        for (Collection c : plugin.settings().collections().values()) {
            if (c.enabled() && c.detector()) {
                for (Collection.Item i : c.items().values()) {
                    if (i.place().world().equals(m.name)) {
                        route(m, c, i);
                    }
                }
            }
        }
    }

    /** Chemins vers un collectible ; null tant qu'ils ne sont pas calculés. */
    Route route(Monde m, Collection c, Collection.Item i) {
        String key = c.id() + "/" + i.id();
        Route r = m.routes.get(key);
        Reseau network = m.network;
        if (r == null && network != null && m.pending.add(key)) {
            double reach = plugin.settings().guidage().walk().reach();
            run(() -> {
                List<Integer> arrivals = network.arrivals(i.place(), reach, EYES);
                boolean reached = !arrivals.isEmpty();
                if (!reached) {
                    int closest = network.closest(i.center());
                    arrivals = closest < 0 ? List.of() : List.of(closest);
                }
                int end = arrivals.isEmpty() ? -1 : arrivals.get(0);
                Route made = new Route(network.field(arrivals), reached,
                        end < 0 ? "-" : network.x(end) + " " + network.y(end) + " " + network.z(end));
                if (m.network == network) {
                    m.routes.put(key, made);
                }
                m.pending.remove(key);
            });
        }
        return r;
    }
}
