package id.senzy.shop.shop;

import id.senzy.shop.contract.ContractManager;
import id.senzy.shop.event.ShopEventManager;
import id.senzy.shop.database.DatabaseManager;
import id.senzy.shop.database.PlayerRecord;
import id.senzy.shop.database.PlayerRepository;
import id.senzy.shop.database.StockRecord;
import id.senzy.shop.database.StockRepository;
import id.senzy.shop.database.TransactionRecord;
import id.senzy.shop.database.TransactionRepository;
import id.senzy.shop.economy.EconomyManager;
import id.senzy.shop.util.ItemUtil;
import id.senzy.shop.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Logika beli/jual. Seluruh validasi dan perubahan state terjadi SINKRON di main thread
 * (tidak ada celah race), lalu balance + stok + log disimpan dalam satu transaksi SQL.
 * Server tidak pernah percaya data dari GUI: item, harga, dan stok selalu dibaca ulang dari katalog.
 */
public final class TradeService {
    private final ShopManager shop;
    private final StockManager stock;
    private final EconomyManager economy;
    private final DatabaseManager db;
    private final PlayerRepository players;
    private final StockRepository stocks;
    private final TransactionRepository transactions;
    private final MessageUtil msg;
    private final ContractManager contracts;
    private final ShopEventManager events;
    private final Set<UUID> busy = new HashSet<>();

    public TradeService(ShopManager shop, StockManager stock, EconomyManager economy, DatabaseManager db,
                        PlayerRepository players, StockRepository stocks, TransactionRepository transactions,
                        MessageUtil msg, ContractManager contracts, ShopEventManager events) {
        this.shop = shop;
        this.stock = stock;
        this.economy = economy;
        this.db = db;
        this.players = players;
        this.stocks = stocks;
        this.transactions = transactions;
        this.msg = msg;
        this.contracts = contracts;
        this.events = events;
    }

    /** Hasil perhitungan pembelian TANPA efek samping - dipakai GUI untuk cek "expensive purchase". */
    public record BuyPlan(boolean ok, ShopItem item, int amount, long cost) {
        static final BuyPlan FAIL = new BuyPlan(false, null, 0, 0L);
    }

    /** requested > 0 = jumlah tepat, requested < 0 = maksimum. */
    public boolean buy(Player player, ShopItem requestedItem, int requested) {
        if (!busy.add(player.getUniqueId())) return false;   // cegah proses ganda / re-entrancy
        try {
            return doBuy(player, requestedItem, requested);
        } finally {
            busy.remove(player.getUniqueId());
        }
    }

    /** requested > 0 = jumlah tepat, requested < 0 = semua. */
    public boolean sell(Player player, ShopItem requestedItem, int requested) {
        if (!busy.add(player.getUniqueId())) return false;
        try {
            return doSell(player, requestedItem, requested);
        } finally {
            busy.remove(player.getUniqueId());
        }
    }

    public boolean sellAll(Player player) {
        if (!busy.add(player.getUniqueId())) return false;
        try {
            return doSellAll(player);
        } finally {
            busy.remove(player.getUniqueId());
        }
    }

    /**
     * Hitung saja (tanpa mengubah apa pun / tanpa kirim pesan) - dipakai GUI untuk memutuskan
     * apakah perlu menampilkan dialog konfirmasi (expensive purchase) sebelum benar-benar membeli.
     */
    public BuyPlan previewBuy(Player p, ShopItem requestedItem, int requested) {
        if (!events.canUseShop(shop.activeShop()) || requested == 0) return BuyPlan.FAIL;
        ShopItem item = shop.get(requestedItem.material());
        if (item == null || !item.canBuy() || !item.allowedIn(p.getWorld().getName())) return BuyPlan.FAIL;
        int available = stock.getStock(item);
        if (available <= 0) return BuyPlan.FAIL;
        long price = events.buyPrice(shop.activeShop(), item);
        long balance = economy.getBalance(p.getUniqueId());
        if (balance < price) return BuyPlan.FAIL;
        int space = ItemUtil.capacityFor(p.getInventory(), item.material());
        if (space <= 0) return BuyPlan.FAIL;
        long affordable = balance / price;
        int amount;
        if (requested < 0) {
            amount = (int) Math.min(Math.min((long) available, (long) space), Math.min(affordable, (long) Integer.MAX_VALUE));
        } else {
            amount = Math.min(requested, available);
            if (amount > affordable || amount > space) return BuyPlan.FAIL;
        }
        long cost;
        try {
            cost = Math.multiplyExact(price, (long) amount);
        } catch (ArithmeticException e) {
            return BuyPlan.FAIL;
        }
        if (amount <= 0 || cost <= 0 || cost > balance) return BuyPlan.FAIL;
        return new BuyPlan(true, item, amount, cost);
    }

