package id.senzy.shop.database;

import java.util.UUID;

/** Snapshot immutable satu baris tabel players. */
public record PlayerRecord(UUID uuid, String name, long balance, long createdAt, long updatedAt) {}
