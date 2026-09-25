package id.senzy.shop.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class TransactionRepository {

    public void insert(Connection c, TransactionRecord t) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO transactions (uuid, player_name, type, material, amount, price,
                                          balance_before, balance_after, timestamp)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""")) {
            ps.setString(1, t.uuid());
            ps.setString(2, t.playerName());
            ps.setString(3, t.type().name());
            ps.setString(4, t.material());
            ps.setLong(5, t.amount());
            ps.setLong(6, t.price());
            ps.setLong(7, t.balanceBefore());
            ps.setLong(8, t.balanceAfter());
            ps.setLong(9, t.timestamp());
            ps.executeUpdate();
        }
    }

    public List<TransactionRecord> recent(Connection c, int limit) throws SQLException {
        List<TransactionRecord> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT id, uuid, player_name, type, material, amount, price, balance_before, balance_after, timestamp
                FROM transactions ORDER BY id DESC LIMIT ?""")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new TransactionRecord(rs.getLong(1), rs.getString(2), rs.getString(3),
                            TransactionRecord.Type.valueOf(rs.getString(4)), rs.getString(5),
                            rs.getLong(6), rs.getLong(7), rs.getLong(8), rs.getLong(9), rs.getLong(10)));
                }
            }
        }
        return out;
    }
}
