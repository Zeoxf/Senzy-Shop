package id.senzy.shop.database;

/** Satu baris tabel transactions. id = 0 untuk record yang belum disimpan. */
public record TransactionRecord(long id, String uuid, String playerName, Type type, String material,
                                long amount, long price, long balanceBefore, long balanceAfter, long timestamp) {
    public enum Type { BUY, SELL, ADMIN, CONTRACT_REWARD }
}
