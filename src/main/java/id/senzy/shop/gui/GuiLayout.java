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

    public int mainRows = 5;
    public final Map<ShopCategory, Integer> categorySlots = new EnumMap<>(ShopCategory.class);
    public int mainSellSlot = 22;
    public int mainBalanceSlot = 39;
    public int mainCloseSlot = 40;
    public int mainRestockSlot = 41;

    public List<Integer> itemSlots = List.of();
    public int listRestockSlot = 45;
    public int listPrevSlot = 47;
    public int listBackSlot = 49;
    public int listNextSlot = 51;
    public int listBalanceSlot = 53;
    public int listSellAllSlot = 47;

    public Material filler = Material.GRAY_STAINED_GLASS_PANE;
    public long clickCooldownMs = 250L;

    public GuiLayout(SenzyShop plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        mainRows = clamp(c.getInt("gui.main.rows", 5), 1, 6);
        categorySlots.clear();
        categorySlots.put(ShopCategory.NATURAL, c.getInt("gui.main.category-slots.NATURAL", 11));
        categorySlots.put(ShopCategory.ORE, c.getInt("gui.main.category-slots.ORE", 13));
        categorySlots.put(ShopCategory.FARMING, c.getInt("gui.main.category-slots.FARMING", 15));
        categorySlots.put(ShopCategory.ANIMALS, c.getInt("gui.main.category-slots.ANIMALS", 20));
        categorySlots.put(ShopCategory.FOOD, c.getInt("gui.main.category-slots.FOOD", 24));
        mainSellSlot = c.getInt("gui.main.sell-slot", 22);
        mainBalanceSlot = c.getInt("gui.main.balance-slot", 39);
        mainCloseSlot = c.getInt("gui.main.close-slot", 40);
        mainRestockSlot = c.getInt("gui.main.restock-slot", 41);

        int rows = clamp(c.getInt("gui.list.item-rows", 5), 1, 5);
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < rows * 9; i++) slots.add(i);
        itemSlots = List.copyOf(slots);
        listRestockSlot = c.getInt("gui.list.restock-slot", 45);
        listPrevSlot = c.getInt("gui.list.prev-slot", 47);
        listBackSlot = c.getInt("gui.list.back-slot", 49);
        listNextSlot = c.getInt("gui.list.next-slot", 51);
        listBalanceSlot = c.getInt("gui.list.balance-slot", 53);
        listSellAllSlot = c.getInt("gui.list.sellall-slot", 47);

        Material f = Material.matchMaterial(c.getString("gui.filler", "GRAY_STAINED_GLASS_PANE"));
        filler = f != null && f.isItem() ? f : Material.GRAY_STAINED_GLASS_PANE;
        clickCooldownMs = Math.max(0L, c.getLong("gui.click-cooldown-ms", 250L));
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
