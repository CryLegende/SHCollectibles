package org.Cry_Legende.sHCollectibles;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Détecteurs actifs. Le chemin à pied de chaque joueur est calculé hors du fil principal à partir de sa position ; le fil
 * principal n'applique que le résultat : flèche, trace au sol, tout près, bruit. Sans carte, la flèche suit les étapes de
 * secours du collectible.
 */
final class Detecteur implements Listener {

    /** Un collectible qu'aucun chemin n'atteint passe après tous les autres. */
    private static final double UNREACHED = 10_000;

    /** Position d'un joueur à un instant. */
    private record Request(Active active, Collection collection, Donnees data, String world, double x, double y, double z) {
    }

    /** Chemin trouvé ; « reached » faux : il mène au plus près. */
    private record Result(Collection.Item item, String world, boolean reached, Trajet path, String shortcut) {
    }

    /** Détecteur d'un joueur. « place » et « text » sont lus par les placeholders depuis n'importe quel fil. */
    static final class Active {
        volatile Collection collection;
        volatile String place = "";
        volatile String text = "";
        volatile int target = -1;
        private volatile Result result;
        private Result drawn;
        private long drawnDistance;
        final int slot;
        int step;
        int steps;
        double distance;
        boolean near;
        boolean onPath;
        int announced = -1;
        long sinceNoise;
        Location last;
        /** Jour où la collection a été comptée. */
        LocalDate day;

        Active(Collection collection, int slot) {
            this.collection = collection;
            this.slot = slot;
        }
    }

    private final SHCollectibles plugin;
    private final Map<UUID, Active> actives = new ConcurrentHashMap<>();
    private final AtomicBoolean computing = new AtomicBoolean();
    private final AtomicInteger slots = new AtomicInteger();
    private volatile Set<Collection.Item> missing = Set.of();
    private int now;

    Detecteur(SHCollectibles plugin) {
        this.plugin = plugin;
    }

    Active get(UUID id) {
        return actives.get(id);
    }

    /** Active le détecteur de la collection, ou l'arrête s'il l'était déjà. */
    void toggle(Player p, Collection c) {
        Active a = actives.get(p.getUniqueId());
        if (a != null && a.collection.id().equals(c.id())) {
            stop(p);
            return;
        }
        Textes m = plugin.messages();
        Donnees d = plugin.joueurs().get(p.getUniqueId());
        if (!c.enabled()) {
            m.send(p, "fermee", "nom", c.coloredName());
        } else if (!Recherche.allowed(p, c)) {
            m.send(p, "anciennete", "nom", c.coloredName());
        } else if (d == null) {
            m.send(p, "chargement");
        } else if (d.completed().contains(c.id())) {
            m.send(p, "deja-complete", "nom", c.coloredName());
        } else if (blocked(p, d, c)) {
            LocalDateTime now = LocalDateTime.now();
            long left = plugin.settings().limit().minutesLeft(now);
            m.send(p, "limite", "n", Integer.toString(plugin.settings().limit().perDay()),
                    "liste", names(d.tracked(plugin.today())), "heures", Long.toString(left / 60), "minutes", Long.toString(left % 60));
        } else {
            Active now = new Active(c, slots.incrementAndGet());
            actives.put(p.getUniqueId(), now);
            count(p, now, d, plugin.today());
            m.send(p, limited(p) ? "detecteur-active-limite" : "detecteur-active", "nom", c.coloredName(),
                    "n", Integer.toString(plugin.settings().limit().perDay()));
            Effets.sound(p, plugin.settings().noise(), p.getLocation());
            p.closeInventory();
            if (left(p.getWorld().getName(), c, d).isEmpty() && !plugin.settings().otherWorldCommand().isEmpty()) {
                p.performCommand(plugin.settings().otherWorldCommand());
            }
        }
    }

    boolean limited(Player p) {
        return plugin.settings().limit().limited() && !p.hasPermission("shcollectibles.detecteur.illimite");
    }

    /** Vrai si le joueur a déjà suivi aujourd'hui autant de collections qu'il peut, sans celle-ci. */
    boolean blocked(Player p, Donnees d, Collection c) {
        return limited(p) && !plugin.settings().limit().allows(d.tracked(plugin.today()), c.id());
    }

