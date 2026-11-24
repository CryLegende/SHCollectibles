package org.Cry_Legende.sHCollectibles;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
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

import java.time.Duration;
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
 * Détecteurs actifs. Le chemin à pied de chaque joueur est calculé sur le fil des chemins à partir de sa position, et gardé
 * tant qu'il est en l'air : un saut ne change jamais de chemin. Il est refait dès qu'il se pose. Le fil principal ne fait que
 * relever les positions et jouer la zone de fouille et le bruit. Sans carte, la flèche suit les étapes de secours.
 */
final class Detecteur implements Listener {

    /** Un collectible qu'aucun chemin n'atteint passe après tous les autres. */
    private static final double UNREACHED = 10_000;
    private static final long TICK = 50_000_000L;

    /** Position d'un joueur à un instant ; « grounded » : porté (sol, échelle, eau, vol) ; « covered » : à l'abri du ciel. */
    private record Request(UUID id, Active active, Collection collection, Donnees data, String world, double x, double y, double z,
                           boolean grounded, boolean covered) {
    }

    /** Chemin trouvé ; « reached » faux : il mène au plus près. */
    private record Result(Collection.Item item, String world, boolean reached, Trajet path, String shortcut) {
    }

    /** Détecteur d'un joueur. « place », « text » et « end » sont lus par les placeholders depuis n'importe quel fil. */
    static final class Active {
        volatile Collection collection;
        volatile String place = "";
        volatile String text = "";
        /** Texte d'adieu de la flèche dans la zone de fouille ; vide ailleurs. */
        volatile String end = "";
        volatile int target = -1;
        volatile int step;
        volatile int steps;
        volatile double distance;
        volatile boolean near;
        volatile boolean onPath;
        /** Niveau de la zone de fouille, -1 hors de la zone. */
        volatile int level = -1;
        private volatile Result result;
        private volatile boolean air;
        final int slot;
        // Fil des chemins
        private Result drawn;
        private long drawnText = Long.MIN_VALUE;
        private long groundAt;
        private long pathAt;
        private String lastWorld = "";
        private double lastX;
        private double lastY;
        private double lastZ;
        private boolean covered;
        // Fil principal
        int announced = -1;
        long sinceNoise;
        private boolean searching;
        private int shownLevel = -1;
        private BossBar bar;
        /** Jour où la collection a été comptée. */
        LocalDate day;

        /** « monde x y z » du prochain point de la flèche. */
        String next() {
            String[] p = place.split(" ");
            int at = p.length >= 7 ? 4 : 1;
            return p.length < 4 ? place : p[0] + " " + p[at] + " " + p[at + 1] + " " + p[at + 2];
        }

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
    private int trails;

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
            leaveSearch(p, actives.put(p.getUniqueId(), now));
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
        leaveSearch(p, a);
        plugin.messages().send(p, "detecteur-arrete", "nom", a.collection.coloredName());
        p.closeInventory();
        return true;
    }

    /** Après une trouvaille : l'arrêt si la collection est complète, sinon la cible suivante au prochain passage. */
    void onFind(Player p) {
        Active a = actives.get(p.getUniqueId());
        Donnees d = plugin.joueurs().get(p.getUniqueId());
        if (a != null && d != null) {
            leaveSearch(p, a);
            if (d.completed().contains(a.collection.id())) {
                actives.remove(p.getUniqueId());
            } else {
                clear(a);
            }
        }
    }

    private void clear(Active a) {
        a.end = "";
        a.place = "";
        a.text = "";
        a.near = false;
        a.level = -1;
        plugin.cartes().runLive(() -> forget(a));
    }

    /** Fil des chemins : le chemin est repris de zéro. */
    private static void forget(Active a) {
        a.result = null;
        a.drawn = null;
        a.drawnText = Long.MIN_VALUE;
        a.target = -1;
        a.step = 0;
        a.near = false;
        a.level = -1;
        a.onPath = false;
        a.end = "";
        a.place = "";
        a.text = "";
    }

    /** Après un reload : chaque détecteur reprend sa collection, ou s'arrête si elle n'y est plus. */
    void onReload() {
        for (Map.Entry<UUID, Active> en : actives.entrySet()) {
            Collection c = plugin.settings().collections().get(en.getValue().collection.id());
            leaveSearch(Bukkit.getPlayer(en.getKey()), en.getValue());
            if (c == null || !c.detector()) {
                actives.remove(en.getKey());
            } else {
                en.getValue().collection = c;
                clear(en.getValue());
            }
        }
    }

