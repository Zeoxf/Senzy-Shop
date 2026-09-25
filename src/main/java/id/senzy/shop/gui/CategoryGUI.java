package id.senzy.shop.gui;

import id.senzy.shop.shop.ShopCategory;
import id.senzy.shop.shop.ShopItem;
import id.senzy.shop.util.ItemUtil;
import id.senzy.shop.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Kategori dengan 36 slot item + 18 slot kontrol (2 baris bawah). */
public final class CategoryGUI extends AbstractGui {
    private static final int ITEM_SLOTS = 36;
    private final ShopCategory category;
    private final int page;
    private final Map<Integer, ShopItem> slotItems = new HashMap<>();

    public CategoryGUI(GuiManager gui, Player viewer, ShopCategory category, int page) {
        super(gui, viewer);
        this.category = category;
        this.page = Math.max(0, page);
    }

    @Override protected Inventory createInventory() {
        return Bukkit.createInventory(this, 54,
                gui.messages().component("gui.category.title", "category", category.displayName(), "page", page + 1, "pages", maxPage() + 1));
    }

    @Override public void render() {
        MessageUtil m = gui.messages();
        inventory.clear();
        fillBackground();
        slotItems.clear();

        int maxPage = maxPage();
        for (ShopItem item : gui.shop().byCategory(category)) {
            if (!item.enabled() || item.page() != page || item.slot() < 0 || item.slot() >= ITEM_SLOTS) continue;
            slotItems.put(item.slot(), item);
            set(item.slot(), buildIcon(item));
        }

        // Dua baris terbawah khusus kontrol halaman/aksi.
        set(gui.layout().listRestockSlot, gui.restockIcon());
        set(gui.layout().listSellAllSlot, ItemUtil.icon(Material.EMERALD, 1,
                m.component("gui.category.sellall"), null));
        set(gui.layout().listPrevSlot, ItemUtil.icon(Material.ARROW, 1,
                m.component("gui.category.prev", "page", page + 1, "pages", maxPage + 1), null));
        set(gui.layout().listBackSlot, ItemUtil.icon(Material.BARRIER, 1,
                m.component("gui.category.back"), null));
        set(47, ItemUtil.icon(Material.PAPER, 1,
                m.component("gui.category.page", "page", page + 1, "pages", maxPage + 1), null));
        set(gui.layout().listNextSlot, ItemUtil.icon(Material.ARROW, 1,
                m.component("gui.category.next", "page", page + 1), null));
        set(gui.layout().listBalanceSlot, gui.balanceIcon(viewer));
        set(53, ItemUtil.icon(Material.BARRIER, 1, m.component("gui.main.close-name"), null));
    }

    private int maxPage() {
        int max = 0;
        for (ShopItem item : gui.shop().byCategory(category)) if (item.enabled()) max = Math.max(max, item.page());
        return max;
    }

    private ItemStack buildIcon(ShopItem item) {
        MessageUtil m = gui.messages();
        int stock = gui.stock().getStock(item);
        List<Component> lore = m.list("gui.item-lore", "stockcolor", stock > 0 ? "&a" : "&c", "stock", stock,
                "max", gui.stock().getMax(item), "buy", item.canBuy() ? m.money(gui.events().buyPrice(item)) : "-",
                "sell", item.canSell() ? m.money(gui.events().sellPrice(item)) : "-");
        return ItemUtil.icon(item.material(), Math.max(1, Math.min(item.material().getMaxStackSize(), Math.max(stock, 1))),
                MessageUtil.color(item.displayName()), lore);
    }

    @Override public void updateClock() { set(gui.layout().listRestockSlot, gui.restockIcon()); }

    @Override public void onClick(int slot, ClickType type) {
        if (slot == gui.layout().listBackSlot || slot == 53) { gui.openMain(viewer); return; }
        if (slot == gui.layout().listPrevSlot) { if (page > 0) gui.openCategory(viewer, category, page - 1); return; }
        if (slot == gui.layout().listNextSlot) { if (page < maxPage()) gui.openCategory(viewer, category, page + 1); return; }
        if (slot == gui.layout().listSellAllSlot) { gui.trade().sellAll(viewer); return; }

        ShopItem item = slotItems.get(slot);
        if (item == null) return;
        if (type == ClickType.RIGHT) gui.attemptBuy(viewer, item, 1);
        else if (type == ClickType.LEFT) gui.openAmountSelector(viewer, item);
        else if (type == ClickType.SHIFT_RIGHT) gui.trade().sell(viewer, item, 1);
        else if (type == ClickType.SHIFT_LEFT) gui.trade().sell(viewer, item, -1);
        render();
    }
}
