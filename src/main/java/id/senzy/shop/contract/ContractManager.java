package id.senzy.shop.contract;

import id.senzy.shop.SenzyShop;
import id.senzy.shop.database.ContractProgressRecord;
import id.senzy.shop.database.ContractRecord;
import id.senzy.shop.database.ContractRepository;
import id.senzy.shop.database.MetaRepository;
import id.senzy.shop.database.PlayerRecord;
import id.senzy.shop.database.PlayerRepository;
import id.senzy.shop.database.DatabaseManager;
import id.senzy.shop.database.TransactionRecord;
import id.senzy.shop.database.TransactionRepository;
import id.senzy.shop.economy.EconomyManager;
import id.senzy.shop.shop.ShopItem;
import id.senzy.shop.shop.ShopManager;
import id.senzy.shop.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Kontrak (daily/weekly) berbasis waktu SISTEM tersimpan di database (sama seperti restock) -
 * tidak reset ke awal saat server restart. Definisi kontrak berlaku SATU set untuk semua pemain;
 * progres disimpan per-pemain di tabel contract_progress. Target & reward SELALU dari
 * contracts.yml - hanya kontrak MANA yang tampil setiap reset yang diacak.
 */
public final class ContractManager {
    private final SenzyShop plugin;
    private final ShopManager shop;
    private final EconomyManager economy;
    private final DatabaseManager db;
    private final ContractRepository repo;
    private final MetaRepository meta;
    private final PlayerRepository players;
    private final TransactionRepository transactions;
    private final MessageUtil msg;
    private final ContractGenerator generator = new ContractGenerator();

    private final Map<ContractType, List<ContractRecord>> active = new EnumMap<>(ContractType.class);
    private final Map<ContractType, Long> nextReset = new EnumMap<>(ContractType.class);
    private final Map<ContractType, Long> generationId = new EnumMap<>(ContractType.class);
    private final Map<UUID, Map<Long, ContractProgressRecord>> progressCache = new HashMap<>();

    private BukkitTask task;

