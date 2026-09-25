package id.senzy.shop.economy;

import id.senzy.shop.SenzyShop;
import id.senzy.shop.database.DatabaseManager;
import id.senzy.shop.database.PlayerRecord;
import id.senzy.shop.database.PlayerRepository;
import id.senzy.shop.database.TransactionRecord;
import id.senzy.shop.database.TransactionRepository;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * API ekonomi Senzy Coin. Semua nilai bertipe long (tidak pernah double) dan tidak boleh negatif
 * ataupun melebihi economy.max-balance.
 *
 * Metode *Memory hanya mengubah cache (dipakai TradeService yang menyimpan balance + stok + log
 * dalam SATU transaksi SQL). Metode publik lain otomatis menyimpan ke database.
 */
public final class EconomyManager {
    private final SenzyShop plugin;
    private final BalanceManager balances;
    private final DatabaseManager db;
    private final PlayerRepository players;
    private final TransactionRepository transactions;

    public EconomyManager(SenzyShop plugin, BalanceManager balances, DatabaseManager db,
                          PlayerRepository players, TransactionRepository transactions) {
        this.plugin = plugin;
        this.balances = balances;
        this.db = db;
        this.players = players;
        this.transactions = transactions;
    }

    // ---------- query ----------

    public long getBalance(UUID id) {
        return balances.get(id);
    }

    public boolean hasBalance(UUID id, long amount) {
        return amount >= 0 && balances.get(id) >= amount;
    }

    public long maxBalance() {
        return Math.max(0L, plugin.getConfig().getLong("economy.max-balance", 1_000_000_000_000L));
    }

    public boolean canDeposit(UUID id, long amount) {
        if (amount < 0 || !balances.exists(id)) return false;
        try {
            return Math.addExact(balances.get(id), amount) <= maxBalance();
        } catch (ArithmeticException e) {
            return false;
        }
    }

    public PlayerRecord record(UUID id) {
        return balances.record(id);
    }

    public UUID findByName(String name) {
        return balances.findByName(name);
    }

    public String nameOf(UUID id) {
        return balances.nameOf(id);
    }

    public java.util.List<String> knownNames() {
        return balances.names();
    }

    // ---------- akun ----------

    /** @return true kalau akun ini BENAR-BENAR baru dibuat (baris database sebelumnya tidak ada). */
    public boolean ensureAccount(UUID id, String name) {
        long starting = Math.max(0L, Math.min(maxBalance(),
                plugin.getConfig().getLong("economy.starting-balance", 1000L)));
        BalanceManager.EnsureResult res = balances.ensure(id, name, starting, System.currentTimeMillis());
        if (res.toSave() != null) db.run(c -> players.save(c, res.toSave()));
        return res.created();
    }

    // ---------- cadangan saldo di PersistentDataContainer milik pemain ----------
    // SQLite TETAP sumber utama untuk semua transaksi & command admin (termasuk saat pemain
    // offline, yang tidak bisa ditulisi lewat PDC karena keterbatasan API Paper). PDC di sini
    // HANYA cadangan supaya saldo bisa dipulihkan kalau plugin/database dihapus lalu dipasang
    // ulang - PDC ikut file playerdata Minecraft, bukan file plugin.

    private NamespacedKey balanceKey() {
        return new NamespacedKey(plugin, "balance");
    }

    /** Salin saldo saat ini ke PDC pemain (dipanggil tiap balance berubah & tiap kali join). */
    public void syncPdc(Player player) {
        player.getPersistentDataContainer().set(balanceKey(), PersistentDataType.LONG, getBalance(player.getUniqueId()));
    }

    /**
     * Dipanggil HANYA saat akun baru pertama kali dibuat (baris database tidak ada sebelumnya).
     * Kalau PDC pemain ternyata masih menyimpan saldo dari instalasi plugin sebelumnya, pulihkan
     * itu ke database (tercatat di transaction log sebagai ADMIN, bukan diam-diam).
     */
    public void restoreFromPdcIfPresent(Player player) {
        Long stored = player.getPersistentDataContainer().get(balanceKey(), PersistentDataType.LONG);
        if (stored == null || stored <= 0) return;
        UUID id = player.getUniqueId();
        long amount = Math.max(0L, Math.min(stored, maxBalance()));
        long before = balances.get(id);
        TransactionRecord rec = new TransactionRecord(0L, id.toString(), player.getName(),
                TransactionRecord.Type.ADMIN, "RESTORE_PDC (saldo lama dari playerdata)", 0L, amount,
                before, amount, System.currentTimeMillis());
        write(id, amount, rec);
    }

    // ---------- memory only (dipakai TradeService) ----------

    public boolean withdrawMemory(UUID id, long amount) {
        if (amount < 0 || !hasBalance(id, amount)) return false;
        return balances.set(id, balances.get(id) - amount, System.currentTimeMillis());
    }

    public boolean depositMemory(UUID id, long amount) {
        if (!canDeposit(id, amount)) return false;
        return balances.set(id, balances.get(id) + amount, System.currentTimeMillis());
    }

    // ---------- API publik (persisten) ----------

    public boolean setBalance(UUID id, long amount) {
        return write(id, amount, null);
    }

    public boolean addBalance(UUID id, long amount) {
        if (!canDeposit(id, amount)) return false;
        return write(id, balances.get(id) + amount, null);
    }

    public boolean removeBalance(UUID id, long amount) {
        if (!hasBalance(id, amount)) return false;
        return write(id, balances.get(id) - amount, null);
    }

    // ---------- admin (persisten + masuk transaction log) ----------

    public boolean adminSetBalance(String actor, UUID id, long amount) {
        return admin(actor, id, "SETBALANCE", amount, amount);
    }

    public boolean adminAddBalance(String actor, UUID id, long amount) {
        if (!canDeposit(id, amount)) return false;
        return admin(actor, id, "ADDBALANCE", amount, balances.get(id) + amount);
    }

    public boolean adminRemoveBalance(String actor, UUID id, long amount) {
        if (!hasBalance(id, amount)) return false;
        return admin(actor, id, "REMOVEBALANCE", amount, balances.get(id) - amount);
    }

    private boolean admin(String actor, UUID id, String label, long amount, long newBalance) {
        long before = balances.get(id);
        TransactionRecord rec = new TransactionRecord(0L, id.toString(), balances.nameOf(id),
                TransactionRecord.Type.ADMIN, label + " oleh " + actor, 0L, amount,
                before, newBalance, System.currentTimeMillis());
        return write(id, newBalance, rec);
    }

    private boolean write(UUID id, long newBalance, TransactionRecord log) {
        if (newBalance < 0 || newBalance > maxBalance() || !balances.exists(id)) return false;
        if (!balances.set(id, newBalance, System.currentTimeMillis())) return false;
        PlayerRecord snapshot = balances.record(id);
        db.run(c -> {
            players.save(c, snapshot);
            if (log != null) transactions.insert(c, log);
        });
        Player online = plugin.getServer().getPlayer(id);
        if (online != null) syncPdc(online);
        return true;
    }
}
