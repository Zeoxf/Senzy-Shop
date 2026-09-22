package id.senzy.shop.restock;

import id.senzy.shop.SenzyShop;
import id.senzy.shop.database.DatabaseManager;
import id.senzy.shop.database.StockRecord;
import id.senzy.shop.database.StockRepository;
import id.senzy.shop.shop.ShopManager;
import id.senzy.shop.shop.StockManager;
import id.senzy.shop.util.MessageUtil;
import id.senzy.shop.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;

/**
 * Restock berbasis waktu SISTEM yang disimpan di database (tabel meta), bukan counter di RAM.
 * Setelah restart, next_restock dibaca ulang sehingga countdown melanjutkan sisa waktu.
 */
public final class RestockManager {
    private final SenzyShop plugin;
    private final ShopManager shop;
    private final StockManager stock;
    private final DatabaseManager db;
    private final StockRepository repo;
    private final MessageUtil msg;
    private final RestockGenerator generator;

    private long lastRestock;
    private long nextRestock;
    private long restockId;
    private BukkitTask task;
    private Runnable onTick = () -> { };
    private Runnable onRestock = () -> { };

    public RestockManager(SenzyShop plugin, ShopManager shop, StockManager stock, DatabaseManager db,
                          StockRepository repo, MessageUtil msg) {
        this.plugin = plugin;
        this.shop = shop;
        this.stock = stock;
        this.db = db;
        this.repo = repo;
        this.msg = msg;
        this.generator = new RestockGenerator(plugin);
    }

    /** onTick dipanggil tiap detik (update jam GUI); onRestock dipanggil setelah restock (refresh GUI). */
    public void setHooks(Runnable onTick, Runnable onRestock) {
        this.onTick = onTick;
        this.onRestock = onRestock;
    }

    public void start() {
        Map<String, String> meta = db.supply(repo::loadMeta).join();
        lastRestock = parse(meta.get("last_restock"));
        nextRestock = parse(meta.get("next_restock"));
        restockId = parse(meta.get("restock_id"));

        long now = System.currentTimeMillis();
        if (nextRestock <= 0L || nextRestock <= now) {
            restockInternal(false);                 // pertama kali / waktu restock terlewat saat server mati
        } else if (nextRestock > now + intervalMillis()) {
            nextRestock = now + intervalMillis();   // jam sistem mundur / interval diperkecil
            persistMeta();
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    public void onConfigReload() {
        long now = System.currentTimeMillis();
        if (nextRestock > now + intervalMillis()) {
            nextRestock = now + intervalMillis();
            persistMeta();
        }
    }

    public long remainingMillis() {
        return Math.max(0L, nextRestock - System.currentTimeMillis());
    }

    public long restockId() {
        return restockId;
    }

    /** Restock paksa (admin). Timer direset ke interval penuh. */
    public void restockNow() {
        restockInternal(true);
    }

    private void tick() {
        if (System.currentTimeMillis() >= nextRestock) restockInternal(true);
        else onTick.run();
    }

    private long intervalMinutes() {
        return Math.max(1L, plugin.getConfig().getLong("restock.interval-minutes", 180L));
    }

    private long intervalMillis() {
        return intervalMinutes() * 60_000L;
    }

    private void restockInternal(boolean announce) {
        long now = System.currentTimeMillis();
        restockId++;
        lastRestock = now;
        nextRestock = now + intervalMillis();
        stock.applyRestock(generator.generate(shop.all()), restockId, now);

        List<StockRecord> rows = stock.snapshotAll();
        long last = lastRestock;
        long next = nextRestock;
        long id = restockId;
        db.run(c -> {
            for (StockRecord r : rows) repo.save(c, r);
            repo.saveMeta(c, "last_restock", Long.toString(last));
            repo.saveMeta(c, "next_restock", Long.toString(next));
            repo.saveMeta(c, "restock_id", Long.toString(id));
        });

        if (announce) announce();
        onRestock.run();
    }

    private void persistMeta() {
        long last = lastRestock;
        long next = nextRestock;
        long id = restockId;
        db.run(c -> {
            repo.saveMeta(c, "last_restock", Long.toString(last));
            repo.saveMeta(c, "next_restock", Long.toString(next));
            repo.saveMeta(c, "restock_id", Long.toString(id));
        });
    }

    private void announce() {
        String interval = TimeUtil.formatInterval(intervalMinutes());
        String sound = plugin.getConfig().getString("restock.sound", "");
        float volume = (float) plugin.getConfig().getDouble("restock.sound-volume", 0.8);
        float pitch = (float) plugin.getConfig().getDouble("restock.sound-pitch", 1.2);
        for (Player p : Bukkit.getOnlinePlayers()) {
            msg.send(p, "restock.announce", "interval", interval);
            if (sound != null && !sound.isBlank()) p.playSound(p.getLocation(), sound, volume, pitch);
        }
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
