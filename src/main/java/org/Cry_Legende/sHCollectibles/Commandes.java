package org.Cry_Legende.sHCollectibles;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** /detecteur [collection] et /shcollectibles. */
final class Commandes implements TabExecutor {

    private static final Map<String, String> ADMIN = new LinkedHashMap<>();

    static {
        ADMIN.put("reload", "");
        ADMIN.put("list", "[collection]");
        ADMIN.put("info", "<joueur>");
        ADMIN.put("add", "<collection>");
        ADMIN.put("remove", "");
        ADMIN.put("step", "<collection> <numéro> <add|clear>");
        ADMIN.put("tp", "<collection> <numéro>");
        ADMIN.put("give", "<joueur> <collection> <numéro>");
        ADMIN.put("reset", "<joueur|*> <collection|*>");
        ADMIN.put("enable", "<collection>");
        ADMIN.put("disable", "<collection>");
        ADMIN.put("import", "<dossier players>");
    }

    private final SHCollectibles plugin;

    Commandes(SHCollectibles plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equals("detecteur")) {
            detector(sender, args);
        } else {
            admin(sender, args);
        }
        return true;
    }

    private void detector(CommandSender sender, String[] args) {
        Textes m = plugin.messages();
        if (!(sender instanceof Player p)) {
            m.send(sender, "joueurs-seulement");
        } else if (!p.hasPermission("shcollectibles.detecteur")) {
            m.send(p, "permission");
        } else if (args.length == 0) {
            if (!plugin.detecteur().stop(p)) {
                m.send(p, "detecteur-aucun");
            }
        } else {
            Collection c = plugin.settings().find(args[0]);
            if (c == null || !c.detector()) {
                m.send(p, "detecteur-aucun");
            } else {
                plugin.detecteur().toggle(p, c);
            }
        }
    }

    private void admin(CommandSender sender, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (!ADMIN.containsKey(sub) || !allowed(sender, sub) || !run(sender, sub, args)) {
            usage(sender, sub);
        }
    }

    /** Faux si les arguments ne vont pas. */
    private boolean run(CommandSender sender, String sub, String[] args) {
        return switch (sub) {
            case "reload" -> {
                plugin.reload(sender);
                yield true;
            }
            case "list" -> list(sender, args);
            case "info" -> args.length == 2 && info(sender, args[1]);
            case "add" -> args.length == 2 && add(sender, args[1]);
            case "remove" -> remove(sender);
            case "step" -> args.length == 4 && step(sender, args[1], args[2], args[3]);
            case "tp" -> args.length == 3 && tp(sender, args[1], args[2]);
            case "give" -> args.length == 4 && give(sender, args[1], args[2], args[3]);
            case "reset" -> args.length == 3 && reset(sender, args[1], args[2]);
            case "enable", "disable" -> args.length == 2 && open(sender, args[1], sub.equals("enable"));
            case "import" -> args.length == 2 && importPlayers(sender, args[1]);
            default -> false;
        };
    }

    private void usage(CommandSender sender, String sub) {
        Textes m = plugin.messages();
        List<String> usage = new ArrayList<>();
        for (Map.Entry<String, String> en : ADMIN.entrySet()) {
            if (allowed(sender, en.getKey()) && (sub.isEmpty() || !ADMIN.containsKey(sub) || sub.equals(en.getKey()))) {
                usage.add("/shcollectibles " + (en.getKey() + " " + en.getValue()).trim());
            }
        }
        if (usage.isEmpty()) {
            m.send(sender, "permission");
        } else {
            m.send(sender, "utilisation", "commandes", usage.size() == 1 ? usage.get(0)
                    : "/shcollectibles <" + String.join("|", usage.stream().map(u -> u.split(" ")[1]).toList()) + ">");
        }
    }

    private Collection collection(CommandSender sender, String id) {
        Collection c = plugin.settings().find(id);
        if (c == null) {
            plugin.messages().send(sender, "collection-inconnue", "collection", id);
        }
        return c;
    }

    private Collection.Item item(CommandSender sender, Collection c, String number) {
        Collection.Item item = null;
        try {
            item = c.items().get(Integer.parseInt(number));
        } catch (NumberFormatException ignored) {
            // message ci-dessous
        }
        if (item == null) {
            plugin.messages().send(sender, "numero-inconnu", "collection", c.id(), "numero", number);
        }
        return item;
    }

    private boolean list(CommandSender sender, String[] args) {
        Textes m = plugin.messages();
        if (args.length == 1) {
            for (Collection c : plugin.settings().collections().values()) {
                m.send(sender, "liste", "collection", c.id(), "nom", c.coloredName(), "total", Integer.toString(c.total()),
                        "complete", Integer.toString(plugin.joueurs().completions(c.id())), "etat", c.enabled() ? "ouverte" : "fermée");
            }
            return true;
        }
        Collection c = collection(sender, args[1]);
        if (c != null) {
            for (Collection.Item i : c.items().values()) {
                m.send(sender, "liste-collectible", "numero", Integer.toString(i.id()), "lieu", i.place().text(),
                        "etapes", Integer.toString(i.steps().size()));
            }
        }
        return true;
    }

    private boolean info(CommandSender sender, String name) {
        Textes m = plugin.messages();
        Player p = Bukkit.getPlayerExact(name);
        Donnees d = p == null ? null : plugin.joueurs().get(p.getUniqueId());
        if (d == null) {
            m.send(sender, "joueur-inconnu", "joueur", name);
            return true;
        }
        for (Collection c : plugin.settings().collections().values()) {
            m.send(sender, "info", "joueur", p.getName(), "nom", c.coloredName(),
                    "trouves", Integer.toString(c.shown(d.read(c.id()), d.completed().contains(c.id()))),
                    "total", Integer.toString(c.total()), "numeros", d.read(c.id()).stream().sorted().toList().toString());
        }
        Detecteur.Active a = plugin.detecteur().get(p.getUniqueId());
        if (a == null) {
            m.send(sender, "info-detecteur-arrete", "joueur", p.getName());
        } else {
            m.send(sender, "info-detecteur", "joueur", p.getName(), "nom", a.collection.coloredName(),
                    "numero", a.target < 0 ? "-" : Integer.toString(a.target),
                    "distance", a.target < 0 ? "-" : Long.toString(Math.round(a.distance)),
                    "etape", a.target < 0 ? "-" : a.step + "/" + a.steps,
                    "lieu", a.place.isEmpty() ? a.target < 0 ? "autre monde" : "tout près" : a.place);
        }
        return true;
    }

    private boolean add(CommandSender sender, String id) {
        Textes m = plugin.messages();
        if (!(sender instanceof Player p)) {
            m.send(sender, "joueurs-seulement");
            return true;
        }
        Block b = p.getTargetBlockExact(6);
        Collection c = collection(sender, id);
        if (c == null) {
            return true;
        }
        if (b == null || b.isEmpty()) {
            m.send(p, "viser");
            return true;
        }
        Recherche.Found already = plugin.recherche().at(b);
        if (already != null) {
            m.send(p, "deja-collectible", "collection", already.collection().id(), "numero", Integer.toString(already.item().id()));
            return true;
        }
        int n = c.items().keySet().stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
        String place = b.getWorld().getName() + " " + b.getX() + " " + b.getY() + " " + b.getZ();
        plugin.edit(sender, c.id(), y -> y.set("collectibles." + n + ".lieu", place));
        m.send(p, "ajoute", "collection", c.id(), "numero", Integer.toString(n));
        return true;
    }

    private boolean remove(CommandSender sender) {
        Textes m = plugin.messages();
        if (!(sender instanceof Player p)) {
            m.send(sender, "joueurs-seulement");
            return true;
        }
        Block b = p.getTargetBlockExact(6);
        Recherche.Found f = b == null ? null : plugin.recherche().at(b);
        if (f == null) {
            m.send(p, "viser");
            return true;
        }
        plugin.edit(sender, f.collection().id(), y -> y.set("collectibles." + f.item().id(), null));
        m.send(p, "retire", "collection", f.collection().id(), "numero", Integer.toString(f.item().id()));
        return true;
    }

    private boolean step(CommandSender sender, String id, String number, String what) {
        Textes m = plugin.messages();
        if (!(sender instanceof Player p)) {
            m.send(sender, "joueurs-seulement");
            return true;
        }
        Collection c = collection(sender, id);
        Collection.Item item = c == null ? null : item(sender, c, number);
        if (item == null) {
            return true;
        }
        String path = "collectibles." + item.id() + ".etapes";
        if (what.equalsIgnoreCase("clear")) {
            plugin.edit(sender, c.id(), y -> y.set(path, null));
            m.send(p, "etapes-effacees", "collection", c.id(), "numero", number);
            return true;
        }
        if (!what.equalsIgnoreCase("add")) {
            return false;
        }
        Location l = p.getLocation();
        if (!l.getWorld().getName().equals(item.place().world())) {
            m.send(p, "autre-monde", "monde", item.place().world());
            return true;
        }
        plugin.edit(sender, c.id(), y -> {
            List<String> steps = new ArrayList<>(y.getStringList(path));
            steps.add(l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ());
            y.set(path, steps);
        });
        m.send(p, "etape-ajoutee", "collection", c.id(), "numero", number, "n", Integer.toString(item.steps().size() + 1));
        return true;
    }

    private boolean tp(CommandSender sender, String id, String number) {
        if (!(sender instanceof Player p)) {
            plugin.messages().send(sender, "joueurs-seulement");
            return true;
        }
        Collection c = collection(sender, id);
        Collection.Item item = c == null ? null : item(sender, c, number);
        Location l = item == null ? null : item.center().location();
        if (l != null) {
            p.teleport(l.add(0, 0.5, 0));
        }
        return true;
    }

    private boolean give(CommandSender sender, String name, String id, String number) {
        Player p = Bukkit.getPlayerExact(name);
        if (p == null || plugin.joueurs().get(p.getUniqueId()) == null) {
            plugin.messages().send(sender, "joueur-inconnu", "joueur", name);
            return true;
        }
        Collection c = collection(sender, id);
        Collection.Item item = c == null ? null : item(sender, c, number);
        if (item != null) {
            plugin.recherche().take(p, c, item);
            plugin.messages().send(sender, "donne", "joueur", p.getName(), "collection", c.id(), "numero", number);
        }
        return true;
    }

    private boolean reset(CommandSender sender, String name, String id) {
        Collection c = id.equals("*") ? null : collection(sender, id);
        if (!id.equals("*") && c == null) {
            return true;
        }
        plugin.joueurs().io("Remise à zéro", () -> {
            UUID who = null;
            if (!name.equals("*")) {
                Player p = Bukkit.getPlayerExact(name);
                who = p != null ? p.getUniqueId() : plugin.store().find(name);
                if (who == null) {
                    plugin.sync(() -> plugin.messages().send(sender, "joueur-inconnu", "joueur", name));
                    return;
                }
            }
            int n = plugin.store().reset(who, c == null ? null : c.id());
            plugin.sync(() -> {
                plugin.joueurs().loadAll();
                plugin.joueurs().refreshCompletions();
                plugin.messages().send(sender, "remis-a-zero", "joueur", name, "collection", id, "n", Integer.toString(n));
            });
        });
        return true;
    }

    private boolean open(CommandSender sender, String id, boolean enabled) {
        Collection c = collection(sender, id);
        if (c != null) {
            plugin.edit(sender, c.id(), y -> y.set("actif", enabled));
            plugin.messages().send(sender, enabled ? "ouverte" : "refermee", "collection", c.id());
        }
        return true;
    }

    private boolean importPlayers(CommandSender sender, String path) {
        if (!(sender instanceof ConsoleCommandSender)) {
            plugin.messages().send(sender, "console-seulement");
            return true;
        }
        plugin.joueurs().io("Reprise de " + path, () -> {
            ImportTheSearch.Result r = ImportTheSearch.read(new File(path));
            plugin.store().importAll(r.players());
            int finds = plugin.store().finds().values().stream().mapToInt(Integer::intValue).sum();
            int done = plugin.store().completions().values().stream().mapToInt(Integer::intValue).sum();
            plugin.sync(() -> {
                plugin.joueurs().loadAll();
                plugin.joueurs().refreshCompletions();
                plugin.messages().send(sender, "reprise", "joueurs", Integer.toString(r.players().size()),
                        "trouvailles", Integer.toString(r.finds()), "completes", Integer.toString(r.completed()),
                        "base-trouvailles", Integer.toString(finds), "base-completes", Integer.toString(done));
            });
        });
        return true;
    }

    private static boolean allowed(CommandSender sender, String sub) {
        return sender.hasPermission("shcollectibles.admin." + sub);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (command.getName().equals("detecteur")) {
            if (args.length == 1 && sender.hasPermission("shcollectibles.detecteur")) {
                for (Collection c : plugin.settings().collections().values()) {
                    if (c.detector() && c.enabled()) {
                        add(out, c.id(), args[0]);
                    }
                }
            }
            return out;
        }
        if (args.length == 1) {
            for (String s : ADMIN.keySet()) {
                if (allowed(sender, s)) {
                    add(out, s, args[0]);
                }
            }
            return out;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!ADMIN.containsKey(sub) || !allowed(sender, sub)) {
            return out;
        }
        boolean playerFirst = sub.equals("info") || sub.equals("give") || sub.equals("reset");
        if (args.length == 2 && playerFirst) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                add(out, p.getName(), args[1]);
            }
        } else if (args.length == (playerFirst ? 3 : 2) && !sub.equals("import") && !sub.equals("info") && !sub.equals("reload")
                && !sub.equals("remove")) {
            for (String id : plugin.settings().collections().keySet()) {
                add(out, id, args[args.length - 1]);
            }
        } else if (args.length == 4 && sub.equals("step")) {
            add(out, "add", args[3]);
            add(out, "clear", args[3]);
        } else if (args.length == 3 && (sub.equals("step") || sub.equals("tp")) || args.length == 4 && sub.equals("give")) {
            Collection c = plugin.settings().find(args[args.length - 2]);
            if (c != null) {
                for (int n : c.items().keySet()) {
                    add(out, Integer.toString(n), args[args.length - 1]);
                }
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
