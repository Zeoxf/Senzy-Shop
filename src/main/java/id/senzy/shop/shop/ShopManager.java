package id.senzy.shop.shop;

import id.senzy.shop.SenzyShop;
import id.senzy.shop.util.ItemUtil;
import id.senzy.shop.util.NumberUtil;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Katalog item dari items.yml. Item yang tidak valid / terlarang ditolak saat load.
 *
 * Dua tahap parsing: item biasa (FIXED price) diproses duluan, lalu item MATERIAL bermode
 * ORE_MULTIPLIER dihitung belakangan berdasarkan harga item ore acuan yang sudah diproses -
 * jadi urutan penulisan di items.yml bebas (ore boleh ditulis sebelum atau sesudah material-nya).
 */
public final class ShopManager {
    private static final Set<String> BLOCKED = Set.of(
            "NETHER_QUARTZ_ORE", "NETHER_GOLD_ORE", "ANCIENT_DEBRIS", "NETHERITE_SCRAP",
            "NETHERITE_INGOT", "ELYTRA", "TOTEM_OF_UNDYING", "NETHER_STAR");

    private final SenzyShop plugin;
    private volatile Map<Material, ShopItem> byMaterial = Map.of();
    private volatile Map<String, ShopItem> byId = Map.of();

    public ShopManager(SenzyShop plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "items.yml");
        if (!file.exists()) plugin.saveResource("items.yml", false);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("items");
        Map<String, ShopItem> resolved = new LinkedHashMap<>();
        if (root == null) {
            plugin.getLogger().warning("items.yml tidak punya bagian 'items'. Toko kosong.");
            apply(resolved);
            return;
        }

