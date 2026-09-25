package id.senzy.shop.shop;

import org.bukkit.Material;
import java.util.Set;

public record ShopItem(String id, Material material, ShopCategory category, boolean enabled,
                       String displayName, int slot, long buyPrice, long sellPrice, int stockMin, int stockMax,
                       double stockChance, Set<String> worlds, PriceMode priceMode, String oreReferenceId,
                       double multiplier, double sellRatio) {
    public boolean canBuy() { return enabled && buyPrice > 0; }
    public boolean canSell() { return enabled && sellPrice > 0; }
    public boolean allowedIn(String worldName) { return worlds.isEmpty() || worlds.contains(worldName); }
}