    /** Arrêt du plugin : les barres sont retirées. */
    void close() {
        actives.forEach((id, a) -> leaveSearch(Bukkit.getPlayer(id), a));
        actives.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        leaveSearch(e.getPlayer(), actives.remove(e.getPlayer().getUniqueId()));
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
                leaveSearch(p, a);
                continue;
            }
            if (d == null) {
                continue;
            }
            if (!today.equals(a.day)) {
                count(p, a, d, today);
            }
            search(p, a);
            noise(p, a, period);
            Location l = p.getLocation();
            Cartes.Monde w = plugin.cartes().get(l.getWorld().getName());
            // La lumière ne sert qu'aux étapes de secours
            boolean covered = (w == null || w.network == null || !a.onPath)
                    && l.getBlock().getLightFromSky() <= plugin.settings().rules().coveredLight();
            requests.add(new Request(en.getKey(), a, a.collection, d, l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), grounded(p), covered));
        }
        if (!requests.isEmpty() && computing.compareAndSet(false, true)) {
            plugin.cartes().runLive(() -> {
                try {
                    for (Request r : requests) {
                        compute(r);
                    }
                } finally {
                    computing.set(false);
                }
            });
        }
    }

    /** Porté par quelque chose : le chemin peut être refait de là où il est. */
    @SuppressWarnings("deprecation")
    private static boolean grounded(Player p) {
        return p.isOnGround() || p.isFlying() || p.isClimbing() || p.isInWater() || p.isGliding() || p.isInsideVehicle();
    }

    /** Toutes les 50 ms, sur le fil des chemins : le chemin d'un joueur qui vient de se poser est refait tout de suite. */
    void landings() {
        for (Map.Entry<UUID, Active> en : actives.entrySet()) {
            Active a = en.getValue();
            if (!a.air) {
                continue;
            }
            Player p = Bukkit.getPlayer(en.getKey());
            Donnees d = plugin.joueurs().get(en.getKey());
            if (p != null && d != null && grounded(p)) {
                Location l = p.getLocation();
                compute(new Request(en.getKey(), a, a.collection, d, l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), true, a.covered));
            }
        }
    }

    /** Fil des chemins : met à jour la flèche d'un joueur. */
    private void compute(Request q) {
        Active a = q.active();
        if (actives.get(q.id()) != a || a.collection != q.collection()) {
            return;
        }
        Reglages cfg = plugin.settings();
        Guidage g = cfg.guidage();
        long time = System.nanoTime();
        double dx = q.x() - a.lastX;
        double dy = q.y() - a.lastY;
        double dz = q.z() - a.lastZ;
        if (!a.lastWorld.equals(q.world()) || dx * dx + dy * dy + dz * dz > cfg.teleport() * cfg.teleport()) {
            forget(a);
        }
        a.lastWorld = q.world();
        a.lastX = q.x();
        a.lastY = q.y();
        a.lastZ = q.z();
        a.covered = q.covered();
        a.air = !q.grounded();
        if (q.grounded()) {
            a.groundAt = time;
        }
        Result held = a.result;
        if (held != null && (!a.onPath || q.data().read(q.collection().id()).contains(held.item().id()))) {
            held = null;
        }
        Cartes.Monde w = plugin.cartes().get(q.world());
        Reseau r = w == null ? null : w.network;
        if (r != null) {
            // En l'air, le chemin ne change pas : la flèche avance dessus avec le joueur
            if (held != null && !q.grounded() && time - a.groundAt < g.walk().airTicks() * TICK) {
                return;
            }
            Result found = path(q, w, r, g);
            if (found == null && held != null && time - a.pathAt < g.walk().lostTicks() * TICK) {
                return;
            }
            if (found != null && (found.reached() || found.item().steps().isEmpty())) {
                a.pathAt = time;
                byPath(q, a, found);
                return;
            }
            if (found != null) {
                bySteps(q, a, List.of(found.item()));
                return;
            }
        }
        bySteps(q, a, left(q.world(), q.collection(), q.data()));
    }

    /** Le collectible au chemin le plus court, et ce chemin ; null si rien n'est connu. */
    private Result path(Request q, Cartes.Monde w, Reseau r, Guidage g) {
        int node = r.locate(q.x(), q.y(), q.z(), g.walk().locateDown(), g.walk().locateAround());
        if (node < 0 && g.walk().rejoin() > 0) {
            node = r.near(q.x(), q.y(), q.z(), g.walk().rejoin());
        }
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

    private void byPath(Request q, Active a, Result r) {
        Reglages cfg = plugin.settings();
        boolean same = a.target == r.item().id() && a.onPath;
        double straight = r.item().center().distance(q.x(), q.y(), q.z());
        // Sans chemin jusqu'au bout, la fin se fait à vue
        a.distance = r.reached() ? r.path().length() : r.path().length() + straight;
        a.target = r.item().id();
        a.onPath = true;
        a.result = r;
        // Un double saut y mène encore : la flèche le montre, même tout près
        boolean leaps = cfg.search().afterLeap() >= 0 && r.path().leapLeft(cfg.search().afterLeap());
        if (Choix.near(a.near && same, a.distance, cfg.near(), cfg.far()) && !leaps) {
            enter(a);
            return;
        }
        boolean end = !r.reached() && r.path().length() < cfg.near();
        show(a, end ? r.item().center().text() : r.path().route(), r.shortcut(), leapSoon(r));
    }

    /** Vrai si l'élan d'un double saut est assez proche pour l'annoncer. */
    private boolean leapSoon(Result r) {
        return r.path().leap() != null && r.path().leap().away() <= plugin.settings().guidage().hint().announce();
    }

    /** Sans chemin connu : étapes de secours, puis droit vers le collectible. */
    private void bySteps(Request q, Active a, List<Collection.Item> items) {
        Reglages cfg = plugin.settings();
        List<Choix.Candidate> list = new ArrayList<>();
        for (Collection.Item i : items) {
            int step = Choix.step(i, i.id() == a.target ? a.step : 0, q.x(), q.y(), q.z(), q.covered(), cfg.rules());
            list.add(new Choix.Candidate(i, Choix.length(i, step, q.x(), q.y(), q.z())));
        }
        Choix.Candidate t = Choix.target(list, a.target, cfg.margin());
        if (t == null) {
            forget(a);
            return;
        }
        Collection.Item item = t.item();
        boolean same = a.target == item.id() && !a.onPath;
        a.result = null;
        a.target = item.id();
        a.onPath = false;
        a.steps = item.steps().size();
        a.step = Choix.step(item, same ? a.step : 0, q.x(), q.y(), q.z(), q.covered(), cfg.rules());
        boolean arrived = a.step >= a.steps;
        a.distance = t.length();
        if (arrived && Choix.near(a.near && same, a.distance, cfg.near(), cfg.far())) {
            enter(a);
        } else {
            show(a, (arrived ? item.center() : item.steps().get(a.step)).text(), "", false);
        }
    }

    /** Dans la zone de fouille : la flèche s'en va avec son texte d'adieu. */
    private void enter(Active a) {
        a.level = plugin.settings().search().level(a.distance, a.level);
        a.near = true;
        a.end = plugin.messages().text("fleche.fin", "objet", a.collection.coloredObject());
        a.place = "";
        a.text = "";
        a.drawn = null;
        a.drawnText = Long.MIN_VALUE;
    }

    private void show(Active a, String place, String shortcut, boolean leap) {
        Textes m = plugin.messages();
        long key = (Math.round(a.distance) * 2 + (leap ? 1 : 0)) * 31 + shortcut.hashCode();
        if (key != a.drawnText) {
            a.drawnText = key;
            a.text = m.text("fleche.texte", "objet", a.collection.coloredObject(), "distance", Long.toString(Math.round(a.distance)))
                    + (leap ? m.text("fleche.double-saut") : "") + (shortcut.isEmpty() ? "" : m.text("fleche.raccourci", "commande", shortcut));
        }
        a.place = place;
        a.near = false;
        a.level = -1;
        a.end = "";
    }

    /** Fil principal : titre, son et barre de la zone de fouille. */
    private void search(Player p, Active a) {
        Fouille s = plugin.settings().search();
        boolean in = a.near && s.enabled() && a.target >= 0;
        if (!in) {
            if (a.searching) {
                leaveSearch(p, a);
                if (a.target >= 0) {
                    bar(p, "fouille.eloigne", a);
                }
            }
            return;
        }
        if (!a.searching) {
            a.searching = true;
            if (a.announced != a.target) {
                a.announced = a.target;
                String title = plugin.messages().text("fouille.titre", "objet", a.collection.coloredObject());
                String under = plugin.messages().text("fouille.sous-titre", "objet", a.collection.coloredObject());
                if (!title.isEmpty() || !under.isEmpty()) {
                    p.showTitle(Title.title(Textes.component(title), Textes.component(under),
                            Title.Times.times(ticks(s.fadeIn()), ticks(s.stay()), ticks(s.fadeOut()))));
                }
            }
            Effets.sound(p, s.sound(), p.getLocation());
        }
        int level = Math.max(0, a.level);
        if (s.bar() && level != a.shownLevel && level < s.levels().size()) {
            Fouille.Level l = s.levels().get(level);
            Component text = Textes.component(plugin.messages().text("fouille.barre", "objet", a.collection.coloredObject(),
                    "niveau", plugin.messages().text("fouille.niveaux." + l.id())));
            if (a.bar == null) {
                a.bar = BossBar.bossBar(text, l.fill(), l.color(), s.overlay());
                p.showBossBar(a.bar);
            } else {
                a.bar.name(text).progress(l.fill()).color(l.color());
            }
            a.shownLevel = level;
        }
    }

    private static Duration ticks(int n) {
        return Duration.ofMillis(n * 50L);
    }

    /** Barre d'action ; un texte vide n'est pas envoyé. */
    private void bar(Player p, String key, Active a) {
        String text = plugin.messages().text(key, "objet", a.collection.coloredObject());
        if (!text.isEmpty()) {
            p.sendActionBar(Textes.component(text));
        }
    }

    private static void leaveSearch(Player p, Active a) {
        if (a == null) {
            return;
        }
        a.searching = false;
        a.shownLevel = -1;
        BossBar bar = a.bar;
        a.bar = null;
        if (bar != null && p != null) {
            p.hideBossBar(bar);
        }
    }

    /** Le bruit suit la longueur du chemin : jamais à travers un mur ou un plancher. */
    private void noise(Player p, Active a, int period) {
        a.sinceNoise += period;
        Reglages cfg = plugin.settings();
        int target = a.target;
        if (target < 0 || a.distance > cfg.noiseReach() || a.sinceNoise < cfg.noiseTicks() || !a.onPath && a.step < a.steps) {
            return;
        }
        Collection.Item item = a.collection.items().get(target);
        Location at = item == null ? null : item.center().location();
        if (at != null) {
            a.sinceNoise = 0;
            Effets.sound(p, cfg.noise(), at);
        }
    }

    /** À chaque tick : trace au sol et ligne des doubles sauts, chacune à son rythme. */
    void trail() {
        if (actives.isEmpty()) {
            return;
        }
        trails++;
        Guidage g = plugin.settings().guidage();
        boolean ground = g.trail().enabled() && trails % g.trail().intervalTicks() == 0;
        boolean leap = g.hint().line() && trails % g.hint().intervalTicks() == 0;
        if (ground || leap) {
            plugin.cartes().runLive(() -> draw(ground, leap));
        }
    }

    private void draw(boolean ground, boolean leap) {
        Guidage g = plugin.settings().guidage();
        for (Map.Entry<UUID, Active> en : actives.entrySet()) {
            Active a = en.getValue();
            Result r = a.result;
            Player p = r == null || !a.onPath || a.near || a.place.isEmpty() ? null : Bukkit.getPlayer(en.getKey());
            if (p == null || !p.getWorld().getName().equals(r.world())) {
                continue;
            }
            if (ground) {
                Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(a.collection.trailColor()), g.trail().size());
                for (Lieu l : r.path().trail()) {
                    p.spawnParticle(Particle.DUST, l.x(), l.y(), l.z(), 1, 0, 0, 0, 0, dust);
                }
            }
            if (leap && leapSoon(r)) {
                leap(p, r.path().leap(), g.hint(), new Particle.DustOptions(
                        Color.fromRGB(g.hint().rgb() >= 0 ? g.hint().rgb() : a.collection.trailColor()), g.hint().size()));
            }
        }
    }

    /** Points du vol, et un cercle où se poser ; vus de loin (« force »), l'arrivée peut être à 40 blocs. */
    private static void leap(Player p, Trajet.Leap leap, Guidage.Hint h, Particle.DustOptions dust) {
        for (Lieu l : leap.line(h.gap(), h.max())) {
            p.spawnParticle(Particle.DUST, l.x(), l.y(), l.z(), 1, 0, 0, 0, 0, dust, true);
        }
        Lieu to = leap.to();
        for (int i = 0; h.ring() > 0 && i < 12; i++) {
            double a = i * Math.PI / 6;
            p.spawnParticle(Particle.DUST, to.x() + Math.cos(a) * h.ring(), to.y() + 0.15, to.z() + Math.sin(a) * h.ring(), 1, 0, 0, 0, 0, dust, true);
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
