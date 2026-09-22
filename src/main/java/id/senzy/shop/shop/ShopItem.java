package id.senzy.shop.shop;

import org.bukkit.Material;

import java.util.Set;

/**
 * Definisi satu item toko (immutable) dari items.yml. buyPrice/sellPrice SELALU nilai akhir yang
 * sudah dihitung (untuk item MATERIAL bermode ORE_MULTIPLIER, dihitung dari harga ore acuan saat
 * items.yml dimuat) - kode lain (GUI, TradeService) tidak perlu tahu bagaimana harga itu didapat.
 * priceMode/oreReferenceId/multiplier/sellRatio disimpan hanya untuk keperluan tampilan admin
 * dan supaya "/senzy admin setprice" tahu item ini awalnya dihitung dari mana.
 */
public record ShopItem(String id, Material material, ShopCategory category, boolean enabled,
                       long buyPrice, long sellPrice, int stockMin, int stockMax, int stockChance,
                       Set<String> worlds, PriceMode priceMode, String oreReferenceId,
                       double multiplier, double sellRatio) {

    public boolean canBuy() {
        return enabled && buyPrice > 0;
    }

    public boolean canSell() {
        return enabled && sellPrice > 0;
    }

    /** worlds kosong = semua world diizinkan. */
    public boolean allowedIn(String worldName) {
        return worlds.isEmpty() || worlds.contains(worldName);
    }
}