    /** La collection suivie compte dans la journée du joueur (gardé en base). */
    private void count(Player p, Active a, Donnees d, LocalDate day) {
        a.day = day;
        Set<String> used = new HashSet<>(d.tracked(day));
        if (limited(p) && used.add(a.collection.id())) {
            d.track(day, used);
            UUID id = p.getUniqueId();
            String collection = a.collection.id();
            plugin.joueurs().io("Enregistrement du détecteur de " + p.getName(), () -> plugin.store().track(id, day, collection));
        }
    }

    String names(Set<String> ids) {
        return String.join("&7, ", ids.stream().sorted().map(id -> {
            Collection c = plugin.settings().find(id);
            return c == null ? id : c.coloredName();
        }).toList());
    }

    /** Arrête le détecteur du joueur ; faux s'il n'en avait pas. */
    boolean stop(Player p) {
        Active a = actives.remove(p.getUniqueId());
        if (a == null) {
            return false;
        }
        plugin.messages().send(p, "detecteur-arrete", "nom", a.collection.coloredName());
        p.closeInventory();
        return true;
    }

    /** Après une trouvaille : l'arrêt si la collection est complète, sinon la cible suivante au prochain passage. */
    void onFind(Player p) {
        Active a = actives.get(p.getUniqueId());
        Donnees d = plugin.joueurs().get(p.getUniqueId());
        if (a != null && d != null) {
            if (d.completed().contains(a.collection.id())) {
                actives.remove(p.getUniqueId());
            } else {
                clear(a);
            }
        }
    }

    private static void clear(Active a) {
        a.result = null;
        a.drawn = null;
        a.target = -1;
        a.near = false;
        a.onPath = false;
        a.place = "";
        a.text = "";
    }

