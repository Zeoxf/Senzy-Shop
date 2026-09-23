package id.senzy.shop.gui;

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

/** Hasil pencarian (dengan halaman) - kata kunci diketik lewat chat, lihat SearchListener. */
public final class SearchGUI extends AbstractGui {
    private final String query;
    private final int page;
    private final Map<Integer, ShopItem> slotItems = new HashMap<>();
    private List<ShopItem> results;

    public SearchGUI(GuiManager gui, Player viewer, String query, int page) {
        super(gui, viewer);
        this.query = query;
        this.page = Math.max(0, page);
    }

    private List<ShopItem> results() {
        if (results == null) results = gui.shop().search(query);
        return results;
    }

    private int perPage() {
        return Math.max(1, gui.layout().itemSlots.size());
    }

    private int pages() {
        return Math.max(1, (int) Math.ceil(results().size() / (double) perPage()));
    }

    private int currentPage() {
        return Math.min(page, pages() - 1);
    }

    @Override
    protected Inventory createInventory() {
        int shownPage = currentPage() + 1;
        return Bukkit.createInventory(this, 54, gui.messages().component("gui.search.title",
                "query", query, "page", shownPage, "pages", pages()));
    }

    @Override
    public void render() {
        GuiLayout layout = gui.layout();
        MessageUtil m = gui.messages();
        inventory.clear();
        fillBackground();
        slotItems.clear();

        List<ShopItem> list = results();
        if (list.isEmpty()) {
            m.send(viewer, "search.empty", "query", query);
        }
        List<Integer> slots = layout.itemSlots;
        int start = currentPage() * slots.size();
        for (int i = 0; i < slots.size() && start + i < list.size(); i++) {
            ShopItem item = list.get(start + i);
            int slot = slots.get(i);
            slotItems.put(slot, item);
            set(slot, buildIcon(item));
        }

        if (currentPage() > 0) {
            set(layout.listPrevSlot, ItemUtil.icon(Material.ARROW, 1, m.component("gui.category.prev"), null));
        }
        if (currentPage() < pages() - 1) {
            set(layout.listNextSlot, ItemUtil.icon(Material.ARROW, 1, m.component("gui.category.next"), null));
        }
        set(layout.listBackSlot, ItemUtil.icon(Material.BARRIER, 1, m.component("gui.category.back"), null));
        set(layout.listBalanceSlot, gui.balanceIcon(viewer));
        updateClock();
    }

    private ItemStack buildIcon(ShopItem item) {
        MessageUtil m = gui.messages();
        int stock = gui.stock().getStock(item);
        List<Component> lore = m.list("gui.item-lore",
                "stockcolor", stock > 0 ? "&a" : "&c",
                "stock", stock,
                "max", gui.stock().getMax(item),
                "buy", item.canBuy() ? m.money(item.buyPrice()) : "-",
                "sell", item.canSell() ? m.money(item.sellPrice()) : "-");
        return ItemUtil.icon(item.material(), 1, null, lore);
    }

    @Override
    public void updateClock() {
        set(gui.layout().listRestockSlot, gui.restockIcon());
    }

    @Override
    public void onClick(int slot, ClickType type) {
        GuiLayout layout = gui.layout();
        if (slot == layout.listBackSlot) {
            gui.openMain(viewer);
            return;
        }
        if (slot == layout.listPrevSlot && currentPage() > 0) {
            gui.openSearchResults(viewer, query, currentPage() - 1);
            return;
        }
        if (slot == layout.listNextSlot && currentPage() < pages() - 1) {
            gui.openSearchResults(viewer, query, currentPage() + 1);
            return;
        }
        ShopItem item = slotItems.get(slot);
        if (item == null) return;
        switch (type) {
            case LEFT -> gui.attemptBuy(viewer, item, 1);
            case SHIFT_LEFT -> gui.attemptBuy(viewer, item, -1);
            case RIGHT -> gui.trade().sell(viewer, item, 1);
            case SHIFT_RIGHT -> gui.trade().sell(viewer, item, -1);
            default -> { }
        }
        render();
    }
}