    /** Maksimum pembelian yang valid saat selector dibuka: stok + saldo + kapasitas inventory. */
    public int maxBuyable(Player p, ShopItem requestedItem) {
        if (!events.canUseShop(shop.activeShop())) return 0;
        ShopItem item = shop.get(requestedItem.material());
        if (item == null || !item.canBuy() || !item.allowedIn(p.getWorld().getName())) return 0;
        int available = stock.getStock(item);
        long price = events.buyPrice(shop.activeShop(), item);
        if (available <= 0 || price <= 0) return 0;
        long affordable = economy.getBalance(p.getUniqueId()) / price;
        int space = ItemUtil.capacityFor(p.getInventory(), item.material());
        return (int) Math.max(0, Math.min(Math.min((long) available, affordable), (long) space));
    }

    // ------------------------------------------------------------------ BUY

    private boolean doBuy(Player p, ShopItem requestedItem, int requested) {
        UUID id = p.getUniqueId();
        if (!events.canUseShop(shop.activeShop())) {
            msg.send(p, "trade.item-unavailable");
            return false;
        }
        if (requested == 0) return false;
        ShopItem item = shop.get(requestedItem.material());      // selalu pakai katalog terbaru
        if (item == null || !item.canBuy() || !item.allowedIn(p.getWorld().getName())) {
            msg.send(p, "trade.item-unavailable");
            return false;
        }
        int available = stock.getStock(item);
        if (available <= 0) {
            msg.send(p, "trade.out-of-stock");
            return false;
        }
        long price = events.buyPrice(shop.activeShop(), item);
        long balance = economy.getBalance(id);
        if (balance < price) {
            msg.send(p, "trade.insufficient-balance");
            return false;
        }
        PlayerInventory inv = p.getInventory();
        Material material = item.material();
        int space = ItemUtil.capacityFor(inv, material);
        if (space <= 0) {
            msg.send(p, "trade.inventory-full");
            return false;
        }
        long affordable = balance / price;
        int amount;
        if (requested < 0) {
            amount = (int) Math.min(Math.min((long) available, (long) space), Math.min(affordable, (long) Integer.MAX_VALUE));
        } else {
            amount = Math.min(requested, available);
            if (amount > affordable) {
                msg.send(p, "trade.insufficient-balance");
                return false;
            }
            if (amount > space) {
                msg.send(p, "trade.inventory-full");
                return false;
            }
        }
        long cost;
        try {
            cost = Math.multiplyExact(price, (long) amount);
        } catch (ArithmeticException e) {
            msg.send(p, "trade.invalid");
            return false;
        }
        if (amount <= 0 || cost <= 0 || cost > balance) {
            msg.send(p, "trade.invalid");
            return false;
        }

        // --- semua validasi lolos: eksekusi (dengan rollback penuh jika gagal) ---
        long before = balance;
        if (!economy.withdrawMemory(id, cost)) {
            msg.send(p, "trade.invalid");
            return false;
        }
        if (!stock.take(item, amount)) {
            economy.depositMemory(id, cost);
            msg.send(p, "trade.out-of-stock");
            return false;
        }
        int leftover = ItemUtil.give(inv, material, amount);
        if (leftover > 0) {
            int given = amount - leftover;
            if (given > 0) ItemUtil.removePlain(inv, material, given);
            stock.restore(item, amount);
            economy.depositMemory(id, cost);
            msg.send(p, "trade.inventory-full");
            return false;
        }
        long after = economy.getBalance(id);
        TransactionRecord rec = new TransactionRecord(0L, id.toString(), p.getName(),
                TransactionRecord.Type.BUY, material.name(), amount, cost, before, after, System.currentTimeMillis());
        persist(id, List.of(rec), item);
        economy.syncPdc(p);
        msg.send(p, "trade.buy-success", "amount", amount, "item", ItemUtil.prettyName(material),
                "price", msg.money(cost));
        return true;
    }

    // ------------------------------------------------------------------ SELL

