package id.senzy.shop.gui;

import id.senzy.shop.SenzyShop;
import id.senzy.shop.shop.ShopCategory;
import id.senzy.shop.shop.ShopManager;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

/** Layout GUI; kategori/item slot utama berasal dari custom shop YAML. */
public final class GuiLayout {
    private final SenzyShop plugin;
    private ShopManager shop;
    public int mainRows = 6;
    public final Map<ShopCategory, Integer> categorySlots = new LinkedHashMap<>();
    public List<Integer> itemSlots = List.of();
    public int listRestockSlot = 36, listPrevSlot = 42, listBackSlot = 45, listNextSlot = 48, listBalanceSlot = 50, listSellAllSlot = 39;
    public int contractDailyTabSlot = 3, contractWeeklyTabSlot = 5;
    public List<Integer> contractSlots = List.of();
    public int contractBackSlot = 49;
    public int selectorRows = 3, selectorDecreaseSlot = 11, selectorConfirmSlot = 13, selectorIncreaseSlot = 15, selectorCancelSlot = 22;
    public int confirmPreviewSlot = 13, confirmYesSlot = 21, confirmNoSlot = 23;
    public Material filler = Material.GRAY_STAINED_GLASS_PANE;
    public Material selectorBackground = Material.PURPLE_STAINED_GLASS_PANE;
    public Material selectorSide = Material.PINK_STAINED_GLASS_PANE;
    public long clickCooldownMs = 250L;

    public GuiLayout(SenzyShop plugin) { this.plugin = plugin; }
    public void setShop(ShopManager shop) { this.shop = shop; }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        mainRows = clamp(c.getInt("gui.main.rows", 6), 1, 6);
        categorySlots.clear();
        if (shop != null) for (ShopCategory cat : shop.categories()) categorySlots.put(cat, cat.slot());
        int rows = clamp(c.getInt("gui.list.item-rows", 4), 1, 4);
        itemSlots = readSlots(c.getIntegerList("gui.list.item-slots"), slotRange(0, rows), 54);
        listRestockSlot = c.getInt("gui.list.restock-slot", 45);
        listPrevSlot = c.getInt("gui.list.prev-slot", 47);
        listBackSlot = c.getInt("gui.list.back-slot", 49);
        listNextSlot = c.getInt("gui.list.next-slot", 51);
        listBalanceSlot = c.getInt("gui.list.balance-slot", 53);
        listSellAllSlot = c.getInt("gui.list.sellall-slot", 47);
        contractDailyTabSlot = c.getInt("gui.contract.daily-tab-slot", 3);
        contractWeeklyTabSlot = c.getInt("gui.contract.weekly-tab-slot", 5);
        contractSlots = slotRange(9, clamp(c.getInt("gui.contract.item-rows", 3), 1, 4));
        contractBackSlot = c.getInt("gui.contract.back-slot", 49);
        selectorRows = 3;
        selectorDecreaseSlot = c.getInt("gui.amount-selector.decrease-slot", 11);
        selectorConfirmSlot = c.getInt("gui.amount-selector.confirm-slot", 13);
        selectorIncreaseSlot = c.getInt("gui.amount-selector.increase-slot", 15);
        selectorCancelSlot = c.getInt("gui.amount-selector.cancel-slot", 22);
        confirmPreviewSlot = c.getInt("gui.confirm.preview-slot", 13);
        confirmYesSlot = c.getInt("gui.confirm.yes-slot", 21);
        confirmNoSlot = c.getInt("gui.confirm.no-slot", 23);
        filler = material(c.getString("gui.filler", "GRAY_STAINED_GLASS_PANE"), Material.GRAY_STAINED_GLASS_PANE);
        selectorBackground = material(c.getString("gui.amount-selector.background", "PURPLE_STAINED_GLASS_PANE"), Material.PURPLE_STAINED_GLASS_PANE);
        selectorSide = material(c.getString("gui.amount-selector.side", "PINK_STAINED_GLASS_PANE"), Material.PINK_STAINED_GLASS_PANE);
        clickCooldownMs = Math.max(0L, c.getLong("gui.click-cooldown-ms", 250L));
    }
    private static Material material(String s, Material fallback) { Material m = Material.matchMaterial(s); return m == null ? fallback : m; }
    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
    private static List<Integer> slotRange(int startRow, int rows) { List<Integer> out = new ArrayList<>(); for (int r=0;r<rows;r++) for(int c=0;c<9;c++) out.add((startRow+r)*9+c); return out; }
    private static List<Integer> readSlots(List<Integer> configured, List<Integer> fallback, int size) { if(configured==null||configured.isEmpty()) return fallback; LinkedHashSet<Integer> set=new LinkedHashSet<>(); for(Integer s:configured) if(s!=null&&s>=0&&s<size)set.add(s); return List.copyOf(set); }
}
