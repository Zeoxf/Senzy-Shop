package id.senzy.shop.shop;

import id.senzy.shop.SenzyShop;
import id.senzy.shop.util.ItemUtil;
import id.senzy.shop.util.NumberUtil;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * Loader marketplace.
 *
 * V1.3+ membaca struktur:
 * plugins/SenzyShop/shop/<shop>/message.yml
 * plugins/SenzyShop/shop/<shop>/<category>.yml
 *
 * items.yml lama tetap didukung sebagai fallback agar update tidak memutus server lama.
 */
public final class ShopManager {
    private static final Set<String> BLOCKED = Set.of(
            "COMMAND_BLOCK", "CHAIN_COMMAND_BLOCK", "REPEATING_COMMAND_BLOCK", "COMMAND_BLOCK_MINECART",
            "STRUCTURE_BLOCK", "STRUCTURE_VOID", "BARRIER", "LIGHT", "JIGSAW", "DEBUG_STICK", "KNOWLEDGE_BOOK");

    private final SenzyShop plugin;
    private volatile Map<Material, ShopItem> byMaterial = Map.of();
    private volatile Map<String, ShopItem> byId = Map.of();
    private volatile Map<String, ShopCategory> categories = Map.of();
    private volatile String activeShop;

    public ShopManager(SenzyShop plugin) { this.plugin = plugin; }

    public void load() {
        String configured = plugin.getConfig().getString("default-shop", "senzyshop");
        File shopRoot = new File(plugin.getDataFolder(), "shop");
        File activeDir = new File(shopRoot, safeName(configured));
        if (new File(activeDir, "message.yml").exists()) {
            loadCustom(configured, activeDir);
        } else {
            try {
                ensureBundledDefaultShop(configured);
                activeDir = new File(shopRoot, safeName(configured));
                if (new File(activeDir, "message.yml").exists()) loadCustom(configured, activeDir);
                else loadLegacy();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Gagal membuat custom shop; fallback ke items.yml lama.", e);
                loadLegacy();
            }
        }
    }

    private void ensureBundledDefaultShop(String shopName) throws IOException {
        File dir = new File(plugin.getDataFolder(), "shop/" + safeName(shopName));
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Tidak bisa membuat " + dir);
        String[] files = {"message.yml","blocks.yml","items.yml","sword.yml","armor.yml","food.yml","farming.yml","ores.yml","materials.yml","mobs.yml","tools.yml","utility.yml","rare.yml"};
        for (String name : files) {
            File target = new File(dir, name);
            if (!target.exists() && plugin.getResource("shop/senzyshop/" + name) != null) plugin.saveResource("shop/senzyshop/" + name, false);
        }
    }

