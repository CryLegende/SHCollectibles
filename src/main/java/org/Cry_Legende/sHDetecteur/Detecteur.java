package org.Cry_Legende.sHDetecteur;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import ts.ajneb97.api.TheSearchAPI;
import ts.ajneb97.managers.SearchPlayerManager;
import ts.ajneb97.model.ObjectToFind;
import ts.ajneb97.model.Search;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Détecteurs actifs : choix du collectible, flèche montrée ou cachée, petit bruit. Tout se passe sur le fil principal. */
final class Detecteur implements Listener {

    /** Ticks entre deux passages. */
    static final int PERIOD = 10;

    /** Détecteur d'un joueur. « place » est lu par les placeholders depuis n'importe quel fil. */
    static final class Active {
        volatile Reglages.Collectible collection;
        volatile String place = "";
        int target = -1;
        double distance;
        boolean near;
        int announced = -1;
        long sinceNoise;

        Active(Reglages.Collectible collection) {
            this.collection = collection;
        }
    }

    private final SHDetecteur plugin;
    private final Map<UUID, Active> actives = new ConcurrentHashMap<>();

    Detecteur(SHDetecteur plugin) {
        this.plugin = plugin;
    }

    Active get(UUID id) {
        return actives.get(id);
    }

    /** Active le détecteur de la collection, ou l'arrête s'il l'était déjà. */
    void toggle(Player p, Reglages.Collectible c) {
        Active a = actives.get(p.getUniqueId());
        if (a != null && a.collection.id().equals(c.id())) {
            stop(p);
            return;
        }
        Textes m = plugin.messages();
        Search search = search(c);
        if (search == null) {
            m.send(p, "indisponible", "nom", c.coloredName());
            return;
        }
        if (!search.playerHasPermissionToInteract(p)) {
            m.send(p, "anciennete", "nom", c.coloredName());
            return;
        }
        if (complete(p, c)) {
            m.send(p, "complete", "nom", c.coloredName());
            return;
        }
        Active now = new Active(c);
        actives.put(p.getUniqueId(), now);
        m.send(p, "active", "nom", c.coloredName());
        noise(p, c, p.getLocation());
        p.closeInventory();
        if (left(p, search).isEmpty() && !plugin.settings().otherWorldCommand().isEmpty()) {
            p.performCommand(plugin.settings().otherWorldCommand());
        }
        update(p, now, search);
    }

    /** Arrête le détecteur du joueur ; faux s'il n'en avait pas. */
    boolean stop(Player p) {
        Active a = actives.remove(p.getUniqueId());
        if (a == null) {
            return false;
        }
        plugin.messages().send(p, "arrete", "nom", a.collection.coloredName());
        p.closeInventory();
        return true;
    }

    /** Après un reload : chaque détecteur reprend les réglages de sa collection, ou s'arrête si elle n'y est plus. */
    void onReload() {
        for (Map.Entry<UUID, Active> en : actives.entrySet()) {
            Reglages.Collectible c = plugin.settings().collections().get(en.getValue().collection.id());
            if (c == null) {
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
            Search search = search(a.collection);
            if (p == null || search == null || complete(p, a.collection)) {
                it.remove();
                continue;
            }
            update(p, a, search);
            a.sinceNoise += PERIOD;
            if (a.target >= 0 && a.distance <= a.collection.range() && a.sinceNoise >= plugin.settings().noiseTicks()) {
                a.sinceNoise = 0;
                ObjectToFind o = search.getObjectById(a.target);
                if (o != null) {
                    noise(p, a.collection, center(o));
                }
            }
        }
    }

    private void update(Player p, Active a, Search search) {
        Reglages cfg = plugin.settings();
        Location l = p.getLocation();
        Choix.Point t = Choix.target(left(p, search), a.target, l.getX(), l.getY(), l.getZ(), cfg.margin());
        if (t == null) {
            a.target = -1;
            a.near = false;
            a.place = "";
            return;
        }
        a.distance = t.distance(l.getX(), l.getY(), l.getZ());
        a.near = Choix.near(a.near && a.target == t.id(), a.distance, cfg.near(), cfg.far());
        a.target = t.id();
        a.place = a.near ? "" : l.getWorld().getName() + " " + t.x() + " " + t.y() + " " + t.z();
        if (a.near && a.announced != t.id()) {
            a.announced = t.id();
            String text = plugin.messages().text("barre-proche", "objet", a.collection.coloredObject());
            if (!text.isEmpty()) {
                p.sendActionBar(Textes.component(text));
            }
        }
    }

    /** Collectibles pas encore trouvés dans le monde du joueur. */
    private static List<Choix.Point> left(Player p, Search search) {
        SearchPlayerManager players = TheSearchAPI.getPlugin().getSearchPlayerManager();
        List<Choix.Point> out = new ArrayList<>();
        for (ObjectToFind o : search.getToFind()) {
            Location l = o.getLocation();
            if (l == null || l.getWorld() != p.getWorld() || players.playerHasFind(p, search.getName(), o.getId())) {
                continue;
            }
            Location c = center(o);
            out.add(new Choix.Point(o.getId(), c.getX(), c.getY(), c.getZ()));
        }
        return out;
    }

    private static Location center(ObjectToFind o) {
        return o.isBlock() ? o.getLocation().clone().add(0.5, 0.5, 0.5) : o.getLocation().clone();
    }

    private static void noise(Player p, Reglages.Collectible c, Location at) {
        if (!c.sound().isEmpty()) {
            p.playSound(at, c.sound(), SoundCategory.MASTER, c.volume(), c.pitch());
        }
    }

    /** Recherche TheSearch de la collection ; null si elle n'existe pas ou est désactivée. */
    private static Search search(Reglages.Collectible c) {
        Search s = TheSearchAPI.getPlugin().getSearchManager().getSearch(c.id());
        return s == null || !s.isEnabled() ? null : s;
    }

    static boolean complete(Player p, Reglages.Collectible c) {
        return TheSearchAPI.hasCompletedSearch(p, c.id());
    }
}
