package org.Cry_Legende.sHDetecteur;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Textes de messages.yml. Le fichier du jar sert de valeurs par défaut : une clé absente du fichier du serveur garde le
 * texte du jar, une valeur vide posée exprès reste muette.
 */
final class Textes {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&').hexCharacter('#').hexColors().build();

    private final YamlConfiguration file;

    Textes(YamlConfiguration file) {
        this.file = file;
    }

    /** Envoie chat.« key » ; un message vide n'est pas envoyé. */
    void send(CommandSender to, String key, String... replacements) {
        String text = text("chat." + key, replacements);
        if (!text.isEmpty()) {
            to.sendMessage(component(text));
        }
    }

    String text(String key, String... replacements) {
        String text = file.getString(key);
        if (text == null) {
            return "";
        }
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        return text;
    }

    static Component component(String text) {
        return LEGACY.deserialize(text);
    }
}