    /** Après un reload : chaque détecteur reprend sa collection, ou s'arrête si elle n'y est plus. */
    void onReload() {
        for (Map.Entry<UUID, Active> en : actives.entrySet()) {
            Collection c = plugin.settings().collections().get(en.getValue().collection.id());
            if (c == null || !c.detector()) {
                actives.remove(en.getKey());
            } else {
                en.getValue().collection = c;
                clear(en.getValue());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        actives.remove(e.getPlayer().getUniqueId());
    }

    /** À chaque tick : les détecteurs dont c'est le tour (chacun toutes les « guidage.suivi.intervalle-ticks »). */
    void tick() {
        if (actives.isEmpty()) {
            return;
        }
        now++;
        List<Request> requests = new ArrayList<>();
        LocalDate today = plugin.today();
        int period = plugin.settings().guidage().walk().updateTicks();
        Iterator<Map.Entry<UUID, Active>> it = actives.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Active> en = it.next();
            Active a = en.getValue();
            if ((a.slot + now) % period != 0) {
                continue;
            }
            Player p = Bukkit.getPlayer(en.getKey());
            Donnees d = plugin.joueurs().get(en.getKey());
            if (p == null || !a.collection.enabled() || d != null && d.completed().contains(a.collection.id())) {
                it.remove();
                continue;
            }
            if (d == null) {
                continue;
            }
            if (!today.equals(a.day)) {
                count(p, a, d, today);
            }
            Location l = p.getLocation();
            apply(p, l, a, d);
            noise(p, a, period);
            requests.add(new Request(a, a.collection, d, l.getWorld().getName(), l.getX(), l.getY(), l.getZ()));
        }
        if (!requests.isEmpty() && !plugin.cartes().all().isEmpty() && computing.compareAndSet(false, true)) {
            plugin.cartes().runLive(() -> {
                try {
                    for (Request r : requests) {
                        r.active().result = compute(r);
                    }
                } finally {
                    computing.set(false);
                }
            });
        }
    }

    /** Hors du fil principal : le collectible au chemin le plus court, et ce chemin. null si rien n'est connu. */
    private Result compute(Request q) {
        Cartes.Monde w = plugin.cartes().get(q.world());
        Reseau r = w == null ? null : w.network;
        if (r == null) {
            return null;
        }
        Guidage g = plugin.settings().guidage();
        int node = r.locate(q.x(), q.y(), q.z(), g.walk().locateDown(), g.walk().locateAround());
        if (node < 0) {
            return null;
        }
        List<Choix.Candidate> list = new ArrayList<>();
        for (Collection.Item i : left(q.world(), q.collection(), q.data())) {
            Cartes.Route route = plugin.cartes().route(w, q.collection(), i);
            if (route != null && route.field().reaches(node)) {
                list.add(new Choix.Candidate(i, route.field().cost(node) + (route.reached() ? 0 : UNREACHED)));
            }
        }
        Choix.Candidate best = Choix.target(list, q.active().target, plugin.settings().margin());
        if (best == null) {
            return null;
        }
        Cartes.Route route = plugin.cartes().route(w, q.collection(), best.item());
        Trajet path = route == null ? null : Trajet.from(r, route.field(), node, q.world(), q.x(), q.y(), q.z(), g);
        if (path == null) {
            return null;
        }
        String shortcut = "";
        double gain = 0;
        for (Guidage.Shortcut s : g.shortcuts()) {
            int at = s.place().world().equals(q.world()) ? r.locate(s.place().x(), s.place().y(), s.place().z(),
                    g.walk().locateDown(), g.walk().locateAround()) : -1;
            if (at >= 0 && route.field().reaches(at)) {
                double won = route.field().cost(node) - route.field().cost(at);
                if (won >= s.gain() && won > gain) {
                    gain = won;
                    shortcut = s.command();
                }
            }
        }
        return new Result(best.item(), q.world(), route.reached(), path, shortcut);
    }

    private void apply(Player p, Location l, Active a, Donnees d) {
        Reglages cfg = plugin.settings();
        if (a.last != null && (a.last.getWorld() != l.getWorld() || a.last.distanceSquared(l) > cfg.teleport() * cfg.teleport())) {
            a.step = 0;
            a.result = null;
        }
        a.last = l;
        Result r = a.result;
        if (r == null || !r.world().equals(l.getWorld().getName()) || d.read(a.collection.id()).contains(r.item().id())) {
            bySteps(p, l, a, left(l.getWorld().getName(), a.collection, d));
        } else if (r.reached() || r.item().steps().isEmpty()) {
            byPath(p, l, a, r);
        } else {
            bySteps(p, l, a, List.of(r.item()));
        }
    }

    private void byPath(Player p, Location l, Active a, Result r) {
        Reglages cfg = plugin.settings();
        boolean same = a.target == r.item().id() && a.onPath;
        double straight = r.item().center().distance(l.getX(), l.getY(), l.getZ());
        // Sans chemin jusqu'au bout, la fin se fait à vue
        a.distance = r.reached() ? r.path().length() : r.path().length() + straight;
        a.target = r.item().id();
        a.onPath = true;
        a.near = Choix.near(a.near && same, a.distance, cfg.near(), cfg.far());
        if (a.near) {
            arrive(p, a, r.item());
            return;
        }
        // Rien à refaire tant que le chemin n'a pas changé
        if (a.drawn == r && !a.place.isEmpty()) {
            return;
        }
        Lieu point = !r.reached() && r.path().length() < cfg.near() ? r.item().center() : r.path().point();
        a.drawn = r;
        show(a, point.text(), a.distance, r.shortcut());
    }

    /** Sans chemin connu : étapes de secours, puis droit vers le collectible. */
    private void bySteps(Player p, Location l, Active a, List<Collection.Item> items) {
        Reglages cfg = plugin.settings();
        boolean covered = l.getBlock().getLightFromSky() <= cfg.rules().coveredLight();
        List<Choix.Candidate> list = new ArrayList<>();
        for (Collection.Item i : items) {
            int step = Choix.step(i, i.id() == a.target ? a.step : 0, l.getX(), l.getY(), l.getZ(), covered, cfg.rules());
            list.add(new Choix.Candidate(i, Choix.length(i, step, l.getX(), l.getY(), l.getZ())));
        }
        Choix.Candidate t = Choix.target(list, a.target, cfg.margin());
        if (t == null) {
            clear(a);
            return;
        }
        Collection.Item item = t.item();
        boolean same = a.target == item.id() && !a.onPath;
        a.target = item.id();
        a.onPath = false;
        a.steps = item.steps().size();
        a.step = Choix.step(item, same ? a.step : 0, l.getX(), l.getY(), l.getZ(), covered, cfg.rules());
        boolean arrived = a.step >= a.steps;
        a.distance = t.length();
        a.near = arrived && Choix.near(a.near && same, a.distance, cfg.near(), cfg.far());
        if (a.near) {
            arrive(p, a, item);
        } else {
            show(a, (arrived ? item.center() : item.steps().get(a.step)).text(), a.distance, "");
        }
    }

    private void arrive(Player p, Active a, Collection.Item item) {
        a.place = "";
        a.text = "";
        if (a.announced != item.id()) {
            a.announced = item.id();
            String text = plugin.messages().text("barre-proche", "objet", a.collection.coloredObject());
            if (!text.isEmpty()) {
                p.sendActionBar(Textes.component(text));
            }
        }
    }

    private void show(Active a, String place, double distance, String shortcut) {
        Textes m = plugin.messages();
        long shown = Math.round(distance) * 31 + shortcut.hashCode();
        if (shown == a.drawnDistance && !a.text.isEmpty()) {
            a.place = place;
            return;
        }
        a.drawnDistance = shown;
        a.text = m.text("fleche.texte", "objet", a.collection.coloredObject(), "distance", Long.toString(Math.round(distance)))
                + (shortcut.isEmpty() ? "" : m.text("fleche.raccourci", "commande", shortcut));
        a.place = place;
    }

    /** Le bruit suit la longueur du chemin : jamais à travers un mur ou un plancher. */
    private void noise(Player p, Active a, int period) {
        a.sinceNoise += period;
        Reglages cfg = plugin.settings();
        if (a.target < 0 || a.distance > cfg.noiseReach() || a.sinceNoise < cfg.noiseTicks() || !a.onPath && a.step < a.steps) {
            return;
        }
        Collection.Item item = a.collection.items().get(a.target);
        Location at = item == null ? null : item.center().location();
        if (at != null) {
            a.sinceNoise = 0;
            Effets.sound(p, cfg.noise(), at);
        }
    }

    /** Trace au sol, toutes les « guidage.trace.intervalle-ticks ». */
    void trail() {
        if (plugin.settings().guidage().trail().enabled() && !actives.isEmpty()) {
            plugin.cartes().runLive(this::drawTrail);
        }
    }

    private void drawTrail() {
        Guidage.Trail t = plugin.settings().guidage().trail();
        for (Map.Entry<UUID, Active> en : actives.entrySet()) {
            Active a = en.getValue();
            Result r = a.result;
            Player p = r == null || !a.onPath || a.near || a.place.isEmpty() ? null : Bukkit.getPlayer(en.getKey());
            if (p == null || !p.getWorld().getName().equals(r.world())) {
                continue;
            }
            Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(a.collection.trailColor()), t.size());
            for (Lieu l : r.path().trail()) {
                p.spawnParticle(Particle.DUST, l.x(), l.y(), l.z(), 1, 0, 0, 0, 0, dust);
            }
        }
    }

