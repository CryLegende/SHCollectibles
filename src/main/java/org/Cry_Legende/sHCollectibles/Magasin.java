package org.Cry_Legende.sHCollectibles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Trouvailles en MariaDB, une ligne par collectible trouvé. Plusieurs serveurs peuvent partager la base, chacun sous son
 * nom. Méthodes bloquantes, hors du fil principal.
 */
final class Magasin {

    private final DataSource source;
    private final String server;

    Magasin(DataSource source, String server) {
        this.source = source;
        this.server = server;
    }

    void createTables() throws SQLException {
        try (Connection c = source.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS shcollectibles_trouvailles ("
                    + "serveur VARCHAR(16) NOT NULL, uuid CHAR(36) NOT NULL, collection VARCHAR(48) NOT NULL, numero INT NOT NULL, "
                    + "trouve_le TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "PRIMARY KEY (serveur, uuid, collection, numero), KEY uuid (uuid)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin");
            st.execute("CREATE TABLE IF NOT EXISTS shcollectibles_completes ("
                    + "serveur VARCHAR(16) NOT NULL, uuid CHAR(36) NOT NULL, collection VARCHAR(48) NOT NULL, "
                    + "complete_le TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "PRIMARY KEY (serveur, uuid, collection), KEY uuid (uuid)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin");
            st.execute("CREATE TABLE IF NOT EXISTS shcollectibles_suivies ("
                    + "serveur VARCHAR(16) NOT NULL, uuid CHAR(36) NOT NULL, jour DATE NOT NULL, collection VARCHAR(48) NOT NULL, "
                    + "PRIMARY KEY (serveur, uuid, jour, collection), KEY uuid (uuid)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin");
            st.execute("CREATE TABLE IF NOT EXISTS shcollectibles_joueurs ("
                    + "uuid CHAR(36) NOT NULL PRIMARY KEY, nom VARCHAR(16) NOT NULL, KEY nom (nom)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
    }

    Donnees load(UUID id, LocalDate day) throws SQLException {
        Donnees d = new Donnees();
        try (Connection c = source.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT collection, numero FROM shcollectibles_trouvailles WHERE serveur = ? AND uuid = ?")) {
                ps.setString(1, server);
                ps.setString(2, id.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        d.finds(rs.getString(1)).add(rs.getInt(2));
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT collection FROM shcollectibles_completes WHERE serveur = ? AND uuid = ?")) {
                ps.setString(1, server);
                ps.setString(2, id.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        d.completed().add(rs.getString(1));
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT collection FROM shcollectibles_suivies WHERE serveur = ? AND uuid = ? AND jour = ?")) {
                ps.setString(1, server);
                ps.setString(2, id.toString());
                ps.setString(3, day.toString());
                Set<String> tracked = new HashSet<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        tracked.add(rs.getString(1));
                    }
                }
                d.track(day, tracked);
            }
        }
        return d;
    }

    /** Vrai si le collectible n'était pas encore enregistré : lui seul donne la récompense. */
    boolean add(UUID id, String collection, int number) throws SQLException {
        try (Connection c = source.getConnection(); PreparedStatement ps = c.prepareStatement(
                "INSERT IGNORE INTO shcollectibles_trouvailles (serveur, uuid, collection, numero) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, server);
            ps.setString(2, id.toString());
            ps.setString(3, collection);
            ps.setInt(4, number);
            return ps.executeUpdate() > 0;
        }
    }

    void remove(UUID id, String collection, int number) throws SQLException {
        try (Connection c = source.getConnection(); PreparedStatement ps = c.prepareStatement(
                "DELETE FROM shcollectibles_trouvailles WHERE serveur = ? AND uuid = ? AND collection = ? AND numero = ?")) {
            ps.setString(1, server);
            ps.setString(2, id.toString());
            ps.setString(3, collection);
            ps.setInt(4, number);
            ps.executeUpdate();
        }
    }

    void complete(UUID id, String collection) throws SQLException {
        try (Connection c = source.getConnection(); PreparedStatement ps = c.prepareStatement(
                "INSERT IGNORE INTO shcollectibles_completes (serveur, uuid, collection) VALUES (?, ?, ?)")) {
            ps.setString(1, server);
            ps.setString(2, id.toString());
            ps.setString(3, collection);
            ps.executeUpdate();
        }
    }

    /** Collection suivie au détecteur ce jour-là. */
    void track(UUID id, LocalDate day, String collection) throws SQLException {
        try (Connection c = source.getConnection(); PreparedStatement ps = c.prepareStatement(
                "INSERT IGNORE INTO shcollectibles_suivies (serveur, uuid, jour, collection) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, server);
            ps.setString(2, id.toString());
            ps.setString(3, day.toString());
            ps.setString(4, collection);
            ps.executeUpdate();
        }
    }

    void untrack(UUID id) throws SQLException {
        try (Connection c = source.getConnection(); PreparedStatement ps = c.prepareStatement(
                "DELETE FROM shcollectibles_suivies WHERE serveur = ? AND uuid = ?")) {
            ps.setString(1, server);
            ps.setString(2, id.toString());
            ps.executeUpdate();
        }
    }

    /** Jours passés : ils ne servent plus. */
    void purgeTracked(LocalDate today) throws SQLException {
        try (Connection c = source.getConnection(); PreparedStatement ps = c.prepareStatement(
                "DELETE FROM shcollectibles_suivies WHERE serveur = ? AND jour < ?")) {
            ps.setString(1, server);
            ps.setString(2, today.toString());
            ps.executeUpdate();
        }
    }

    void saveName(UUID id, String name) throws SQLException {
        try (Connection c = source.getConnection(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO shcollectibles_joueurs (uuid, nom) VALUES (?, ?) ON DUPLICATE KEY UPDATE nom = VALUES(nom)")) {
            ps.setString(1, id.toString());
            ps.setString(2, name);
            ps.executeUpdate();
        }
    }

    /** UUID d'un pseudo déjà vu ; null sinon. */
    UUID find(String name) throws SQLException {
        try (Connection c = source.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT uuid FROM shcollectibles_joueurs WHERE nom = ? LIMIT 1")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? UUID.fromString(rs.getString(1)) : null;
            }
        }
    }

    /** Joueurs qui ont complété chaque collection. */
    Map<String, Integer> completions() throws SQLException {
        return counts("SELECT collection, COUNT(*) FROM shcollectibles_completes WHERE serveur = ? GROUP BY collection");
    }

    Map<String, Integer> finds() throws SQLException {
        return counts("SELECT collection, COUNT(*) FROM shcollectibles_trouvailles WHERE serveur = ? GROUP BY collection");
    }

    private Map<String, Integer> counts(String sql) throws SQLException {
        Map<String, Integer> out = new HashMap<>();
        try (Connection c = source.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, server);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getString(1), rs.getInt(2));
                }
            }
        }
        return out;
    }

    /** Efface des trouvailles ; null = tous les joueurs, ou toutes les collections. Rend le nombre de lignes effacées. */
    int reset(UUID id, String collection) throws SQLException {
        int n = 0;
        for (String table : List.of("shcollectibles_trouvailles", "shcollectibles_completes")) {
            StringBuilder sql = new StringBuilder("DELETE FROM ").append(table).append(" WHERE serveur = ?");
            List<String> values = new ArrayList<>(List.of(server));
            if (id != null) {
                sql.append(" AND uuid = ?");
                values.add(id.toString());
            }
            if (collection != null) {
                sql.append(" AND collection = ?");
                values.add(collection);
            }
            try (Connection c = source.getConnection(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
                for (int i = 0; i < values.size(); i++) {
                    ps.setString(i + 1, values.get(i));
                }
                n += ps.executeUpdate();
            }
        }
        return n;
    }

    /** Joueur repris d'un autre plugin. */
    record Imported(UUID id, String name, Map<String, List<Integer>> finds, List<String> completed) {
    }

    /** Tout ou rien ; une ligne déjà présente est laissée telle quelle. Les dates d'origine n'existent pas : vides. */
    void importAll(List<Imported> players) throws SQLException {
        try (Connection c = source.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement name = c.prepareStatement("INSERT IGNORE INTO shcollectibles_joueurs (uuid, nom) VALUES (?, ?)");
                 PreparedStatement find = c.prepareStatement(
                         "INSERT IGNORE INTO shcollectibles_trouvailles (serveur, uuid, collection, numero, trouve_le) VALUES (?, ?, ?, ?, NULL)");
                 PreparedStatement done = c.prepareStatement(
                         "INSERT IGNORE INTO shcollectibles_completes (serveur, uuid, collection, complete_le) VALUES (?, ?, ?, NULL)")) {
                for (Imported p : players) {
                    if (!p.name().isEmpty()) {
                        name.setString(1, p.id().toString());
                        name.setString(2, p.name());
                        name.addBatch();
                    }
                    for (Map.Entry<String, List<Integer>> en : p.finds().entrySet()) {
                        for (int n : en.getValue()) {
                            find.setString(1, server);
                            find.setString(2, p.id().toString());
                            find.setString(3, en.getKey());
                            find.setInt(4, n);
                            find.addBatch();
                        }
                    }
                    for (String col : p.completed()) {
                        done.setString(1, server);
                        done.setString(2, p.id().toString());
                        done.setString(3, col);
                        done.addBatch();
                    }
                }
                name.executeBatch();
                find.executeBatch();
                done.executeBatch();
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }
}
