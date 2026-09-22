package id.senzy.shop.contract;

/** Satu kandidat kontrak dari contracts.yml (belum tentu dipilih saat generate). */
public record ContractPoolEntry(String key, String itemId, long amount, long reward) {
}
