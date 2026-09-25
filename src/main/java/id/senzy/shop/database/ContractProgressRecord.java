package id.senzy.shop.database;

import java.util.UUID;

/** Satu baris tabel contract_progress: progres SATU pemain terhadap SATU contract. */
public record ContractProgressRecord(UUID uuid, long contractId, long progress, boolean completed,
                                     boolean claimed, long updatedAt) {
}
