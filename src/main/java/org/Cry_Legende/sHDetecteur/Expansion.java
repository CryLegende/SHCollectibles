package org.Cry_Legende.sHDetecteur;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

/** %shdetecteur_cible%, _nom%, _collection% et _menu_<collection>%. */
final class Expansion extends PlaceholderExpansion {

    private final SHDetecteur plugin;

    Expansion(SHDetecteur plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "shdetecteur";
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
        if (p == null) {
            return "";
        }
        Detecteur.Active a = plugin.detecteur().get(p.getUniqueId());
        if (params.startsWith("menu_")) {
            return menu(p, a, params.substring("menu_".length()));
        }
        return switch (params) {
            case "cible" -> a == null ? "" : a.place;
            case "nom" -> a == null ? "" : a.collection.coloredObject();
            case "collection" -> a == null ? "" : a.collection.id();
            default -> null;
        };
    }

    private String menu(Player p, Detecteur.Active a, String id) {
        Reglages.Collectible c = plugin.settings().find(id);
        if (c == null) {
            return "";
        }
        String key = Detecteur.complete(p, c) ? "menu.complete"
                : a != null && a.collection.id().equals(c.id()) ? "menu.arreter" : "menu.activer";
        return plugin.messages().text(key, "couleur", c.color(), "nom", c.coloredName(), "objet", c.coloredObject());
    }
}
