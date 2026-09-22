package id.senzy.shop.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PlayerRepository {

    public List<PlayerRecord> loadAll(Connection c) throws SQLException {
        List<PlayerRecord> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT uuid, name, balance, created_at, updated_at FROM players");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new PlayerRecord(UUID.fromString(rs.getString(1)), rs.getString(2),
                        rs.getLong(3), rs.getLong(4), rs.getLong(5)));
            }
        }
        return out;
    }

    public void save(Connection c, PlayerRecord p) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO players (uuid, name, balance, created_at, updated_at) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET name = excluded.name,
                    balance = excluded.balance, updated_at = excluded.updated_at""")) {
            ps.setString(1, p.uuid().toString());
            ps.setString(2, p.name());
            ps.setLong(3, p.balance());
            ps.setLong(4, p.createdAt());
            ps.setLong(5, p.updatedAt());
            ps.executeUpdate();
        }
    }
}
