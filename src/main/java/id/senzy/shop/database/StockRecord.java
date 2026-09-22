package id.senzy.shop.database;

/** Snapshot immutable satu baris tabel shop_stock. */
public record StockRecord(String material, String category, int stock, int maxStock,
                          long buyPrice, long sellPrice, long restockId, long updatedAt) {}
