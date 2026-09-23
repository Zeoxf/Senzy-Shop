package id.senzy.shop.gui;

import id.senzy.shop.shop.ShopCategory;
import id.senzy.shop.shop.ShopItem;
import id.senzy.shop.util.ItemUtil;
import id.senzy.shop.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

import java.util.Map;

public final class MainShopGUI extends AbstractGui {

    public MainShopGUI(GuiManager gui, Player viewer) {
        super(gui, viewer);
    }

    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(this, gui.layout().mainRows * 9, gui.messages().component("gui.main.title"));
    }

    @Override
    public void render() {
        GuiLayout layout = gui.layout();
        MessageUtil m = gui.messages();
        inventory.clear();
        fillBackground();

        for (ShopCategory category : ShopCategory.values()) {
            Integer slot = layout.categorySlots.get(category);
            if (slot == null) continue;
            int count = 0;
            for (ShopItem item : gui.shop().byCategory(category)) if (item.enabled()) count++;
            set(slot, ItemUtil.icon(category.icon(), 1, MessageUtil.color(category.displayName()),
                    m.list("gui.main.category-lore", "count", count)));
        }
        set(layout.mainSellSlot, ItemUtil.icon(Material.EMERALD, 1, m.component("gui.main.sell-name"),
                m.list("gui.main.sell-lore")));
        set(layout.mainBalanceSlot, gui.balanceIcon(viewer));
        set(layout.mainContractsSlot, ItemUtil.icon(Material.WRITABLE_BOOK, 1, m.component("gui.main.contracts-name"),
                m.list("gui.main.contracts-lore")));
        set(layout.mainSearchSlot, ItemUtil.icon(Material.COMPASS, 1, m.component("gui.main.search-name"),
                m.list("gui.main.search-lore")));
        set(layout.mainCloseSlot, ItemUtil.icon(Material.BARRIER, 1, m.component("gui.main.close-name"), null));
        updateClock();
    }

    @Override
    public void updateClock() {
        set(gui.layout().mainRestockSlot, gui.restockIcon());
    }

    @Override
    public void onClick(int slot, ClickType type) {
        GuiLayout layout = gui.layout();
        if (slot == layout.mainCloseSlot) {
            gui.later(viewer::closeInventory);
            return;
        }
        if (slot == layout.mainSellSlot) {
            gui.openSell(viewer);
            return;
        }
        if (slot == layout.mainContractsSlot) {
            gui.openContract(viewer);
            return;
        }
        if (slot == layout.mainSearchSlot) {
            gui.later(() -> {
                viewer.closeInventory();
                gui.messages().send(viewer, "search.prompt");
                gui.beginSearch(viewer);
            });
            return;
        }
        for (Map.Entry<ShopCategory, Integer> e : layout.categorySlots.entrySet()) {
            if (e.getValue() == slot) {
                gui.openCategory(viewer, e.getKey(), 0);
                return;
            }
        }
    }
}