    private boolean doSell(Player p, ShopItem requestedItem, int requested) {
        UUID id = p.getUniqueId();
        if (!events.canUseShop(shop.activeShop())) {
            msg.send(p, "trade.not-sellable");
            return false;
        }
        if (requested == 0) return false;
        ShopItem item = shop.get(requestedItem.material());
        if (item == null || !item.canSell() || !item.allowedIn(p.getWorld().getName())) {
            msg.send(p, "trade.not-sellable");
            return false;
        }
        PlayerInventory inv = p.getInventory();
        Material material = item.material();
        int have = ItemUtil.countPlain(inv, material);
        if (have <= 0) {
            msg.send(p, "trade.no-item");
            return false;
        }
        int amount = requested < 0 ? have : Math.min(requested, have);
        long sellPrice = events.sellPrice(shop.activeShop(), item);
        long credit;
        try {
            credit = Math.multiplyExact(sellPrice, (long) amount);
        } catch (ArithmeticException e) {
            msg.send(p, "trade.invalid");
            return false;
        }
        if (!economy.canDeposit(id, credit)) {
            msg.send(p, "trade.balance-limit");
            return false;
        }
        int removed = ItemUtil.removePlain(inv, material, amount);
        if (removed <= 0) {
            msg.send(p, "trade.no-item");
            return false;
        }
        long real = sellPrice * removed;     // kredit selalu dihitung dari item yang BENAR-BENAR terhapus
        long before = economy.getBalance(id);
        if (!economy.depositMemory(id, real)) {
            ItemUtil.give(inv, material, removed);  // rollback
            msg.send(p, "trade.invalid");
            return false;
        }
        long after = economy.getBalance(id);
        TransactionRecord rec = new TransactionRecord(0L, id.toString(), p.getName(),
                TransactionRecord.Type.SELL, material.name(), removed, real, before, after, System.currentTimeMillis());
        persist(id, List.of(rec), null);
        economy.syncPdc(p);
        contracts.recordSell(p, item, removed);      // hanya SELL yang valid & tereksekusi yang menambah progress
        msg.send(p, "trade.sell-success", "amount", removed, "item", ItemUtil.prettyName(material),
                "price", msg.money(real));
        return true;
    }

    private boolean doSellAll(Player p) {
        UUID id = p.getUniqueId();
        if (!events.canUseShop(shop.activeShop())) {
            msg.send(p, "trade.sellall-empty");
            return false;
        }
        PlayerInventory inv = p.getInventory();
        String world = p.getWorld().getName();
        List<TransactionRecord> records = new ArrayList<>();
        List<ShopItem> soldItems = new ArrayList<>();
        List<Integer> soldAmounts = new ArrayList<>();
        long total = 0L;
        long items = 0L;
        boolean limitReached = false;

        for (ShopItem item : shop.all()) {
            if (!item.canSell() || !item.allowedIn(world)) continue;
            Material material = item.material();
            int have = ItemUtil.countPlain(inv, material);
            if (have <= 0) continue;
            long sellPrice = events.sellPrice(shop.activeShop(), item);
            long credit;
            try {
                credit = Math.multiplyExact(sellPrice, (long) have);
            } catch (ArithmeticException e) {
                continue;
            }
            if (!economy.canDeposit(id, credit)) {
                limitReached = true;
                break;
            }
            int removed = ItemUtil.removePlain(inv, material, have);
            if (removed <= 0) continue;
            long real = sellPrice * removed;
            long before = economy.getBalance(id);
            if (!economy.depositMemory(id, real)) {
                ItemUtil.give(inv, material, removed);
                continue;
            }
            records.add(new TransactionRecord(0L, id.toString(), p.getName(), TransactionRecord.Type.SELL,
                    material.name(), removed, real, before, economy.getBalance(id), System.currentTimeMillis()));
            soldItems.add(item);
            soldAmounts.add(removed);
            total += real;
            items += removed;
        }
        if (records.isEmpty()) {
            msg.send(p, limitReached ? "trade.balance-limit" : "trade.sellall-empty");
            return false;
        }
        persist(id, records, null);
        economy.syncPdc(p);
        for (int i = 0; i < soldItems.size(); i++) contracts.recordSell(p, soldItems.get(i), soldAmounts.get(i));
        msg.send(p, "trade.sellall-success", "amount", items, "kinds", records.size(), "price", msg.money(total));
        if (limitReached) msg.send(p, "trade.balance-limit");
        return true;
    }

    // ------------------------------------------------------------------ persist

    /** Balance + stok + log dalam SATU transaksi SQL (all-or-nothing). */
    private void persist(UUID id, List<TransactionRecord> records, ShopItem stockItem) {
        PlayerRecord playerSnapshot = economy.record(id);
        StockRecord stockSnapshot = stockItem == null ? null : stock.snapshot(stockItem);
        db.run(c -> {
            players.save(c, playerSnapshot);
            if (stockSnapshot != null && stock.persistent()) stocks.save(c, stockSnapshot);
            for (TransactionRecord r : records) transactions.insert(c, r);
        });
    }
}
