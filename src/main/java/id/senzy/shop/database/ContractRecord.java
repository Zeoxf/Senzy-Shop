package id.senzy.shop.database;

/** Satu baris tabel contracts: definisi kontrak yang berlaku untuk SEMUA pemain (server-wide). */
public record ContractRecord(long id, String contractKey, String type, String target,
                             long requiredAmount, long reward, long generationId, long expiresAt) {
}
