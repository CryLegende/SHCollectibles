package org.Cry_Legende.sHCollectibles;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Clic droit sur un collectible, et protection des têtes. */
final class Recherche implements Listener {

    /** Collectible et sa collection. */
    record Found(Collection collection, Collection.Item item) {
    }

    private final SHCollectibles plugin;
    private final Effets effets;
    /** Collectibles par monde, puis par case. */
    private Map<String, Map<Long, Found>> places = Map.of();
    private final Set<String> writing = new HashSet<>();

    Recherche(SHCollectibles plugin, Effets effets) {
        this.plugin = plugin;
        this.effets = effets;
    }

    /** Refait l'index des cases à partir des réglages. */
    void index(Reglages settings) {
        Map<String, Map<Long, Found>> out = new HashMap<>();
        for (Collection c : settings.collections().values()) {
            for (Collection.Item item : c.items().values()) {
                Lieu l = item.place();
                out.computeIfAbsent(l.world(), k -> new HashMap<>())
                        .put(Block.getBlockKey((int) Math.floor(l.x()), (int) Math.floor(l.y()), (int) Math.floor(l.z())), new Found(c, item));
            }
        }
        places = out;
    }

    Found at(Block b) {
        Map<Long, Found> world = places.get(b.getWorld().getName());
        return world == null ? null : world.get(b.getBlockKey());
    }

    /** Collectibles du monde (vide s'il n'y en a pas). */
    Iterable<Found> in(String world) {
        return places.getOrDefault(world, Map.of()).values();
    }

    /** Un événement annulé compte aussi : les têtes sont dans des régions où l'interaction est refusée. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND || e.getClickedBlock() == null) {
            return;
        }
        Found f = at(e.getClickedBlock());
        if (f == null) {
            return;
        }
        Player p = e.getPlayer();
        Textes m = plugin.messages();
        Collection c = f.collection();
        Donnees d = plugin.joueurs().get(p.getUniqueId());
        if (!c.enabled()) {
            m.send(p, "fermee", "nom", c.coloredName());
        } else if (plugin.settings().refusedModes().contains(p.getGameMode())) {
            m.send(p, "mode-refuse");
        } else if (!allowed(p, c)) {
            m.send(p, "anciennete", "nom", c.coloredName());
        } else if (d == null) {
            m.send(p, "chargement");
        } else if (d.completed().contains(c.id())) {
            m.send(p, "deja-complete", "nom", c.coloredName());
        } else if (d.read(c.id()).contains(f.item().id())) {
            m.send(p, "deja-trouve", "nom", c.coloredName());
        } else {
            take(p, c, f.item());
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Found f = at(e.getBlock());
        if (f != null) {
            e.setCancelled(true);
            boolean admin = e.getPlayer().hasPermission("shcollectibles.admin.remove");
            plugin.messages().send(e.getPlayer(), admin ? "protege-admin" : "protege", "collection", f.collection().id(),
                    "numero", Integer.toString(f.item().id()));
        }
    }

    static boolean allowed(Player p, Collection c) {
        return c.permission().isEmpty() || p.isOp() || p.hasPermission(c.permission());
    }

    /** Enregistre la trouvaille, puis donne la récompense seulement si la base ne l'avait pas déjà. */
    void take(Player p, Collection c, Collection.Item item) {
        UUID id = p.getUniqueId();
        String key = id + "/" + c.id() + "/" + item.id();
        if (!writing.add(key)) {
            return;
        }
        plugin.joueurs().io("Trouvaille de " + p.getName() + " (" + c.id() + " " + item.id() + ")", () -> {
            boolean added = false;
            boolean saved = false;
            try {
                added = plugin.store().add(id, c.id(), item.id());
                saved = true;
            } finally {
                boolean isNew = added;
                boolean isSaved = saved;
                plugin.sync(() -> {
                    writing.remove(key);
                    if (isSaved) {
                        taken(p, c, item, isNew);
                    } else if (p.isOnline()) {
                        plugin.messages().send(p, "erreur");
                    }
                });
            }
        });
    }

    private void taken(Player p, Collection c, Collection.Item item, boolean isNew) {
        UUID id = p.getUniqueId();
        Donnees d = plugin.joueurs().get(id);
        if (!p.isOnline() || d == null) {
            if (isNew) {
                // Parti avant la récompense : la trouvaille est rendue, il pourra la refaire
                plugin.joueurs().io("Trouvaille rendue à " + p.getName(), () -> plugin.store().remove(id, c.id(), item.id()));
            }
            return;
        }
        d.finds(c.id()).add(item.id());
        if (!isNew) {
            plugin.messages().send(p, "deja-trouve", "nom", c.coloredName());
            return;
        }
        boolean last = c.hasAll(d.read(c.id()));
        if (last) {
            d.completed().add(c.id());
            plugin.joueurs().addCompletion(c.id());
            plugin.joueurs().io("Collection complète de " + p.getName(), () -> plugin.store().complete(id, c.id()));
        }
        effets.play(p, c, item, last ? c.complete() : c.actionsFor(item), c.shown(d.read(c.id()), last));
        plugin.detecteur().onFind(p);
    }
}
