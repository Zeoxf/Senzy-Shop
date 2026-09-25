package id.senzy.shop.event;

import id.senzy.shop.SenzyShop;
import id.senzy.shop.shop.ShopItem;
import id.senzy.shop.shop.ShopManager;
import id.senzy.shop.shop.StockManager;
import id.senzy.shop.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class ShopEventManager {
    public record EventItem(double chance, int min, int max, String priceType, double priceValue) {}
    public record ShopEvent(String id, String displayName, long durationMillis, int priority,
                            Map<String, EventItem> items, boolean announceStart, boolean announceEnd) {}
    private final SenzyShop plugin;
    private final ShopManager shop;
    private final StockManager stock;
    private final Map<String, ShopEvent> definitions = new LinkedHashMap<>();
    private String activeId;
    private long activeUntil;
    private long activeStarted;

    public ShopEventManager(SenzyShop plugin, ShopManager shop, StockManager stock) {
        this.plugin = plugin; this.shop = shop; this.stock = stock;
    }

    public void load() {
        File f = new File(plugin.getDataFolder(), "events.yml");
        if (!f.exists()) plugin.saveResource("events.yml", false);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        definitions.clear();
        ConfigurationSection root = y.getConfigurationSection("events");
        if (root != null) for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            long duration = parseDuration(s.getString("duration", "1h"));
            int priority = s.getInt("priority", 0);
            Map<String, EventItem> items = new HashMap<>();
            ConfigurationSection is = s.getConfigurationSection("items");
            if (is != null) for (String itemId : is.getKeys(false)) {
                ConfigurationSection e = is.getConfigurationSection(itemId);
                if (e == null) continue;
                double chance = e.getDouble("chance", 1.0);
                if (chance > 1 && chance <= 100) chance /= 100.0;
                int min = e.getInt("stock.min", e.getInt("min", 1));
                int max = e.getInt("stock.max", e.getInt("max", min));
                if (chance < 0 || chance > 1 || min < 0 || max < min) {
                    plugin.getLogger().warning("events.yml ["+id+" -> "+itemId+"] stock/chance invalid; skipped");
                    continue;
                }
                String type = e.getString("price.type", "none").toLowerCase(Locale.ROOT);
                double value = e.getDouble("price.value", e.getDouble("price.percent", 0));
                items.put(itemId.toLowerCase(Locale.ROOT), new EventItem(chance,min,max,type,value));
            }
            definitions.put(id.toLowerCase(Locale.ROOT), new ShopEvent(
                    id.toLowerCase(Locale.ROOT), s.getString("display-name","&5"+id),
                    duration, priority, Map.copyOf(items),
                    s.getBoolean("announcement.start", true), s.getBoolean("announcement.end", true)));
        }
        File state = new File(plugin.getDataFolder(), "event-state.yml");
        YamlConfiguration st = YamlConfiguration.loadConfiguration(state);
        String savedId = st.getString("active-id");
        long savedStarted = st.getLong("started-at", 0L);
        long savedUntil = st.getLong("until", 0L);
        if (savedId != null && definitions.containsKey(savedId.toLowerCase(Locale.ROOT)) && savedUntil > System.currentTimeMillis()) {
            activeId = savedId.toLowerCase(Locale.ROOT);
            activeStarted = savedStarted;
            activeUntil = savedUntil;
        } else {
            activeId = null; activeStarted = 0; activeUntil = 0;
        }
    }

    public void tick() {
        if (activeId != null && System.currentTimeMillis() >= activeUntil) stop(null);
    }

    public Collection<ShopEvent> all() { return Collections.unmodifiableCollection(definitions.values()); }
    public ShopEvent get(String id) { return definitions.get(id == null ? "" : id.toLowerCase(Locale.ROOT)); }
    public String activeId() { return activeId; }
    public ShopEvent active() { return activeId == null ? null : definitions.get(activeId); }
    public long remainingMillis() { return Math.max(0, activeUntil-System.currentTimeMillis()); }

    public long buyPrice(ShopItem item) { return modify(item.buyPrice(), item); }
    public long sellPrice(ShopItem item) { return modify(item.sellPrice(), item); }

    private long modify(long base, ShopItem item) {
        EventItem e = activeItem(item);
        if (e == null || base <= 0 || "none".equals(e.priceType())) return base;
        double result = base;
        switch (e.priceType()) {
            case "increase" -> result *= 1.0 + e.priceValue()/100.0;
            case "decrease" -> result *= Math.max(0.0, 1.0 - e.priceValue()/100.0);
            case "set" -> result = e.priceValue();
        }
        return Math.max(0, Math.round(result));
    }

    private EventItem activeItem(ShopItem item) {
        ShopEvent ev = active();
        return ev == null ? null : ev.items().get(item.id().toLowerCase(Locale.ROOT));
    }

    public boolean start(String id, CommandSenderLike sender) {
        ShopEvent ev = get(id);
        if (ev == null) return false;
        activeId = ev.id(); activeStarted = System.currentTimeMillis(); activeUntil = activeStarted + ev.durationMillis();
        saveState();
        Map<org.bukkit.Material,Integer> rolls = new HashMap<>();
        for (ShopItem item : shop.all()) {
            EventItem ei = ev.items().get(item.id().toLowerCase(Locale.ROOT));
            if (ei == null) continue;
            int amount = ThreadLocalRandom.current().nextDouble() < ei.chance()
                    ? (ei.min() >= ei.max() ? ei.min() : ThreadLocalRandom.current().nextInt(ei.min(), ei.max()+1)) : 0;
            rolls.put(item.material(), amount);
        }
        for (var e : rolls.entrySet()) {
            ShopItem item = shop.get(e.getKey());
            if (item != null) stock.setRuntimeStock(item, e.getValue());
        }
        if (ev.announceStart()) broadcast("&5&lSENZY EVENT &r" + ev.displayName() + " &7dimulai! &f" + TimeUtil.formatDuration(ev.durationMillis()));
        return true;
    }

    public boolean stop(CommandSenderLike sender) {
        if (activeId == null) return false;
        ShopEvent old = active();
        activeId = null; activeUntil = 0; activeStarted = 0; saveState();
        if (old != null && old.announceEnd()) broadcast("&5&lSENZY EVENT &r" + old.displayName() + " &7telah berakhir.");
        return true;
    }

    public long startedAt() { return activeStarted; }

    public boolean create(String id) {
        if (id == null || id.isBlank() || get(id) != null) return false;
        File f=new File(plugin.getDataFolder(),"events.yml");
        YamlConfiguration y=YamlConfiguration.loadConfiguration(f);
        String path="events."+id.toLowerCase(Locale.ROOT);
        y.set(path+".display-name","&5"+id);
        y.set(path+".duration","1h");
        y.set(path+".priority",0);
        y.set(path+".announcement.start",true);
        y.set(path+".announcement.end",true);
        y.createSection(path+".items");
        try { y.save(f); } catch(IOException e){ plugin.getLogger().warning("Gagal membuat event "+id+": "+e.getMessage()); return false; }
        load(); return true;
    }

    public boolean delete(String id) {
        if (activeId != null && activeId.equalsIgnoreCase(id)) stop(null);
        if (get(id)==null) return false;
        File f=new File(plugin.getDataFolder(),"events.yml"); YamlConfiguration y=YamlConfiguration.loadConfiguration(f);
        y.set("events."+id.toLowerCase(Locale.ROOT),null);
        try { y.save(f); } catch(IOException e){ return false; }
        load(); return true;
    }

    private void saveState() {
        File f = new File(plugin.getDataFolder(), "event-state.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        y.set("active-id", activeId);
        y.set("started-at", activeStarted);
        y.set("until", activeUntil);
        try { y.save(f); } catch (IOException e) { plugin.getLogger().warning("Gagal menyimpan event-state.yml: " + e.getMessage()); }
    }

    private void broadcast(String raw) {
        String msg = org.bukkit.ChatColor.translateAlternateColorCodes('&', raw);
        Bukkit.broadcastMessage(msg);
    }

    private static long parseDuration(String raw) {
        if(raw==null||raw.isBlank()) return 3600000L;
        String s=raw.trim().toLowerCase(Locale.ROOT);
        try {
            long mul=s.endsWith("d")?86400000L:s.endsWith("h")?3600000L:s.endsWith("m")?60000L:s.endsWith("s")?1000L:1L;
            String n=s.replaceAll("[^0-9]","");
            return Math.max(1000L, Long.parseLong(n)*mul);
        } catch(Exception e){ return 3600000L; }
    }

    @FunctionalInterface public interface CommandSenderLike {}
}
