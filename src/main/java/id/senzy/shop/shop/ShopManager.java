package id.senzy.shop.shop;

import id.senzy.shop.SenzyShop;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Katalog item dari items.yml. Item yang tidak valid / terlarang ditolak saat load. */
public final class ShopManager {
    private static final Set<String> BLOCKED = Set.of(
            "NETHER_QUARTZ_ORE", "NETHER_GOLD_ORE", "ANCIENT_DEBRIS", "NETHERITE_SCRAP",
            "NETHERITE_INGOT", "ELYTRA", "TOTEM_OF_UNDYING", "NETHER_STAR");

    private final SenzyShop plugin;
    private volatile Map<Material, ShopItem> items = Map.of();

    public ShopManager(SenzyShop plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "items.yml");
        if (!file.exists()) plugin.saveResource("items.yml", false);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("items");
        Map<Material, ShopItem> loaded = new LinkedHashMap<>();
        if (root == null) {
            plugin.getLogger().warning("items.yml tidak punya bagian 'items'. Toko kosong.");
            items = Collections.unmodifiableMap(loaded);
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) continue;
            ShopItem item = parse(id, section);
            if (item == null) continue;
            if (loaded.containsKey(item.material())) {
                warn(id, "material duplikat, dilewati");
                continue;
            }
            loaded.put(item.material(), item);
        }
        items = Collections.unmodifiableMap(loaded);
        plugin.getLogger().info("Memuat " + loaded.size() + " item toko.");
    }

    private ShopItem parse(String id, ConfigurationSection s) {
        String matName = s.getString("material", id).trim().toUpperCase(Locale.ROOT);
        if (matName.contains("NETHERITE") || BLOCKED.contains(matName)) {
            warn(id, "item terlarang di V1.0 (" + matName + ")");
            return null;
        }
        Material material = Material.matchMaterial(matName);
        if (material == null || !material.isItem() || material.isAir()) {
            warn(id, "material tidak dikenal: " + matName);
            return null;
        }
        ShopCategory category = ShopCategory.fromString(s.getString("category"));
        if (category == null) {
            warn(id, "category tidak valid");
            return null;
        }
        long buy = s.getLong("buy-price", 0L);
        long sell = s.getLong("sell-price", 0L);
        if (buy < 0 || sell < 0) {
            warn(id, "harga negatif");
            return null;
        }
        if (buy > 0 && sell > buy) {
            warn(id, "sell-price > buy-price (celah cetak uang), dilewati");
            return null;
        }
        var cfg = plugin.getConfig();
        ConfigurationSection st = s.getConfigurationSection("stock");
        int min = st != null ? st.getInt("min", cfg.getInt("stock.default-min", 0)) : cfg.getInt("stock.default-min", 0);
        int max = st != null ? st.getInt("max", cfg.getInt("stock.default-max", 64)) : cfg.getInt("stock.default-max", 64);
        int chance = st != null ? st.getInt("chance", cfg.getInt("stock.default-chance", 100)) : cfg.getInt("stock.default-chance", 100);
        if (min < 0 || max < min || chance < 0 || chance > 100) {
            warn(id, "konfigurasi stock tidak valid");
            return null;
        }
        Set<String> worlds = new HashSet<>(s.getStringList("worlds"));
        return new ShopItem(id.toLowerCase(Locale.ROOT), material, category, s.getBoolean("enabled", true),
                buy, sell, min, max, chance, Set.copyOf(worlds));
    }

    private void warn(String id, String reason) {
        plugin.getLogger().warning("items.yml [" + id + "]: " + reason);
    }

    public Collection<ShopItem> all() {
        return items.values();
    }

    public ShopItem get(Material material) {
        return items.get(material);
    }

    public List<ShopItem> byCategory(ShopCategory category) {
        List<ShopItem> out = new ArrayList<>();
        for (ShopItem i : items.values()) if (i.category() == category) out.add(i);
        return out;
    }

    /** Cari berdasarkan id config atau nama material (tidak peka huruf besar/kecil). */
    public ShopItem find(String input) {
        String key = input.trim().toLowerCase(Locale.ROOT);
        for (ShopItem i : items.values()) {
            if (i.id().equals(key) || i.material().name().toLowerCase(Locale.ROOT).equals(key)) return i;
        }
        return null;
    }
}
