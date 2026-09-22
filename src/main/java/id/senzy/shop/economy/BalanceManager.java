package id.senzy.shop.economy;

import id.senzy.shop.database.PlayerRecord;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Cache akun pemain di memori (thread-safe lewat synchronized). Tidak menyentuh database. */
public final class BalanceManager {
    private final Map<UUID, PlayerRecord> accounts = new HashMap<>();
    private final Map<String, UUID> names = new HashMap<>();

    public synchronized void load(List<PlayerRecord> rows) {
        accounts.clear();
        names.clear();
        for (PlayerRecord r : rows) {
            accounts.put(r.uuid(), r);
            names.put(r.name().toLowerCase(Locale.ROOT), r.uuid());
        }
    }

    public synchronized boolean exists(UUID id) {
        return accounts.containsKey(id);
    }

    public synchronized long get(UUID id) {
        PlayerRecord r = accounts.get(id);
        return r == null ? 0L : r.balance();
    }

    public synchronized PlayerRecord record(UUID id) {
        return accounts.get(id);
    }

    public synchronized String nameOf(UUID id) {
        PlayerRecord r = accounts.get(id);
        return r == null ? id.toString() : r.name();
    }

    public synchronized UUID findByName(String name) {
        return names.get(name.toLowerCase(Locale.ROOT));
    }

    public synchronized List<String> names() {
        return accounts.values().stream().map(PlayerRecord::name).sorted().toList();
    }

    /** Ubah balance di memori. Menolak nilai negatif atau akun yang tidak ada. */
    public synchronized boolean set(UUID id, long balance, long now) {
        PlayerRecord r = accounts.get(id);
        if (r == null || balance < 0) return false;
        accounts.put(id, new PlayerRecord(id, r.name(), balance, r.createdAt(), now));
        return true;
    }

    /** Membuat akun baru / memperbarui nama. Mengembalikan record yang perlu disimpan, atau null. */
    public synchronized PlayerRecord ensure(UUID id, String name, long starting, long now) {
        PlayerRecord r = accounts.get(id);
        if (r == null) {
            PlayerRecord created = new PlayerRecord(id, name, starting, now, now);
            accounts.put(id, created);
            names.put(name.toLowerCase(Locale.ROOT), id);
            return created;
        }
        if (!r.name().equals(name)) {
            names.remove(r.name().toLowerCase(Locale.ROOT));
            PlayerRecord renamed = new PlayerRecord(id, name, r.balance(), r.createdAt(), now);
            accounts.put(id, renamed);
            names.put(name.toLowerCase(Locale.ROOT), id);
            return renamed;
        }
        return null;
    }
}
