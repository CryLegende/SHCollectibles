package org.Cry_Legende.sHCollectibles;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Trouvailles des joueurs connectés, lues avant leur entrée en jeu, et écritures en base dans l'ordre. Un joueur dont la
 * lecture a échoué n'a pas de données : il ne peut rien ramasser tant qu'elles ne sont pas là.
 */
final class Joueurs implements Listener {

    private record Early(Donnees data, long at) {
    }

    private final SHCollectibles plugin;
    private final Magasin store;
    private final Map<UUID, Donnees> online = new ConcurrentHashMap<>();
    private final Map<UUID, Early> early = new ConcurrentHashMap<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> new Thread(r, "SHCollectibles-SQL"));
    private volatile Map<String, Integer> completions = Map.of();

    Joueurs(SHCollectibles plugin, Magasin store) {
        this.plugin = plugin;
        this.store = store;
    }

    /** null si les trouvailles du joueur ne sont pas encore lues. */
    Donnees get(UUID id) {
        return online.get(id);
    }

    int completions(String collection) {
        return completions.getOrDefault(collection, 0);
    }

    /** Lecture ou écriture en base, hors du fil principal et dans l'ordre. */
    void io(String what, IoTask task) {
        io.execute(() -> {
            try {
                task.run();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, what + " impossible", e);
            }
        });
    }

    interface IoTask {
        void run() throws Exception;
    }

    void refreshCompletions() {
        io("Lecture des collections complètes", () -> completions = Map.copyOf(store.completions()));
    }

    /** Un joueur vient de compléter une collection. */
    void addCompletion(String collection) {
        Map<String, Integer> next = new HashMap<>(completions);
        next.merge(collection, 1, Integer::sum);
        completions = Map.copyOf(next);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        if (e.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        try {
            early.put(e.getUniqueId(), new Early(store.load(e.getUniqueId()), System.currentTimeMillis()));
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Lecture des trouvailles de " + e.getName() + " impossible", ex);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Early ready = early.remove(p.getUniqueId());
        if (ready != null) {
            online.put(p.getUniqueId(), ready.data());
        } else {
            load(p);
        }
        UUID id = p.getUniqueId();
        String name = p.getName();
        io("Enregistrement du pseudo de " + name, () -> store.saveName(id, name));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        online.remove(e.getPlayer().getUniqueId());
    }

    /** Relit les trouvailles d'un joueur connecté (démarrage à chaud, reprise, remise à zéro). */
    void load(Player p) {
        UUID id = p.getUniqueId();
        io("Lecture des trouvailles de " + p.getName(), () -> {
            Donnees d = store.load(id);
            plugin.sync(() -> {
                if (p.isOnline()) {
                    online.put(id, d);
                }
            });
        });
    }

    void loadAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            load(p);
        }
    }

    /** Connexions refusées après la lecture : oubliées au bout d'une minute. */
    void cleanEarly() {
        long limit = System.currentTimeMillis() - 60_000;
        early.values().removeIf(e -> e.at() < limit);
    }

    void close() {
        io.shutdown();
        try {
            if (!io.awaitTermination(10, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Écritures des trouvailles pas terminées après 10 s.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
