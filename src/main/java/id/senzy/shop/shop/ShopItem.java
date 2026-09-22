package id.senzy.shop.shop;

import org.bukkit.Material;

import java.util.Set;

/** Definisi satu item toko (immutable) dari items.yml. Harga statis. */
public record ShopItem(String id, Material material, ShopCategory category, boolean enabled,
                       long buyPrice, long sellPrice, int stockMin, int stockMax, int stockChance,
                       Set<String> worlds) {

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
