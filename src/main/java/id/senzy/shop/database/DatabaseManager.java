package id.senzy.shop.database;

import id.senzy.shop.SenzyShop;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Satu koneksi SQLite + satu thread khusus. Semua akses database lewat {@link #supply}/{@link #run},
 * sehingga tidak pernah ada dua query berjalan bersamaan dan tidak ada query di main thread.
 * Setiap task dibungkus satu transaksi SQL (commit atau rollback penuh).
 */
public final class DatabaseManager {

    @FunctionalInterface
    public interface SqlFunction<T> { T apply(Connection connection) throws SQLException; }

    @FunctionalInterface
    public interface SqlAction { void run(Connection connection) throws SQLException; }

    private final SenzyShop plugin;
    private Connection connection;
    private ExecutorService executor;

    public DatabaseManager(SenzyShop plugin) {
        this.plugin = plugin;
    }

    public void connect() throws SQLException {
        File cfgFile = new File(plugin.getDataFolder(), "database.yml");
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(cfgFile);
        String fileName = cfg.getString("sqlite.file", "senzyshop.db");
        int busyTimeout = cfg.getInt("sqlite.busy-timeout-ms", 5000);
        boolean wal = cfg.getBoolean("sqlite.wal", true);

        File dbFile = new File(plugin.getDataFolder(), fileName);
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("Driver SQLite tidak ditemukan di server", e);
        }
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA busy_timeout=" + busyTimeout);
            if (wal) st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA synchronous=FULL");
        }
        createTables();
        executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "SenzyShop-DB"));
    }

    private void createTables() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS players (
                        uuid TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        balance INTEGER NOT NULL DEFAULT 0 CHECK (balance >= 0),
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )""");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS transactions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        uuid TEXT NOT NULL,
                        player_name TEXT NOT NULL,
                        type TEXT NOT NULL,
                        material TEXT,
                        amount INTEGER NOT NULL,
                        price INTEGER NOT NULL,
                        balance_before INTEGER NOT NULL,
                        balance_after INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL
                    )""");
            st.execute("CREATE INDEX IF NOT EXISTS idx_transactions_time ON transactions(timestamp)");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS shop_stock (
                        material TEXT PRIMARY KEY,
                        category TEXT NOT NULL,
                        stock INTEGER NOT NULL CHECK (stock >= 0),
                        max_stock INTEGER NOT NULL,
                        buy_price INTEGER NOT NULL,
                        sell_price INTEGER NOT NULL,
                        restock_id INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )""");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS meta (
                        meta_key TEXT PRIMARY KEY,
                        meta_value TEXT NOT NULL
                    )""");
        }
    }

    public <T> CompletableFuture<T> supply(SqlFunction<T> function) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                connection.setAutoCommit(false);
                T result = function.apply(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                try {
                    connection.rollback();
                } catch (SQLException ignored) {
                    // rollback best-effort
                }
                plugin.getLogger().log(Level.SEVERE, "Database error (transaksi di-rollback)", e);
                throw new CompletionException(e);
            }
        }, executor);
    }

    public CompletableFuture<Void> run(SqlAction action) {
        return supply(c -> {
            action.run(c);
            return null;
        });
    }

    /** Menunggu semua task tertulis lalu menutup koneksi. Dipanggil saat plugin disable. */
    public void close() {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    plugin.getLogger().warning("Antrian database belum selesai setelah 30 detik.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // sudah ditutup
            }
        }
    }
}