    private void loadCustom(String shopName, File dir) {
        File messageFile = new File(dir, "message.yml");
        YamlConfiguration message = YamlConfiguration.loadConfiguration(messageFile);
        LinkedHashMap<String, ShopCategory> loadedCategories = new LinkedHashMap<>();
        ConfigurationSection catSection = message.getConfigurationSection("categories");

        if (catSection != null) {
            for (String id : catSection.getKeys(false)) {
                ConfigurationSection c = catSection.getConfigurationSection(id);
                if (c == null) continue;
                loadedCategories.put(ShopCategory.normalize(id), new ShopCategory(
                        id,
                        colorOrDefault(c.getString("name"), id),
                        material(c.getString("texture"), Material.CHEST),
                        c.getInt("slot", loadedCategories.size())));
            }
        }

        // Juga dukung format texture.category yang diminta, walaupun categories belum ditulis.
        ConfigurationSection textures = message.getConfigurationSection("texture.category");
        if (textures != null) {
            for (String id : textures.getKeys(false)) {
                String key = ShopCategory.normalize(id);
                if (!loadedCategories.containsKey(key)) {
                    loadedCategories.put(key, new ShopCategory(id, id, material(textures.getString(id), Material.CHEST), loadedCategories.size()));
                }
            }
        }

        LinkedHashMap<String, ShopItem> loadedItems = new LinkedHashMap<>();
        File[] files = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".yml") && !n.equalsIgnoreCase("message.yml"));
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName));
            for (File file : files) parseCustomCategoryFile(file, loadedCategories, loadedItems);
        }

        if (loadedCategories.isEmpty()) {
            plugin.getLogger().warning("Custom shop '" + shopName + "' belum punya kategori. Gunakan /senzy shop addcategory ...");
        }
        activeShop = safeName(shopName);
        apply(loadedCategories, loadedItems);
        plugin.getLogger().info("Memuat custom shop '" + activeShop + "': " + loadedItems.size() + " item, " + loadedCategories.size() + " kategori.");
    }

    private void parseCustomCategoryFile(File file, Map<String, ShopCategory> cats, Map<String, ShopItem> items) {
        String fileCategory = file.getName().substring(0, file.getName().length() - 4);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            String declared = ShopCategory.normalize(s.getString("category", ""));
            String categoryId = cats.containsKey(declared) ? declared : ShopCategory.normalize(fileCategory);
            ShopCategory category = cats.get(categoryId);
            if (category == null) {
                category = new ShopCategory(categoryId, categoryId, Material.CHEST, cats.size());
                cats.put(categoryId, category);
            }
            ShopItem item = parseCustomItem(id, s, category);
            if (item != null) put(items, item);
        }
    }

    private ShopItem parseCustomItem(String id, ConfigurationSection s, ShopCategory category) {
        String matName = s.getString("material", id);
        Material material = validate(matName);
        if (material == null) { warn(id, "material tidak dikenal/terlarang: " + matName); return null; }
        long buy = s.getLong("buy-price", 0L);
        long sell = s.getLong("sell-price", 0L);
        if (buy < 0 || sell < 0) { warn(id, "harga negatif"); return null; }
        ConfigurationSection st = s.getConfigurationSection("stock");
        int min = st == null ? 0 : st.getInt("min", 0);
        int max = st == null ? 64 : st.getInt("max", 64);
        double chance = st == null ? 1.0 : st.getDouble("chance", 1.0);
        if (chance > 1.0 && chance <= 100.0) chance /= 100.0;
        if (min < 0 || max < min || chance < 0.01 || chance > 1.0) {
            warn(id, "stock tidak valid; min>=0, max>=min, chance 0.01-1.0"); return null;
        }
        String display = s.getString("display-name", "&f" + id.replace('_', ' '));
        int slot = Math.max(0, Math.min(53, s.getInt("slot", 0)));
        Set<String> worlds = Set.copyOf(new HashSet<>(s.getStringList("worlds")));
        return new ShopItem(id.toLowerCase(Locale.ROOT), material, category, s.getBoolean("enabled", true), display,
                slot, buy, sell, min, max, chance, worlds, PriceMode.FIXED, null, 0, 0);
    }

    private Material validate(String name) {
        if (name == null) return null;
        String mat = name.trim().toUpperCase(Locale.ROOT);
        if (mat.contains("NETHERITE") || BLOCKED.contains(mat)) return null;
        return ItemRegistry.validateMaterial(mat);
    }

    private void loadLegacy() {
        File file = new File(plugin.getDataFolder(), "items.yml");
        if (!file.exists()) plugin.saveResource("items.yml", false);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("items");
        LinkedHashMap<String, ShopItem> resolved = new LinkedHashMap<>();
        LinkedHashMap<String, ShopCategory> cats = new LinkedHashMap<>();
        if (root != null) {
            List<String> deferred = new ArrayList<>();
            for (String id : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(id);
                if (section == null) continue;
                if (isDeferredMaterial(section)) { deferred.add(id); continue; }
                ShopItem item = parseFixed(id, section, cats);
                if (item != null) put(resolved, item);
            }
            for (String id : deferred) {
                ShopItem item = parseOreMultiplier(id, root.getConfigurationSection(id), resolved, cats);
                if (item != null) put(resolved, item);
            }
        }
        if (cats.isEmpty()) {
            int slot = 10;
            for (String id : List.of("natural","ore","material","farming","animals","food","wood","nether","end","building","utility","special"))
                cats.put(id, new ShopCategory(id, id, Material.CHEST, slot++));
        }
        activeShop = "legacy";
        apply(cats, resolved);
        plugin.getLogger().info("Memuat " + resolved.size() + " item toko legacy.");
    }

    private boolean isDeferredMaterial(ConfigurationSection s) {
        String category = ShopCategory.normalize(s.getString("category"));
        PriceMode mode = PriceMode.fromString(s.getString("price-mode"), PriceMode.ORE_MULTIPLIER);
        return "material".equals(category) && mode == PriceMode.ORE_MULTIPLIER;
    }

    private ShopItem parseFixed(String id, ConfigurationSection s, Map<String, ShopCategory> cats) {
        ShopItemCommon common = parseCommon(id, s, cats);
        if (common == null) return null;
        long buy = s.getLong("buy-price", 0), sell = s.getLong("sell-price", 0);
        if (buy < 0 || sell < 0) return null;
        return new ShopItem(common.id, common.material, common.category, common.enabled, common.display, common.slot,
                buy, sell, common.min, common.max, common.chance, common.worlds, PriceMode.FIXED, null, 0, 0);
    }

    private ShopItem parseOreMultiplier(String id, ConfigurationSection s, Map<String, ShopItem> resolved, Map<String, ShopCategory> cats) {
        ShopItemCommon common = parseCommon(id, s, cats);
        if (common == null) return null;
        String ref = s.getString("ore-reference");
        ShopItem ore = ref == null ? null : resolved.get(ref.trim().toLowerCase(Locale.ROOT));
        if (ore == null || ore.buyPrice() <= 0) return null;
        double mult = s.getDouble("multiplier", 1.5), ratio = s.getDouble("sell-ratio", 0.5);
        long buy = NumberUtil.scale(ore.buyPrice(), mult), sell = NumberUtil.ratio(buy, ratio);
        return new ShopItem(common.id, common.material, common.category, common.enabled, common.display, common.slot,
                buy, sell, common.min, common.max, common.chance, common.worlds, PriceMode.ORE_MULTIPLIER, ore.id(), mult, ratio);
    }

    private record ShopItemCommon(String id, Material material, ShopCategory category, boolean enabled, String display,
                                  int slot, int min, int max, double chance, Set<String> worlds) {}

    private ShopItemCommon parseCommon(String id, ConfigurationSection s, Map<String, ShopCategory> cats) {
        Material material = validate(s.getString("material", id));
        if (material == null) return null;
        String catId = ShopCategory.normalize(s.getString("category"));
        if (catId.isBlank()) return null;
        ShopCategory category = cats.computeIfAbsent(catId, k -> new ShopCategory(k, k, Material.CHEST, cats.size() + 10));
        ConfigurationSection st = s.getConfigurationSection("stock");
        int min = st == null ? plugin.getConfig().getInt("stock.default-min", 0) : st.getInt("min", 0);
        int max = st == null ? plugin.getConfig().getInt("stock.default-max", 64) : st.getInt("max", 64);
        double chance = st == null ? plugin.getConfig().getDouble("stock.default-chance", 1) : st.getDouble("chance", 1);
        if (chance > 1) chance /= 100;
        return new ShopItemCommon(id.toLowerCase(Locale.ROOT), material, category, s.getBoolean("enabled", true),
                s.getString("display-name", "&f" + id.replace('_', ' ')), s.getInt("slot", 0), min, max, chance,
                Set.copyOf(new HashSet<>(s.getStringList("worlds"))));
    }

    private void apply(Map<String, ShopCategory> cats, Map<String, ShopItem> items) {
        byMaterial = Collections.unmodifiableMap(new LinkedHashMap<>(itemsByMaterial(items)));
        byId = Collections.unmodifiableMap(new LinkedHashMap<>(items));
        categories = Collections.unmodifiableMap(new LinkedHashMap<>(cats));
    }

    private Map<Material, ShopItem> itemsByMaterial(Map<String, ShopItem> items) {
        LinkedHashMap<Material, ShopItem> out = new LinkedHashMap<>();
        for (ShopItem item : items.values()) out.putIfAbsent(item.material(), item);
        return out;
    }

    private void put(Map<String, ShopItem> map, ShopItem item) {
        if (map.containsKey(item.id()) || map.values().stream().anyMatch(i -> i.material() == item.material())) {
            warn(item.id(), "id/material duplikat, dilewati"); return;
        }
        map.put(item.id(), item);
    }

    public Collection<ShopItem> all() { return byMaterial.values(); }
    public ShopItem get(Material material) { return byMaterial.get(material); }
    public ShopItem getById(String id) { return id == null ? null : byId.get(id.toLowerCase(Locale.ROOT)); }
    public List<ShopCategory> categories() { return List.copyOf(categories.values()); }
    public ShopCategory category(String id) { return id == null ? null : categories.get(ShopCategory.normalize(id)); }
    public List<ShopItem> byCategory(ShopCategory category) {
        List<ShopItem> out = new ArrayList<>();
        if (category == null) return out;
        for (ShopItem i : byMaterial.values()) if (i.category().equals(category)) out.add(i);
        out.sort(Comparator.comparingInt(ShopItem::slot).thenComparing(ShopItem::id));
        return out;
    }
    public List<ShopItem> search(String query) {
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT);
        List<ShopItem> out = new ArrayList<>();
        for (ShopItem i : byMaterial.values()) if (i.id().contains(q) || i.material().name().toLowerCase(Locale.ROOT).contains(q)
                || MessageUtilCompat.plain(i.displayName()).toLowerCase(Locale.ROOT).contains(q)) out.add(i);
        return out;
    }
    public ShopItem find(String input) {
        if (input == null) return null;
        ShopItem x = getById(input);
        if (x != null) return x;
        try { return get(Material.valueOf(input.trim().toUpperCase(Locale.ROOT))); } catch (IllegalArgumentException ignored) { return null; }
    }

    /** Membuat shop baru beserta message.yml kosong. */
    public boolean createShop(String name) throws IOException {
        String safe = safeName(name);
        if (safe.isBlank()) return false;
        File dir = new File(plugin.getDataFolder(), "shop/" + safe);
        if (dir.exists()) return false;
        if (!dir.mkdirs() && !dir.isDirectory()) return false;
        YamlConfiguration y = new YamlConfiguration();
        y.set("id", randomId());
        y.set("name", "§aSenzy-§bShop");
        y.set("settings.rows", 6);
        y.set("texture.category", new LinkedHashMap<>());
        y.set("categories", new LinkedHashMap<>());
        y.save(new File(dir, "message.yml"));
        return true;
    }

    public boolean addCategory(int slot, String texture, String name) {
        if (activeShop == null || name == null || name.isBlank() || slot < 0 || slot > 53) return false;
        File dir = activeDir();
        if (dir == null) return false;
        File file = new File(dir, "message.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        String id = ShopCategory.normalize(name);
        Material mat = material(texture, Material.CHEST);
        y.set("categories." + id + ".slot", slot);
        y.set("categories." + id + ".name", name);
        y.set("categories." + id + ".texture", mat.name());
        y.set("texture.category." + id, mat.name());
        try { y.save(file); load(); return true; } catch (IOException e) { plugin.getLogger().log(Level.SEVERE, "Gagal menyimpan kategori", e); return false; }
    }

    public boolean addItem(String shopName, String categoryName, int slot, String itemName, int amount, double chance) {
        if (slot < 0 || slot > 53 || amount < 0 || chance < 0.01 || chance > 1.0) return false;
        File dir = new File(plugin.getDataFolder(), "shop/" + safeName(shopName));
        if (!dir.isDirectory()) return false;
        YamlConfiguration message = YamlConfiguration.loadConfiguration(new File(dir, "message.yml"));
        String cat = ShopCategory.normalize(categoryName);
        if (!message.isConfigurationSection("categories." + cat) && !message.contains("texture.category." + cat)) return false;
        Material material = validate(itemName);
        if (material == null) return false;
        File file = new File(dir, cat + ".yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        String key = material.name().toLowerCase(Locale.ROOT);
        y.set("id", randomId());
        y.set(key + ".material", material.name());
        y.set(key + ".display-name", "&f" + pretty(material));
        y.set(key + ".slot", slot);
        y.set(key + ".category", cat);
        y.set(key + ".buy-price", 0);
        y.set(key + ".sell-price", 0);
        y.set(key + ".enabled", true);
        y.set(key + ".stock.min", Math.max(0, amount));
        y.set(key + ".stock.max", Math.max(0, amount));
        y.set(key + ".stock.chance", chance);
        try { y.save(file); load(); return true; } catch (IOException e) { plugin.getLogger().log(Level.SEVERE, "Gagal menyimpan item", e); return false; }
    }

    public boolean setBuy(String shopName, String categoryName, int slot, long price) {
        return setPriceCustom(shopName, categoryName, slot, "buy-price", price, false);
    }
    public boolean setSell(String shopName, String categoryName, int slot, String value) {
        ShopItem item = itemAt(shopName, categoryName, slot);
        if (item == null) return false;
        long sell;
        if (value.equalsIgnoreCase("sell_at_buy")) sell = Math.round(item.buyPrice() * 1.015d);
        else try { sell = Long.parseLong(value); } catch (NumberFormatException e) { return false; }
        return setPriceCustom(shopName, categoryName, slot, "sell-price", sell, true);
    }
    private boolean setPriceCustom(String shopName, String categoryName, int slot, String path, long price, boolean unused) {
        if (price < 0) return false;
        File dir = new File(plugin.getDataFolder(), "shop/" + safeName(shopName));
        File file = new File(dir, ShopCategory.normalize(categoryName) + ".yml");
        if (!file.isFile()) return false;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ShopItem item = itemAt(shopName, categoryName, slot);
        if (item == null) return false;
        y.set(item.id() + "." + path, price);
        try { y.save(file); load(); return true; } catch (IOException e) { plugin.getLogger().log(Level.SEVERE, "Gagal menyimpan harga", e); return false; }
    }
    private ShopItem itemAt(String shopName, String categoryName, int slot) {
        if (!safeName(shopName).equals(activeShop)) loadCustom(safeName(shopName), new File(plugin.getDataFolder(), "shop/" + safeName(shopName)));
        ShopCategory c = category(categoryName);
        if (c == null) return null;
        for (ShopItem i : byCategory(c)) if (i.slot() == slot) return i;
        return null;
    }

    private File activeDir() { return activeShop == null ? null : new File(plugin.getDataFolder(), "shop/" + activeShop); }
    public String activeShop() { return activeShop; }
    private static String safeName(String s) { return s == null ? "" : s.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_"); }
    private static String randomId() { return UUID.randomUUID().toString().replace("-", "").substring(0, 6); }
    private static String colorOrDefault(String s, String fallback) { return s == null || s.isBlank() ? fallback : s; }
    private static Material material(String s, Material fallback) {
        if (s == null) return fallback;
        String key = s.trim();
        if (key.regionMatches(true, 0, "minecraft:", 0, 10)) key = key.substring(10);
        Material m = Material.matchMaterial(key);
        return m == null ? fallback : m;
    }
    private static String pretty(Material m) { return Arrays.stream(m.name().toLowerCase(Locale.ROOT).split("_")).map(x -> x.isEmpty()?x:Character.toUpperCase(x.charAt(0))+x.substring(1)).reduce((a,b)->a+" "+b).orElse(m.name()); }
    private void warn(String id, String reason) { plugin.getLogger().warning("Shop [" + id + "]: " + reason); }

    /** Hindari dependensi tambahan hanya untuk membersihkan legacy display-name. */
    private static final class MessageUtilCompat {
        static String plain(String s) { return s == null ? "" : s.replaceAll("&[0-9a-fk-or]", "").replaceAll("§.", ""); }
    }
}
