package nl.cosmetics;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * De verbinding met de gedeelde database. Elke methode blokkeert en hoort dus NIET op de hoofd-thread te draaien.
 * Per speler staat er één rij: uuid, naam, de gegevens als JSON en een tijdstip van de laatste wijziging.
 * De SQL is bewust simpel (geen MySQL-only syntax).
 */
final class Sync {
    /** Eén rij uit de database. */
    record Row(UUID id, String name, String json, long updated) {
    }

    private final Logger log;
    private final String url;
    private final String altUrl;
    private final String user;
    private final String pass;
    private final String table;
    private Connection con;

    Sync(Logger log, String url, String altUrl, String user, String pass, String table) {
        this.log = log;
        this.url = url;
        this.altUrl = altUrl;
        this.user = user;
        this.pass = pass;
        this.table = table;
    }

    /** Alleen letters, cijfers en een underscore, zodat de tabelnaam nooit SQL kan breken. */
    static String cleanTable(String t) {
        String s = t == null ? "" : t.replaceAll("[^A-Za-z0-9_]", "");
        return s.isEmpty() ? "cosmetics_spelers" : s;
    }

    private Connection open() throws SQLException {
        SQLException first = null;
        for (String u : altUrl == null || altUrl.isBlank() ? new String[] {url} : new String[] {url, altUrl}) {
            try {
                return DriverManager.getConnection(u, user, pass);
            } catch (SQLException ex) {
                if (first == null) first = ex;
            }
        }
        throw first;
    }

    /** Geeft een werkende verbinding, en maakt zo nodig een nieuwe. */
    private Connection c() throws SQLException {
        if (con != null) {
            try {
                if (!con.isClosed() && con.isValid(2)) return con;
            } catch (SQLException ignored) {
                // nieuwe verbinding hieronder
            }
            try {
                con.close();
            } catch (SQLException ignored) {
                // maakt niet uit
            }
            con = null;
        }
        con = open();
        return con;
    }

    synchronized void init() throws SQLException {
        try (Statement st = c().createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS " + table
                    + " (uuid VARCHAR(36) NOT NULL PRIMARY KEY, name VARCHAR(32), data MEDIUMTEXT, updated BIGINT NOT NULL)");
        }
    }

    private List<Row> read(ResultSet rs) throws SQLException {
        List<Row> out = new ArrayList<>();
        while (rs.next()) {
            try {
                out.add(new Row(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getString(3), rs.getLong(4)));
            } catch (IllegalArgumentException ex) {
                log.warning("Rij met ongeldige uuid in de database overgeslagen.");
            }
        }
        return out;
    }

    /** Alle rijen die op of na dit tijdstip gewijzigd zijn (0 = alles). */
    synchronized List<Row> since(long t) throws SQLException {
        try (PreparedStatement ps = c().prepareStatement("SELECT uuid, name, data, updated FROM " + table + " WHERE updated >= ?")) {
            ps.setLong(1, t);
            try (ResultSet rs = ps.executeQuery()) {
                return read(rs);
            }
        }
    }

    synchronized Row one(UUID id) throws SQLException {
        try (PreparedStatement ps = c().prepareStatement("SELECT uuid, name, data, updated FROM " + table + " WHERE uuid = ?")) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                List<Row> l = read(rs);
                return l.isEmpty() ? null : l.get(0);
            }
        }
    }

    synchronized void save(UUID id, String name, String json, long updated) throws SQLException {
        Connection k = c();
        if (update(k, id, name, json, updated) > 0) return;
        try (PreparedStatement ps = k.prepareStatement("INSERT INTO " + table + " (uuid, name, data, updated) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, id.toString());
            ps.setString(2, name);
            ps.setString(3, json);
            ps.setLong(4, updated);
            ps.executeUpdate();
        } catch (SQLException ex) {
            // De andere server was ons net voor met het aanmaken van de rij: gewoon bijwerken.
            if (update(k, id, name, json, updated) == 0) throw ex;
        }
    }

    private int update(Connection k, UUID id, String name, String json, long updated) throws SQLException {
        try (PreparedStatement ps = k.prepareStatement("UPDATE " + table + " SET name = ?, data = ?, updated = ? WHERE uuid = ?")) {
            ps.setString(1, name);
            ps.setString(2, json);
            ps.setLong(3, updated);
            ps.setString(4, id.toString());
            return ps.executeUpdate();
        }
    }

    synchronized void close() {
        if (con != null) {
            try {
                con.close();
            } catch (SQLException ignored) {
                // maakt niet uit
            }
            con = null;
        }
    }
}
