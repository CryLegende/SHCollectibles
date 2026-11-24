package org.Cry_Legende.sHDetecteur;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Détecteur de collectibles TheSearch : flèche vers le prochain, petit bruit quand il est proche. */
public final class SHDetecteur extends JavaPlugin {

    private volatile Reglages settings;
    private volatile Textes messages;
    private Detecteur detecteur;
    private Expansion expansion;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), "messages.yml").exists()) {
            saveResource("messages.yml", false);
        }
        try {
            settings = Reglages.read(load("config.yml"));
            messages = new Textes(loadMessages());
        } catch (RuntimeException e) {
            getLogger().severe("config.yml ou messages.yml invalide, SHDetecteur s'arrête : " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        detecteur = new Detecteur(this);
        getServer().getPluginManager().registerEvents(detecteur, this);
        Commandes commands = new Commandes(this);
        for (String name : List.of("detecteur", "shdetecteur")) {
            PluginCommand command = getCommand(name);
            command.setExecutor(commands);
            command.setTabCompleter(commands);
        }
        expansion = new Expansion(this);
        expansion.register();
        Bukkit.getScheduler().runTaskTimer(this, detecteur::tick, 20L, Detecteur.PERIOD);
    }

    @Override
    public void onDisable() {
        if (expansion != null) {
            expansion.unregister();
        }
    }

    /** Recharge config.yml et messages.yml ; un fichier invalide ne change rien et le message le nomme. */
    void reload(CommandSender sender) {
        Reglages s;
        Textes m;
        try {
            s = Reglages.read(load("config.yml"));
        } catch (RuntimeException e) {
            messages.send(sender, "reload-erreur", "fichier", "config.yml", "erreur", String.valueOf(e.getMessage()));
            return;
        }
        try {
            m = new Textes(loadMessages());
        } catch (RuntimeException e) {
            messages.send(sender, "reload-erreur", "fichier", "messages.yml", "erreur", String.valueOf(e.getMessage()));
            return;
        }
        settings = s;
        messages = m;
        detecteur.onReload();
        messages.send(sender, "reload-ok");
    }

    private YamlConfiguration load(String name) {
        YamlConfiguration file = new YamlConfiguration();
        try {
            file.load(new File(getDataFolder(), name));
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage());
        }
        return file;
    }

    private YamlConfiguration loadMessages() {
        YamlConfiguration file = load("messages.yml");
        InputStream defaults = getResource("messages.yml");
        if (defaults != null) {
            file.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defaults, StandardCharsets.UTF_8)));
        }
        return file;
    }

    Reglages settings() {
        return settings;
    }

    Textes messages() {
        return messages;
    }

    Detecteur detecteur() {
        return detecteur;
    }
}
