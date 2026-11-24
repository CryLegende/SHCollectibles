package org.Cry_Legende.sHCollectibles;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Collections de têtes à trouver : trouvailles, récompenses, particules et détecteur vers la prochaine. */
public final class SHCollectibles extends JavaPlugin {

    private volatile Reglages settings;
    private volatile Textes messages;
    private HikariDataSource pool;
    private Magasin store;
    private Joueurs joueurs;
    private Effets effets;
    private Recherche recherche;
    private Detecteur detecteur;
    private Particules particules;
    private Cartes cartes;
    private final List<BukkitTask> periodic = new ArrayList<>();
    private final List<Expansion> expansions = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), "messages.yml").exists()) {
            saveResource("messages.yml", false);
        }
        if (!new File(getDataFolder(), "zones.yml").exists()) {
            saveResource("zones.yml", false);
        }
        if (!collectionsFolder().exists()) {
            saveResource("collections/exemple.yml", false);
        }
        try {
            settings = readSettings();
            messages = new Textes(loadMessages());
        } catch (RuntimeException e) {
            getLogger().severe("Configuration invalide, SHCollectibles s'arrête : " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        Reglages.Db db = settings.db();
        HikariConfig hc = new HikariConfig();
        hc.setPoolName("SHCollectibles");
        hc.setDriverClassName("org.mariadb.jdbc.Driver");
        hc.setJdbcUrl("jdbc:mariadb://" + db.host() + ":" + db.port() + "/" + db.database());
        hc.setUsername(db.user());
        hc.setPassword(db.password());
        hc.setMaximumPoolSize(db.connections());
        hc.setMinimumIdle(1);
        hc.setConnectionTimeout(5000);
        try {
            pool = new HikariDataSource(hc);
            store = new Magasin(pool, settings.server());
            store.createTables();
        } catch (Exception e) {
            getLogger().severe("Base de données inaccessible, SHCollectibles s'arrête : " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        joueurs = new Joueurs(this, store);
        effets = new Effets(this);
        recherche = new Recherche(this, effets);
        recherche.index(settings);
        cartes = new Cartes(this);
        detecteur = new Detecteur(this);
        particules = new Particules(this);
        particules.index(settings);
        for (var listener : List.of(joueurs, effets, recherche, detecteur)) {
            getServer().getPluginManager().registerEvents(listener, this);
        }
        Commandes commands = new Commandes(this);
        for (String name : List.of("detecteur", "shcollectibles")) {
            PluginCommand command = getCommand(name);
            command.setExecutor(commands);
            command.setTabCompleter(commands);
        }
        expansions.add(new Expansion(this, "shcollectibles"));
        if (!settings.alias().isEmpty() && !settings.alias().equalsIgnoreCase("shcollectibles")) {
            expansions.add(new Expansion(this, settings.alias()));
        }
        expansions.forEach(Expansion::register);
        joueurs.refreshCompletions();
        joueurs.loadAll();
        LocalDate today = today();
        joueurs.io("Nettoyage des détecteurs passés", () -> store.purgeTracked(today));
        Bukkit.getScheduler().runTaskTimer(this, joueurs::cleanEarly, 1200L, 1200L);
        Bukkit.getScheduler().runTaskLater(this, () -> cartes.refresh(false), 20L * settings.guidage().startDelaySeconds());
        startPeriodic();
        cartes.everyTick(particules::tick);
        cartes.everyTick(detecteur::landings);
    }

    @Override
    public void onDisable() {
        expansions.forEach(Expansion::unregister);
        if (detecteur != null) {
            detecteur.close();
        }
        if (cartes != null) {
            cartes.close();
        }
        if (effets != null) {
            effets.clear();
        }
        if (joueurs != null) {
            joueurs.close();
        }
        if (pool != null) {
            pool.close();
        }
    }

    /** Passages réguliers dont la fréquence est dans config.yml. */
    private void startPeriodic() {
        periodic.forEach(BukkitTask::cancel);
        periodic.clear();
        periodic.add(Bukkit.getScheduler().runTaskTimer(this, detecteur::tick, 5L, 1L));
        periodic.add(Bukkit.getScheduler().runTaskTimer(this, detecteur::checkHeads, 1L, 20L * settings.headCheckSeconds()));
        periodic.add(Bukkit.getScheduler().runTaskTimer(this, detecteur::trail, 7L, 1L));
    }

    /** Recharge tout ; un fichier invalide ne change rien et le message le nomme. Base et serveur : au redémarrage. */
    boolean reload(CommandSender sender) {
        Reglages s;
        Textes m;
        try {
            s = readSettings();
            m = new Textes(loadMessages());
        } catch (RuntimeException e) {
            messages.send(sender, "reload-erreur", "erreur", String.valueOf(e.getMessage()));
            return false;
        }
        apply(s, m);
        messages.send(sender, "reload-ok", "n", Integer.toString(s.collections().size()));
        return true;
    }

    private void apply(Reglages s, Textes m) {
        settings = s;
        messages = m;
        recherche.index(s);
        particules.index(s);
        detecteur.onReload();
        cartes.refresh(false);
        startPeriodic();
    }

    /** Modifie le fichier d'une collection (commentaires gardés) et l'applique aussitôt. */
    void edit(CommandSender sender, String id, Consumer<YamlConfiguration> change) {
        File f = new File(collectionsFolder(), id + ".yml");
        try {
            YamlConfiguration y = load(f);
            change.accept(y);
            y.options().width(4096);
            y.save(f);
            apply(readSettings(), messages);
        } catch (Exception e) {
            messages.send(sender, "reload-erreur", "erreur", String.valueOf(e.getMessage()));
        }
    }

    private Reglages readSettings() {
        YamlConfiguration config = load(new File(getDataFolder(), "config.yml"));
        String permission = Reglages.defaultPermission(config);
        Map<String, Collection> collections = new LinkedHashMap<>();
        File[] files = collectionsFolder().listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            Arrays.sort(files);
            for (File f : files) {
                String id = f.getName().substring(0, f.getName().length() - 4);
                try {
                    collections.put(id, Collection.read(id, load(f), permission));
                } catch (RuntimeException e) {
                    throw new IllegalArgumentException("collections/" + f.getName() + ", " + e.getMessage());
                }
            }
        }
        Zones zones;
        try {
            zones = Zones.read(load(new File(getDataFolder(), "zones.yml")), collections.values());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("zones.yml, " + e.getMessage());
        }
        try {
            return Reglages.read(config, collections, zones);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("config.yml, " + e.getMessage());
        }
    }

    private static YamlConfiguration load(File f) {
        YamlConfiguration file = new YamlConfiguration();
        try {
            file.load(f);
        } catch (Exception e) {
            throw new IllegalArgumentException(f.getName() + " illisible, " + e.getMessage());
        }
        return file;
    }

    private YamlConfiguration loadMessages() {
        YamlConfiguration file = load(new File(getDataFolder(), "messages.yml"));
        InputStream defaults = getResource("messages.yml");
        if (defaults != null) {
            file.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defaults, StandardCharsets.UTF_8)));
        }
        return file;
    }

    private File collectionsFolder() {
        return new File(getDataFolder(), "collections");
    }

    /** Jour du détecteur : il change à l'heure de « detecteur.limite.heure ». */
    LocalDate today() {
        return settings.limit().day(LocalDateTime.now());
    }

    void sync(Runnable r) {
        if (isEnabled()) {
            Bukkit.getScheduler().runTask(this, r);
        }
    }

    Reglages settings() {
        return settings;
    }

    Textes messages() {
        return messages;
    }

    Magasin store() {
        return store;
    }

    Joueurs joueurs() {
        return joueurs;
    }

    Recherche recherche() {
        return recherche;
    }

    Detecteur detecteur() {
        return detecteur;
    }

    Cartes cartes() {
        return cartes;
    }
}
