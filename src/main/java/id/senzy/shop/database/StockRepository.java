package id.senzy.shop.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Tabel shop_stock. Untuk tabel meta generik (timer restock/contract) lihat {@link MetaRepository}. */
public final class StockRepository {

    public List<StockRecord> loadAll(Connection c) throws SQLException {
        List<StockRecord> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT material, category, stock, max_stock, buy_price, sell_price, restock_id, updated_at
                FROM shop_stock""");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new StockRecord(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getInt(4),
                        rs.getLong(5), rs.getLong(6), rs.getLong(7), rs.getLong(8)));
            }
        }
        return out;
    }

    public void save(Connection c, StockRecord r) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO shop_stock (material, category, stock, max_stock, buy_price, sell_price, restock_id, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(material) DO UPDATE SET category = excluded.category, stock = excluded.stock,
                    max_stock = excluded.max_stock, buy_price = excluded.buy_price,
                    sell_price = excluded.sell_price, restock_id = excluded.restock_id,
                    updated_at = excluded.updated_at""")) {
            ps.setString(1, r.material());
            ps.setString(2, r.category());
            ps.setInt(3, Math.max(0, r.stock()));
            ps.setInt(4, r.maxStock());
            ps.setLong(5, r.buyPrice());
            ps.setLong(6, r.sellPrice());
            ps.setLong(7, r.restockId());
            ps.setLong(8, r.updatedAt());
            ps.executeUpdate();
        }
    }
}
