package org.Cry_Legende.sHDetecteur;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /detecteur [collection] et /shdetecteur <reload|info>. */
final class Commandes implements TabExecutor {

    private static final List<String> ADMIN = List.of("reload", "info");

    private final SHDetecteur plugin;

    Commandes(SHDetecteur plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equals("shdetecteur")) {
            admin(sender, args);
            return true;
        }
        Textes m = plugin.messages();
        if (!(sender instanceof Player p)) {
            m.send(sender, "joueurs-seulement");
            return true;
        }
        if (!p.hasPermission("shdetecteur.use")) {
            m.send(p, "permission");
            return true;
        }
        if (args.length == 0) {
            if (!plugin.detecteur().stop(p)) {
                m.send(p, "aucun");
            }
            return true;
        }
        Reglages.Collectible c = plugin.settings().find(args[0]);
        if (c == null) {
            m.send(p, "aucun");
            return true;
        }
        plugin.detecteur().toggle(p, c);
        return true;
    }

    private void admin(CommandSender sender, String[] args) {
        Textes m = plugin.messages();
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reload") && allowed(sender, sub)) {
            plugin.reload(sender);
        } else if (sub.equals("info") && args.length == 2 && allowed(sender, sub)) {
            info(sender, args[1]);
        } else {
            List<String> usage = new ArrayList<>();
            if (allowed(sender, "reload")) {
                usage.add("/shdetecteur reload");
            }
            if (allowed(sender, "info")) {
                usage.add("/shdetecteur info <joueur>");
            }
            if (usage.isEmpty()) {
                m.send(sender, "permission");
            } else {
                m.send(sender, "utilisation", "commandes", String.join(" ou ", usage));
            }
        }
    }

    private void info(CommandSender sender, String name) {
        Textes m = plugin.messages();
        Player p = Bukkit.getPlayerExact(name);
        if (p == null) {
            m.send(sender, "joueur-inconnu", "joueur", name);
            return;
        }
        Detecteur.Active a = plugin.detecteur().get(p.getUniqueId());
        if (a == null) {
            m.send(sender, "info-arrete", "joueur", p.getName());
            return;
        }
        m.send(sender, "info", "joueur", p.getName(), "nom", a.collection.coloredName(),
                "numero", a.target < 0 ? "-" : Integer.toString(a.target),
                "distance", a.target < 0 ? "-" : Long.toString(Math.round(a.distance)),
                "lieu", a.place.isEmpty() ? a.target < 0 ? "autre monde" : "tout près" : a.place);
    }

    private static boolean allowed(CommandSender sender, String sub) {
        return sender.hasPermission("shdetecteur.admin." + sub);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (command.getName().equals("shdetecteur")) {
            if (args.length == 1) {
                for (String s : ADMIN) {
                    if (allowed(sender, s)) {
                        add(out, s, args[0]);
                    }
                }
            } else if (args.length == 2 && args[0].equalsIgnoreCase("info") && allowed(sender, "info")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    add(out, p.getName(), args[1]);
                }
            }
        } else if (args.length == 1 && sender.hasPermission("shdetecteur.use")) {
            for (String id : plugin.settings().collections().keySet()) {
                add(out, id, args[0]);
            }
        }
        return out;
    }

    private static void add(List<String> out, String value, String typed) {
        if (value.toLowerCase(Locale.ROOT).startsWith(typed.toLowerCase(Locale.ROOT))) {
            out.add(value);
        }
    }
}
