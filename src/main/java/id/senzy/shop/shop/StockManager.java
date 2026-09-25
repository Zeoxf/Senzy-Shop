package id.senzy.shop.shop;

import id.senzy.shop.database.DatabaseManager;
import id.senzy.shop.database.StockRecord;
import id.senzy.shop.database.StockRepository;
import id.senzy.shop.database.TransactionRecord;
import id.senzy.shop.database.TransactionRepository;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Stok saat ini per item. Hanya diakses dari main thread; database menerima snapshot immutable. */
public final class StockManager {

    private static final class Entry {
        int stock;
        int max;
        long restockId;
        long updatedAt;
    }

    private final ShopManager shop;
    private final DatabaseManager db;
    private final StockRepository repo;
    private final TransactionRepository transactions;
    private final Map<Material, Entry> entries = new HashMap<>();

    public StockManager(ShopManager shop, DatabaseManager db, StockRepository repo, TransactionRepository transactions) {
        this.shop = shop;
        this.db = db;
        this.repo = repo;
        this.transactions = transactions;
    }

    public void load() {
        List<StockRecord> rows = db.supply(repo::loadAll).join();
        entries.clear();
        for (StockRecord r : rows) {
            Material m = Material.matchMaterial(r.material());
            if (m == null) continue;
            Entry e = new Entry();
            e.stock = Math.max(0, r.stock());
            e.max = Math.max(e.stock, r.maxStock());
            e.restockId = r.restockId();
            e.updatedAt = r.updatedAt();
            entries.put(m, e);
        }
        reconcile();
    }

    /** Pastikan setiap item di katalog punya entri stok (item baru mulai dari 0). */
    public void reconcile() {
        for (ShopItem item : shop.all()) entries.computeIfAbsent(item.material(), k -> new Entry());
    }

    private Entry entry(ShopItem item) {
        return entries.computeIfAbsent(item.material(), k -> new Entry());
    }

    public int getStock(ShopItem item) {
        return entry(item).stock;
    }

    public int getMax(ShopItem item) {
        return entry(item).max;
    }

    public boolean take(ShopItem item, int amount) {
        Entry e = entry(item);
        if (amount <= 0 || e.stock < amount) return false;
        e.stock -= amount;
        e.updatedAt = System.currentTimeMillis();
        return true;
    }

    /** Dipakai untuk rollback transaksi beli. */
    public void restore(ShopItem item, int amount) {
        if (amount <= 0) return;
        Entry e = entry(item);
        e.stock += amount;
        e.updatedAt = System.currentTimeMillis();
    }

    public void applyRestock(Map<Material, Integer> rolled, long restockId, long now) {
        for (ShopItem item : shop.all()) {
            Entry e = entry(item);
            int value = Math.max(0, rolled.getOrDefault(item.material(), 0));
            e.stock = value;
            e.max = value;
            e.restockId = restockId;
            e.updatedAt = now;
        }
    }

    /** Dipakai event runtime untuk membuat stok terbatas tanpa menunggu restock global. */
    public void setRuntimeStock(ShopItem item, int amount) {
        Entry e = entry(item);
        e.stock = Math.max(0, amount);
        e.max = e.stock;
        e.updatedAt = System.currentTimeMillis();
        StockRecord snap = snapshot(item);
        db.run(c -> repo.save(c, snap));
    }

    public StockRecord snapshot(ShopItem item) {
        Entry e = entry(item);
        return new StockRecord(item.material().name(), item.category().id(), e.stock, e.max,
                item.buyPrice(), item.sellPrice(), e.restockId, e.updatedAt);
    }

    public List<StockRecord> snapshotAll() {
        List<StockRecord> out = new ArrayList<>();
        for (ShopItem item : shop.all()) out.add(snapshot(item));
        return out;
    }

    public boolean adminSetStock(String actor, ShopItem item, int amount) {
        if (amount < 0) return false;
        Entry e = entry(item);
        long now = System.currentTimeMillis();
        e.stock = amount;
        e.max = Math.max(e.max, amount);
        e.updatedAt = now;
        StockRecord snap = snapshot(item);
        TransactionRecord log = new TransactionRecord(0L, actor, actor, TransactionRecord.Type.ADMIN,
                "SETSTOCK " + item.material().name(), amount, 0L, 0L, 0L, now);
        db.run(c -> {
            repo.save(c, snap);
            transactions.insert(c, log);
        });
        return true;
    }
}