        List<String> deferred = new ArrayList<>();
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) continue;
            if (isDeferredMaterial(section)) {
                deferred.add(id);
                continue;
            }
            ShopItem item = parseFixed(id, section);
            if (item != null) put(resolved, item);
        }
        for (String id : deferred) {
            ConfigurationSection section = root.getConfigurationSection(id);
            ShopItem item = parseOreMultiplier(id, section, resolved);
            if (item != null) put(resolved, item);
        }

        apply(resolved);
        plugin.getLogger().info("Memuat " + resolved.size() + " item toko.");
    }

    private void apply(Map<String, ShopItem> resolved) {
        Map<Material, ShopItem> materialMap = new LinkedHashMap<>();
        for (ShopItem item : resolved.values()) materialMap.put(item.material(), item);
        byMaterial = Collections.unmodifiableMap(materialMap);
        byId = Collections.unmodifiableMap(resolved);
    }

    private void put(Map<String, ShopItem> resolved, ShopItem item) {
        if (resolved.containsKey(item.id())) {
            warn(item.id(), "id konfigurasi duplikat, dilewati");
            return;
        }
        for (ShopItem existing : resolved.values()) {
            if (existing.material() == item.material()) {
                warn(item.id(), "material duplikat (dipakai oleh '" + existing.id() + "'), dilewati");
                return;
            }
        }
        resolved.put(item.id(), item);
    }

    private boolean isDeferredMaterial(ConfigurationSection s) {
        ShopCategory category = ShopCategory.fromString(s.getString("category"));
        if (category != ShopCategory.MATERIAL) return false;
        PriceMode mode = PriceMode.fromString(s.getString("price-mode"), PriceMode.ORE_MULTIPLIER);
        return mode == PriceMode.ORE_MULTIPLIER;
    }

    /** Item dengan harga statis langsung dari config (semua kategori non-material + material FIXED). */
    private ShopItem parseFixed(String id, ConfigurationSection s) {
        Common common = parseCommon(id, s);
        if (common == null) return null;
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
        return new ShopItem(common.id(), common.material(), common.category(), common.enabled(),
                buy, sell, common.min(), common.max(), common.chance(), common.worlds(),
                PriceMode.FIXED, null, 0.0, 0.0);
    }

    /** Item MATERIAL bermode ORE_MULTIPLIER: harga dihitung dari item ore acuan yang sudah di-resolve. */
    private ShopItem parseOreMultiplier(String id, ConfigurationSection s, Map<String, ShopItem> resolvedSoFar) {
        Common common = parseCommon(id, s);
        if (common == null) return null;
        String oreRef = s.getString("ore-reference");
        if (oreRef == null || oreRef.isBlank()) {
            warn(id, "price-mode ORE_MULTIPLIER tapi 'ore-reference' kosong");
            return null;
        }
        ShopItem ore = resolvedSoFar.get(oreRef.trim().toLowerCase(Locale.ROOT));
        if (ore == null) {
            warn(id, "ore-reference '" + oreRef + "' tidak ditemukan (pastikan item ore itu valid & bukan material lain)");
            return null;
        }
        if (ore.buyPrice() <= 0) {
            warn(id, "ore-reference '" + oreRef + "' tidak punya buy-price, tidak bisa dihitung");
            return null;
        }
        double multiplier = s.getDouble("multiplier", plugin.getConfig().getDouble("economy.material-price-multiplier", 1.5));
        double sellRatio = s.getDouble("sell-ratio", plugin.getConfig().getDouble("economy.default-sell-ratio", 0.5));
        if (multiplier <= 0 || sellRatio < 0 || sellRatio > 1) {
            warn(id, "multiplier/sell-ratio tidak valid");
            return null;
        }
        long buy = NumberUtil.scale(ore.buyPrice(), multiplier);
        long sell = NumberUtil.ratio(buy, sellRatio);
        return new ShopItem(common.id(), common.material(), common.category(), common.enabled(),
                buy, sell, common.min(), common.max(), common.chance(), common.worlds(),
                PriceMode.ORE_MULTIPLIER, ore.id(), multiplier, sellRatio);
    }

    private record Common(String id, Material material, ShopCategory category, boolean enabled,
                          int min, int max, int chance, Set<String> worlds) {}

    private Common parseCommon(String id, ConfigurationSection s) {
        String matName = s.getString("material", id).trim().toUpperCase(Locale.ROOT);
        if (matName.contains("NETHERITE") || BLOCKED.contains(matName)) {
            warn(id, "item terlarang (" + matName + ")");
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
        var cfg = plugin.getConfig();
        ConfigurationSection st = s.getConfigurationSection("stock");
        int min = st != null ? st.getInt("min", cfg.getInt("stock.default-min", 0)) : cfg.getInt("stock.default-min", 0);
        int max = st != null ? st.getInt("max", cfg.getInt("stock.default-max", 64)) : cfg.getInt("stock.default-max", 64);
        int chance = st != null ? st.getInt("chance", cfg.getInt("stock.default-chance", 100)) : cfg.getInt("stock.default-chance", 100);
        if (min < 0 || max < min || chance < 0 || chance > 100) {
            warn(id, "konfigurasi stock tidak valid");
            return null;
        }
        Set<String> worlds = Set.copyOf(new HashSet<>(s.getStringList("worlds")));
        return new Common(id.toLowerCase(Locale.ROOT), material, category, s.getBoolean("enabled", true),
                min, max, chance, worlds);
    }

    private void warn(String id, String reason) {
        plugin.getLogger().warning("items.yml [" + id + "]: " + reason);
    }

    public Collection<ShopItem> all() {
        return byMaterial.values();
    }

    public ShopItem get(Material material) {
        return byMaterial.get(material);
    }

    public ShopItem getById(String id) {
        return id == null ? null : byId.get(id.toLowerCase(Locale.ROOT));
    }

    public List<ShopItem> byCategory(ShopCategory category) {
        List<ShopItem> out = new ArrayList<>();
        for (ShopItem i : byMaterial.values()) if (i.category() == category) out.add(i);
        return out;
    }

    /** Cari berdasarkan id config atau nama material (tidak peka huruf besar/kecil). */
    public ShopItem find(String input) {
        if (input == null) return null;
        String key = input.trim().toLowerCase(Locale.ROOT);
        ShopItem byIdMatch = byId.get(key);
        if (byIdMatch != null) return byIdMatch;
        for (ShopItem i : byMaterial.values()) {
            if (i.material().name().toLowerCase(Locale.ROOT).equals(key)) return i;
        }
        return null;
    }

    /** Pencarian bebas dependency eksternal: cocokkan id config atau nama tampilan material. */
    public List<ShopItem> search(String query) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<ShopItem> out = new ArrayList<>();
        if (needle.isEmpty()) return out;
        for (ShopItem i : byMaterial.values()) {
            if (!i.enabled()) continue;
            String name = ItemUtil.prettyName(i.material()).toLowerCase(Locale.ROOT);
            if (i.id().contains(needle) || name.contains(needle)) out.add(i);
        }
        out.sort((a, b) -> ItemUtil.prettyName(a.material()).compareToIgnoreCase(ItemUtil.prettyName(b.material())));
        return out;
    }

    /**
     * Menulis harga baru LANGSUNG ke items.yml (dipakai /senzy admin setprice) lalu me-reload
     * katalog di memori. Item MATERIAL bermode ORE_MULTIPLIER otomatis dikonversi ke FIXED,
     * karena begitu admin menetapkan harga manual, item itu tidak lagi mengikuti harga ore.
     */
    public boolean setPrice(String itemId, boolean isBuyPrice, long price) {
        ShopItem current = getById(itemId);
        if (current == null || price < 0) return false;
        File file = new File(plugin.getDataFolder(), "items.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("items." + current.id());
        if (section == null) return false;

        long newBuy = isBuyPrice ? price : current.buyPrice();
        long newSell = isBuyPrice ? current.sellPrice() : price;
        if (newBuy > 0 && newSell > newBuy) return false;   // cegah celah cetak uang lewat setprice

        section.set("price-mode", "FIXED");
        section.set("buy-price", newBuy);
        section.set("sell-price", newSell);
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Gagal menyimpan items.yml", e);
            return false;
        }
        load();
        return true;
    }
}
