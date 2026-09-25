package id.senzy.shop.restock;

import id.senzy.shop.SenzyShop;
import id.senzy.shop.shop.ShopItem;
import org.bukkit.Material;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Hanya mengacak KETERSEDIAAN dan JUMLAH stok. Harga tidak pernah diacak. */
public final class RestockGenerator {
    private final SenzyShop plugin;

    public RestockGenerator(SenzyShop plugin) {
        this.plugin = plugin;
    }

    public Map<Material, Integer> generate(Collection<ShopItem> items) {
        boolean randomEnabled = plugin.getConfig().getBoolean("stock.enabled", true);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Map<Material, Integer> out = new HashMap<>();
        for (ShopItem item : items) {
            int amount = 0;
            if (item.enabled()) {
                if (!randomEnabled) {
                    amount = item.stockMax();
                } else if (random.nextDouble() < item.stockChance()) {
                    amount = item.stockMin() >= item.stockMax()
                            ? item.stockMin()
                            : random.nextInt(item.stockMin(), item.stockMax() + 1);
                }
            }
            out.put(item.material(), amount);
        }
        return out;
    }
}