    /** Collectibles pas encore trouvés dans un monde, têtes posées seulement. */
    private List<Collection.Item> left(String world, Collection c, Donnees d) {
        Set<Integer> found = d.read(c.id());
        Set<Collection.Item> absent = missing;
        List<Collection.Item> out = new ArrayList<>();
        for (Collection.Item item : c.items().values()) {
            if (item.place().world().equals(world) && !found.contains(item.id()) && !absent.contains(item)) {
                out.add(item);
            }
        }
        return out;
    }

    /** Têtes pas posées (case vide) : le détecteur n'y emmène personne. Toutes les « detecteur.verification-secondes ». */
    void checkHeads() {
        Set<Collection.Item> out = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Collection c : plugin.settings().collections().values()) {
            if (!c.enabled() || !c.detector()) {
                continue;
            }
            for (Collection.Item item : c.items().values()) {
                Lieu at = item.place();
                World w = Bukkit.getWorld(at.world());
                int x = (int) Math.floor(at.x());
                int z = (int) Math.floor(at.z());
                if (w != null && (w.isChunkLoaded(x >> 4, z >> 4) ? w.getBlockAt(x, (int) Math.floor(at.y()), z).isEmpty() : missing.contains(item))) {
                    out.add(item);
                }
            }
        }
        missing = out;
    }
}
