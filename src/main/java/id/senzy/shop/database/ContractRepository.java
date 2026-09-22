package id.senzy.shop.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ContractRepository {

    /** Insert satu definisi contract baru, mengembalikan id yang di-generate. */
    public long insertContract(Connection c, ContractRecord r) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO contracts (contract_key, type, target, required_amount, reward, generation_id, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)""", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, r.contractKey());
            ps.setString(2, r.type());
            ps.setString(3, r.target());
            ps.setLong(4, r.requiredAmount());
            ps.setLong(5, r.reward());
            ps.setLong(6, r.generationId());
            ps.setLong(7, r.expiresAt());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0L;
            }
        }
    }

    /** Contract yang masih aktif (belum lewat expires_at) untuk tipe tertentu. */
    public List<ContractRecord> loadActive(Connection c, String type, long now) throws SQLException {
        List<ContractRecord> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT id, contract_key, type, target, required_amount, reward, generation_id, expires_at
                FROM contracts WHERE type = ? AND expires_at > ? ORDER BY id""")) {
            ps.setString(1, type);
            ps.setLong(2, now);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        }
        return out;
    }

    public List<ContractProgressRecord> loadProgressForPlayer(Connection c, UUID uuid) throws SQLException {
        List<ContractProgressRecord> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT uuid, contract_id, progress, completed, claimed, updated_at
                FROM contract_progress WHERE uuid = ?""")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapProgress(rs));
            }
        }
        return out;
    }

    /** Semua progres yang tersimpan (dipakai saat startup untuk memuat cache di memori). */
    public List<ContractProgressRecord> loadAllProgress(Connection c) throws SQLException {
        List<ContractProgressRecord> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT uuid, contract_id, progress, completed, claimed, updated_at FROM contract_progress""");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(mapProgress(rs));
        }
        return out;
    }

    public void saveProgress(Connection c, ContractProgressRecord p) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO contract_progress (uuid, contract_id, progress, completed, claimed, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(uuid, contract_id) DO UPDATE SET progress = excluded.progress,
                    completed = excluded.completed, claimed = excluded.claimed, updated_at = excluded.updated_at""")) {
            ps.setString(1, p.uuid().toString());
            ps.setLong(2, p.contractId());
            ps.setLong(3, p.progress());
            ps.setInt(4, p.completed() ? 1 : 0);
            ps.setInt(5, p.claimed() ? 1 : 0);
            ps.setLong(6, p.updatedAt());
            ps.executeUpdate();
        }
    }

    private static ContractRecord map(ResultSet rs) throws SQLException {
        return new ContractRecord(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getLong(5), rs.getLong(6), rs.getLong(7), rs.getLong(8));
    }

    private static ContractProgressRecord mapProgress(ResultSet rs) throws SQLException {
        return new ContractProgressRecord(UUID.fromString(rs.getString(1)), rs.getLong(2), rs.getLong(3),
                rs.getInt(4) != 0, rs.getInt(5) != 0, rs.getLong(6));
    }
}
