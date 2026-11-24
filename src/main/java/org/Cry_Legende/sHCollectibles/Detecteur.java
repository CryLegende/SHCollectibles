package org.Cry_Legende.sHCollectibles;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Détecteurs actifs : le collectible à chercher, l'étape vers laquelle aller (l'entrée de sa zone), la flèche montrée ou
 * cachée, le bruit. Tout se passe sur le fil principal.
 */
final class Detecteur implements Listener {

    /** Ticks entre deux passages. */
    static final int PERIOD = 10;
    /** Lumière du ciel en dessous de laquelle le joueur est à l'abri (zone fermée, maison, sous terre). */
    private static final int COVERED = 4;
    private static final double TELEPORT = 30;

    /** Détecteur d'un joueur. « place » et « name » sont lus par les placeholders depuis n'importe quel fil. */
    static final class Active {
        volatile Collection collection;
        volatile String place = "";
        volatile String name = "";
        int target = -1;
        int step;
        int steps;
        double distance;
        boolean near;
        int announced = -1;
        long sinceNoise;
        Location last;

        Active(Collection collection) {
            this.collection = collection;
        }
    }

    private final SHCollectibles plugin;
    private final Map<UUID, Active> actives = new ConcurrentHashMap<>();

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
        } else {
            Active now = new Active(c);
            actives.put(p.getUniqueId(), now);
            m.send(p, "detecteur-active", "nom", c.coloredName());
            Effets.sound(p, plugin.settings().noise(), p.getLocation());
            p.closeInventory();
            if (left(p, c, d, now).isEmpty() && !plugin.settings().otherWorldCommand().isEmpty()) {
                p.performCommand(plugin.settings().otherWorldCommand());
            }
            update(p, now, d);
        }
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

    /** Après une trouvaille : la cible suivante tout de suite, ou l'arrêt si la collection est complète. */
    void onFind(Player p) {
        Active a = actives.get(p.getUniqueId());
        Donnees d = plugin.joueurs().get(p.getUniqueId());
        if (a == null || d == null) {
            return;
        }
        if (d.completed().contains(a.collection.id())) {
            actives.remove(p.getUniqueId());
        } else {
            update(p, a, d);
        }
    }

    /** Après un reload : chaque détecteur reprend sa collection, ou s'arrête si elle n'y est plus. */
    void onReload() {
        for (Map.Entry<UUID, Active> en : actives.entrySet()) {
            Collection c = plugin.settings().collections().get(en.getValue().collection.id());
            if (c == null || !c.detector()) {
                actives.remove(en.getKey());
            } else {
                en.getValue().collection = c;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        actives.remove(e.getPlayer().getUniqueId());
    }

    /** Toutes les PERIOD ticks. */
    void tick() {
        Iterator<Map.Entry<UUID, Active>> it = actives.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Active> en = it.next();
            Player p = Bukkit.getPlayer(en.getKey());
            Active a = en.getValue();
            Donnees d = plugin.joueurs().get(en.getKey());
            if (p == null || !a.collection.enabled() || d != null && d.completed().contains(a.collection.id())) {
                it.remove();
                continue;
            }
            if (d == null) {
                continue;
            }
            update(p, a, d);
            noise(p, a);
        }
    }

    /** Le bruit n'est joué qu'une fois les étapes passées : à travers le sol, il tromperait sur l'entrée. */
    private void noise(Player p, Active a) {
        a.sinceNoise += PERIOD;
        if (a.target < 0 || a.step < a.steps || a.distance > plugin.settings().noiseRange() || a.sinceNoise < plugin.settings().noiseTicks()) {
            return;
        }
        Collection.Item item = a.collection.items().get(a.target);
        Location at = item == null ? null : item.center().location();
        if (at != null) {
            a.sinceNoise = 0;
            Effets.sound(p, plugin.settings().noise(), at);
        }
    }

    private void update(Player p, Active a, Donnees d) {
        Reglages cfg = plugin.settings();
        Location l = p.getLocation();
        if (a.last != null && (a.last.getWorld() != l.getWorld() || a.last.distanceSquared(l) > TELEPORT * TELEPORT)) {
            a.step = 0;
        }
        a.last = l;
        Choix.Candidate t = Choix.target(left(p, a.collection, d, a), a.target, cfg.margin());
        if (t == null) {
            a.target = -1;
            a.near = false;
            a.place = "";
            a.name = "";
            return;
        }
        Collection.Item item = t.item();
        boolean same = a.target == item.id();
        a.target = item.id();
        a.steps = item.steps().size();
        a.step = Choix.step(item, same ? a.step : 0, l.getX(), l.getY(), l.getZ(), covered(l), cfg.stepRadius());
        a.distance = item.center().distance(l.getX(), l.getY(), l.getZ());
        boolean arrived = a.step >= a.steps;
        a.near = arrived && Choix.near(a.near && same, a.distance, cfg.near(), cfg.far());
        Textes m = plugin.messages();
        if (a.near) {
            a.place = "";
            if (a.announced != item.id()) {
                a.announced = item.id();
                String text = m.text("barre-proche", "objet", a.collection.coloredObject());
                if (!text.isEmpty()) {
                    p.sendActionBar(Textes.component(text));
                }
            }
        } else {
            a.place = (arrived ? item.center() : item.steps().get(a.step)).text();
            a.name = arrived ? a.collection.coloredObject() : m.text("nom-entree", "objet", a.collection.coloredObject());
        }
    }

    private static boolean covered(Location l) {
        return l.getBlock().getLightFromSky() <= COVERED;
    }

    /** Collectibles pas encore trouvés dans le monde du joueur, avec la longueur de leur trajet. */
    private List<Choix.Candidate> left(Player p, Collection c, Donnees d, Active a) {
        Location l = p.getLocation();
        World w = l.getWorld();
        boolean covered = covered(l);
        List<Choix.Candidate> out = new ArrayList<>();
        for (Collection.Item item : c.items().values()) {
            Lieu at = item.place();
            if (!at.world().equals(w.getName()) || d.read(c.id()).contains(item.id()) || missing(w, at)) {
                continue;
            }
            int step = Choix.step(item, item.id() == a.target ? a.step : 0, l.getX(), l.getY(), l.getZ(), covered,
                    plugin.settings().stepRadius());
            out.add(new Choix.Candidate(item, Choix.length(item, step, l.getX(), l.getY(), l.getZ())));
        }
        return out;
    }

    /** Tête pas posée (case vide) : le détecteur n'y emmène personne. */
    private static boolean missing(World w, Lieu at) {
        int x = (int) Math.floor(at.x());
        int z = (int) Math.floor(at.z());
        return w.isChunkLoaded(x >> 4, z >> 4) && w.getBlockAt(x, (int) Math.floor(at.y()), z).isEmpty();
    }
}
