package id.senzy.shop.shop;

import org.bukkit.Material;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * V1.2 registry facade untuk katalog item.
 * ShopManager tetap menjadi sumber konfigurasi; registry ini menyediakan API registry
 * yang dapat dipakai modul lain tanpa mengetahui detail YAML.
 */
public final class ItemRegistry {
    private final Map<String, ShopItem> byId = new LinkedHashMap<>();
    private final Map<Material, ShopItem> byMaterial = new LinkedHashMap<>();

    public void replace(Collection<ShopItem> items) {
        byId.clear(); byMaterial.clear();
        for (ShopItem item : items) {
            byId.put(item.id().toLowerCase(Locale.ROOT), item);
            byMaterial.put(item.material(), item);
        }
    }

    public ShopItem get(String id) {
        return id == null ? null : byId.get(id.toLowerCase(Locale.ROOT));
    }

    public ShopItem get(Material material) { return byMaterial.get(material); }
    public Collection<ShopItem> all() { return Collections.unmodifiableCollection(byId.values()); }

    public static Material validateMaterial(String raw) {
        if (raw == null || raw.isBlank()) return null;
        Material m = Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
        return m != null && m.isItem() && !m.isAir() ? m : null;
    }
}