    public ContractManager(SenzyShop plugin, ShopManager shop, EconomyManager economy, DatabaseManager db,
                           ContractRepository repo, MetaRepository meta, PlayerRepository players,
                           TransactionRepository transactions, MessageUtil msg) {
        this.plugin = plugin;
        this.shop = shop;
        this.economy = economy;
        this.db = db;
        this.repo = repo;
        this.meta = meta;
        this.players = players;
        this.transactions = transactions;
        this.msg = msg;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("contracts.enabled", true)) return;
        Map<String, String> loaded = db.supply(meta::loadAll).join();
        long now = System.currentTimeMillis();
        for (ContractType type : ContractType.values()) {
            generationId.put(type, parse(loaded.get(type.metaGenerationKey())));
            long reset = parse(loaded.get(type.metaResetKey()));
            List<ContractRecord> rows = db.supply(c -> repo.loadActive(c, type.name(), now)).join();
            if (rows.isEmpty() || reset <= now) {
                generate(type, now);
            } else {
                active.put(type, rows);
                nextReset.put(type, reset);
            }
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L * 30, 20L * 30);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (ContractType type : ContractType.values()) {
            Long reset = nextReset.get(type);
            if (reset != null && now >= reset) generate(type, now);
        }
    }

    // ------------------------------------------------------------------ generate

    private void generate(ContractType type, long now) {
        List<ContractPoolEntry> pool = loadPool(type);
        int count = plugin.getConfig().getInt("contracts." + type.configKey() + ".random-count", type == ContractType.DAILY ? 3 : 2);
        long durationMillis = type == ContractType.DAILY
                ? Math.max(1L, plugin.getConfig().getLong("contracts.daily.reset-hours", 24L)) * 3_600_000L
                : Math.max(1L, plugin.getConfig().getLong("contracts.weekly.reset-days", 7L)) * 86_400_000L;
        long expiresAt = now + durationMillis;
        long genId = generationId.getOrDefault(type, 0L) + 1;
        List<ContractPoolEntry> chosen = generator.pick(pool, count);

        List<ContractRecord> created = db.supply(c -> {
            List<ContractRecord> list = new ArrayList<>();
            for (ContractPoolEntry entry : chosen) {
                ContractRecord draft = new ContractRecord(0L, entry.key(), type.name(), entry.itemId(),
                        entry.amount(), entry.reward(), genId, expiresAt);
                long newId = repo.insertContract(c, draft);
                list.add(new ContractRecord(newId, entry.key(), type.name(), entry.itemId(),
                        entry.amount(), entry.reward(), genId, expiresAt));
            }
            meta.save(c, type.metaResetKey(), Long.toString(expiresAt));
            meta.save(c, type.metaGenerationKey(), Long.toString(genId));
            return list;
        }).join();

        active.put(type, created);
        nextReset.put(type, expiresAt);
        generationId.put(type, genId);
        // Kontrak baru: progres lama (generasi sebelumnya) otomatis tidak relevan lagi karena
        // GUI/recordSell hanya mencocokkan terhadap daftar 'active' yang sekarang.
    }

    private List<ContractPoolEntry> loadPool(ContractType type) {
        File file = new File(plugin.getDataFolder(), "contracts.yml");
        if (!file.exists()) plugin.saveResource("contracts.yml", false);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection poolSection = yaml.getConfigurationSection(type.configKey() + ".pool");
        List<ContractPoolEntry> out = new ArrayList<>();
        if (poolSection == null) {
            // Bisa berarti dua hal: section memang tidak ditulis di contracts.yml, ATAU seluruh
            // file gagal di-parse (YAML rusak - YamlConfiguration diam-diam mengembalikan config
            // kosong tanpa melempar exception ke sini). Beri warning eksplisit supaya kelihatan
            // di console, bukan cuma "0 kontrak" tanpa penjelasan.
            plugin.getLogger().warning("contracts.yml: section '" + type.configKey() + ".pool' tidak "
                    + "ditemukan - cek apakah contracts.yml valid (YAML rusak akan membuat SEMUA "
                    + "kontrak kosong tanpa error lain). Tidak ada kontrak " + type.configKey()
                    + " yang akan tersedia sampai ini diperbaiki.");
            return out;
        }
        for (String key : poolSection.getKeys(false)) {
            ConfigurationSection e = poolSection.getConfigurationSection(key);
            if (e == null) continue;
            String itemId = e.getString("item", "").trim().toLowerCase(Locale.ROOT);
            long amount = e.getLong("amount", 0L);
            long reward = e.getLong("reward", 0L);
            ShopItem item = shop.getById(itemId);
            if (item == null || !item.canSell()) {
                plugin.getLogger().warning("contracts.yml [" + type.configKey() + ".pool." + key
                        + "]: item '" + itemId + "' tidak ada / tidak bisa dijual, dilewati");
                continue;
            }
            if (amount <= 0 || reward < 0) {
                plugin.getLogger().warning("contracts.yml [" + type.configKey() + ".pool." + key + "]: amount/reward tidak valid");
                continue;
            }
            out.add(new ContractPoolEntry(key, itemId, amount, reward));
        }
        return out;
    }

    // ------------------------------------------------------------------ query

    public List<ContractRecord> active(ContractType type) {
        return active.getOrDefault(type, List.of());
    }

    public long remainingMillis(ContractType type) {
        Long reset = nextReset.get(type);
        return reset == null ? 0L : Math.max(0L, reset - System.currentTimeMillis());
    }

    private ContractRecord findActive(long contractId) {
        for (List<ContractRecord> list : active.values()) {
            for (ContractRecord c : list) if (c.id() == contractId) return c;
        }
        return null;
    }

    public void ensurePlayerLoaded(UUID uuid) {
        if (progressCache.containsKey(uuid)) return;
        List<ContractProgressRecord> rows = db.supply(c -> repo.loadProgressForPlayer(c, uuid)).join();
        Map<Long, ContractProgressRecord> map = new HashMap<>();
        for (ContractProgressRecord r : rows) map.put(r.contractId(), r);
        progressCache.put(uuid, map);
    }

    public ContractProgressRecord progressFor(UUID uuid, long contractId) {
        ensurePlayerLoaded(uuid);
        return progressCache.getOrDefault(uuid, Map.of()).get(contractId);
    }

    public void unloadPlayer(UUID uuid) {
        progressCache.remove(uuid);
    }

    // ------------------------------------------------------------------ progress & claim

    /** Dipanggil TradeService setelah SELL benar-benar berhasil (bukan saat BUY / item hilang). */
    public void recordSell(Player player, ShopItem item, int amountSold) {
        if (amountSold <= 0) return;
        UUID uuid = player.getUniqueId();
        ensurePlayerLoaded(uuid);
        Map<Long, ContractProgressRecord> cache = progressCache.computeIfAbsent(uuid, k -> new HashMap<>());
        long now = System.currentTimeMillis();
        List<ContractProgressRecord> toSave = new ArrayList<>();

        for (List<ContractRecord> list : active.values()) {
            for (ContractRecord contract : list) {
                if (!contract.target().equalsIgnoreCase(item.id())) continue;
                ContractProgressRecord existing = cache.get(contract.id());
                if (existing != null && existing.completed()) continue;
                long current = existing != null ? existing.progress() : 0L;
                long updatedAmount = Math.min(contract.requiredAmount(), current + amountSold);
                boolean completed = updatedAmount >= contract.requiredAmount();
                ContractProgressRecord updated = new ContractProgressRecord(uuid, contract.id(), updatedAmount,
                        completed, existing != null && existing.claimed(), now);
                cache.put(contract.id(), updated);
                toSave.add(updated);
            }
        }
        if (!toSave.isEmpty()) {
            db.run(c -> { for (ContractProgressRecord p : toSave) repo.saveProgress(c, p); });
        }
    }

    public boolean claim(Player player, long contractId) {
        UUID uuid = player.getUniqueId();
        ensurePlayerLoaded(uuid);
        ContractRecord contract = findActive(contractId);
        if (contract == null) {
            msg.send(player, "contract.not-found");
            return false;
        }
        ContractProgressRecord progress = progressCache.getOrDefault(uuid, Map.of()).get(contractId);
        if (progress == null || !progress.completed()) {
            msg.send(player, "contract.not-completed");
            return false;
        }
        if (progress.claimed()) {
            msg.send(player, "contract.already-claimed");
            return false;
        }
        if (!economy.canDeposit(uuid, contract.reward())) {
            msg.send(player, "trade.balance-limit");
            return false;
        }
        long before = economy.getBalance(uuid);
        if (!economy.depositMemory(uuid, contract.reward())) {
            msg.send(player, "trade.invalid");
            return false;
        }
        long after = economy.getBalance(uuid);
        long now = System.currentTimeMillis();
        ContractProgressRecord updated = new ContractProgressRecord(uuid, contractId, progress.progress(), true, true, now);
        progressCache.computeIfAbsent(uuid, k -> new HashMap<>()).put(contractId, updated);

        TransactionRecord rec = new TransactionRecord(0L, uuid.toString(), player.getName(),
                TransactionRecord.Type.CONTRACT_REWARD, contract.target(), contract.requiredAmount(),
                contract.reward(), before, after, now);
        PlayerRecord playerSnapshot = economy.record(uuid);
        db.run(c -> {
            players.save(c, playerSnapshot);
            repo.saveProgress(c, updated);
            transactions.insert(c, rec);
        });
        msg.send(player, "contract.claimed", "reward", msg.money(contract.reward()));
        return true;
    }

    private static long parse(String value) {
        if (value == null) return 0L;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
