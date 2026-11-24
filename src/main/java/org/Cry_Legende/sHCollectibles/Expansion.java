package org.Cry_Legende.sHCollectibles;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.util.Set;

/** Placeholders, sous le nom du plugin et sous l'alias de config.yml (reprise d'un autre plugin). */
final class Expansion extends PlaceholderExpansion {

    private final SHCollectibles plugin;
    private final String identifier;

    Expansion(SHCollectibles plugin, String identifier) {
        this.plugin = plugin;
        this.identifier = identifier;
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public String getAuthor() {
        return "Cry_Legende";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player p, String params) {
        Reglages s = plugin.settings();
        if (params.startsWith("total_players_with_completed_")) {
            Collection c = s.find(params.substring("total_players_with_completed_".length()));
            return c == null ? "0" : Integer.toString(plugin.joueurs().completions(c.id()));
        }
        if (params.startsWith("total_objects_")) {
            Collection c = s.find(params.substring("total_objects_".length()));
            return c == null ? "0" : Integer.toString(c.total());
        }
        if (params.startsWith("is_enabled_")) {
            Collection c = s.find(params.substring("is_enabled_".length()));
            return Boolean.toString(c != null && c.enabled());
        }
        if (params.startsWith("zone_total_")) {
            return Integer.toString(s.zones().total(params.substring("zone_total_".length())));
        }
        if (p == null) {
            return "";
        }
        Donnees d = plugin.joueurs().get(p.getUniqueId());
        if (params.equals("total_completed_searches")) {
            return Integer.toString(d == null ? 0 : d.completed().size());
        }
        if (params.startsWith("total_found_objects_")) {
            Collection c = s.find(params.substring("total_found_objects_".length()));
            return Integer.toString(c == null ? 0 : shown(c, d));
        }
        if (params.startsWith("remaining_objects_")) {
            Collection c = s.find(params.substring("remaining_objects_".length()));
            return Integer.toString(c == null ? 0 : c.total() - shown(c, d));
        }
        if (params.startsWith("has_completed_")) {
            Collection c = s.find(params.substring("has_completed_".length()));
            return Boolean.toString(c != null && d != null && d.completed().contains(c.id()));
        }
        if (params.startsWith("has_found_object_")) {
            String rest = params.substring("has_found_object_".length());
            int cut = rest.lastIndexOf('_');
            Collection c = cut < 0 ? null : s.find(rest.substring(0, cut));
            return Boolean.toString(c != null && d != null && d.read(c.id()).contains(number(rest.substring(cut + 1))));
        }
        if (params.startsWith("zone_trouves_")) {
            return Integer.toString(s.zones().found(params.substring("zone_trouves_".length()), d));
        }
        if (params.startsWith("zone_")) {
            return zone(params.substring("zone_".length()), d);
        }
        Detecteur.Active a = plugin.detecteur().get(p.getUniqueId());
        if (params.startsWith("menu_")) {
            return menu(p, d, a, s.find(params.substring("menu_".length())));
        }
        return switch (params) {
            case "cible" -> a == null ? "" : a.place;
            case "fleche" -> a == null ? "" : a.text;
            case "collection" -> a == null ? "" : a.collection.id();
            default -> null;
        };
    }

    /** Ligne d'une zone (codex) ; vide s'il n'y a rien à y trouver. */
    private String zone(String id, Donnees d) {
        Zones z = plugin.settings().zones();
        int total = z.total(id);
        if (total == 0) {
            return "";
        }
        int found = z.found(id, d);
        return plugin.messages().text(found >= total ? "zone.complete" : "zone.ligne",
                "trouves", Integer.toString(found), "total", Integer.toString(total));
    }

    private static int shown(Collection c, Donnees d) {
        return d == null ? 0 : c.shown(d.read(c.id()), d.completed().contains(c.id()));
    }

    private static int number(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String menu(Player p, Donnees d, Detecteur.Active a, Collection c) {
        if (c == null || !c.detector()) {
            return "";
        }
        Set<String> done = d == null ? Set.of() : d.completed();
        Limite limit = plugin.settings().limit();
        String key;
        if (done.contains(c.id())) {
            key = "menu.complete";
        } else if (a != null && a.collection.id().equals(c.id())) {
            key = "menu.arreter";
        } else if (d == null || !plugin.detecteur().limited(p) || d.tracked(plugin.today()).contains(c.id())) {
            key = "menu.activer";
        } else {
            key = plugin.detecteur().blocked(p, d, c) ? "menu.limite" : "menu.activer-limite";
        }
        long left = limit.minutesLeft(LocalDateTime.now());
        return plugin.messages().text(key, "couleur", c.color(), "nom", c.coloredName(), "objet", c.coloredObject(),
                "n", Integer.toString(limit.perDay()), "heures", Long.toString(left / 60), "minutes", Long.toString(left % 60));
    }
}
