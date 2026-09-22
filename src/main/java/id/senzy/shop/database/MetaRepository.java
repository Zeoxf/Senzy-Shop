package id.senzy.shop.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/** Tabel key-value generik "meta" (dipakai RestockManager & ContractManager untuk timer/state). */
public final class MetaRepository {

    public Map<String, String> loadAll(Connection c) throws SQLException {
        Map<String, String> out = new HashMap<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT meta_key, meta_value FROM meta");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.put(rs.getString(1), rs.getString(2));
        }
        return out;
    }

    public void save(Connection c, String key, String value) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO meta (meta_key, meta_value) VALUES (?, ?)
                ON CONFLICT(meta_key) DO UPDATE SET meta_value = excluded.meta_value""")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }
}
