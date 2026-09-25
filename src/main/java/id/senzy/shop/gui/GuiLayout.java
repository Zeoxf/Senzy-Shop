package id.senzy.shop.gui;

import id.senzy.shop.SenzyShop;
import id.senzy.shop.shop.ShopCategory;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Semua posisi slot dibaca dari config.yml (bagian gui:) - tidak ada slot hardcode di class GUI. */
public final class GuiLayout {
    private final SenzyShop plugin;

    // ---- Main shop GUI ----
    public int mainRows = 6;
    public final Map<ShopCategory, Integer> categorySlots = new EnumMap<>(ShopCategory.class);
    public int mainSellSlot = 28;
    public int mainBalanceSlot = 30;
    public int mainContractsSlot = 32;
    public int mainSearchSlot = 34;
    public int mainRestockSlot = 40;
    public int mainCloseSlot = 49;

    // ---- List GUI (category / search) ----
    public List<Integer> itemSlots = List.of();
    public final Map<ShopCategory, List<Integer>> categoryItemSlots = new EnumMap<>(ShopCategory.class);
    public int listRestockSlot = 45;
    public int listPrevSlot = 47;
    public int listBackSlot = 49;
    public int listNextSlot = 51;
    public int listBalanceSlot = 53;
    public int listSellAllSlot = 47;

    // ---- Contract GUI ----
    public int contractDailyTabSlot = 3;
    public int contractWeeklyTabSlot = 5;
    public List<Integer> contractSlots = List.of();
    public int contractBackSlot = 49;

    // ---- Confirm (expensive purchase) GUI ----
    public int confirmPreviewSlot = 13;
    public int confirmYesSlot = 21;
    public int confirmNoSlot = 23;

    public Material filler = Material.GRAY_STAINED_GLASS_PANE;
    public long clickCooldownMs = 250L;

    public GuiLayout(SenzyShop plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        mainRows = clamp(c.getInt("gui.main.rows", 6), 1, 6);
        categorySlots.clear();
        int categoryDefaultSlot = 10;
        for (ShopCategory category : ShopCategory.values()) {
            categorySlots.put(category, c.getInt("gui.main.category-slots." + category.name(), categoryDefaultSlot++));
        }
        mainSellSlot = c.getInt("gui.main.sell-slot", 28);
        mainBalanceSlot = c.getInt("gui.main.balance-slot", 30);
        mainContractsSlot = c.getInt("gui.main.contracts-slot", 32);
        mainSearchSlot = c.getInt("gui.main.search-slot", 34);
        mainRestockSlot = c.getInt("gui.main.restock-slot", 40);
        mainCloseSlot = c.getInt("gui.main.close-slot", 49);

        int rows = clamp(c.getInt("gui.list.item-rows", 5), 1, 5);
        itemSlots = readSlots(c.getIntegerList("gui.list.item-slots"), slotRange(0, rows), 54);
        categoryItemSlots.clear();
        for (ShopCategory category : ShopCategory.values()) {
            List<Integer> configured = c.getIntegerList("gui.list.category-slots." + category.name());
            categoryItemSlots.put(category, readSlots(configured, itemSlots, 54));
        }
        listRestockSlot = c.getInt("gui.list.restock-slot", 45);
        listPrevSlot = c.getInt("gui.list.prev-slot", 47);
        listBackSlot = c.getInt("gui.list.back-slot", 49);
        listNextSlot = c.getInt("gui.list.next-slot", 51);
        listBalanceSlot = c.getInt("gui.list.balance-slot", 53);
        listSellAllSlot = c.getInt("gui.list.sellall-slot", 47);

        contractDailyTabSlot = c.getInt("gui.contract.daily-tab-slot", 3);
        contractWeeklyTabSlot = c.getInt("gui.contract.weekly-tab-slot", 5);
        int contractRows = clamp(c.getInt("gui.contract.item-rows", 3), 1, 4);
        contractSlots = slotRange(9, contractRows);
        contractBackSlot = c.getInt("gui.contract.back-slot", 49);

        confirmPreviewSlot = c.getInt("gui.confirm.preview-slot", 13);
        confirmYesSlot = c.getInt("gui.confirm.yes-slot", 21);
        confirmNoSlot = c.getInt("gui.confirm.no-slot", 23);

        Material f = Material.matchMaterial(c.getString("gui.filler", "GRAY_STAINED_GLASS_PANE"));
        filler = f != null && f.isItem() ? f : Material.GRAY_STAINED_GLASS_PANE;
        clickCooldownMs = Math.max(0L, c.getLong("gui.click-cooldown-ms", 250L));
    }

    private static List<Integer> readSlots(List<Integer> configured, List<Integer> fallback, int size) {
        if (configured == null || configured.isEmpty()) return fallback;
        List<Integer> out = new ArrayList<>();
        for (Integer slot : configured) if (slot != null && slot >= 0 && slot < size && !out.contains(slot)) out.add(slot);
        return out.isEmpty() ? fallback : List.copyOf(out);
    }

    private static List<Integer> slotRange(int startSlot, int rows) {
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < rows * 9; i++) slots.add(startSlot + i);
        return List.copyOf(slots);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
