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
    public MainShopGUI(GuiManager gui, Player viewer) { super(gui, viewer); }
    @Override protected Inventory createInventory() {
        return Bukkit.createInventory(this, gui.layout().mainRows * 9, gui.messages().component("gui.main.title"));
    }
    @Override public void render() {
        MessageUtil m = gui.messages(); inventory.clear(); fillBackground();
        for (ShopCategory category : gui.shop(viewer).categories()) {
            int count = 0; for (ShopItem item : gui.shop(viewer).byCategory(category)) if (item.enabled()) count++;
            set(category.slot(), ItemUtil.icon(category.icon(), 1, MessageUtil.color(category.displayName()),
                    m.list("gui.main.category-lore", "count", count)));
        }
        set(28, ItemUtil.icon(Material.EMERALD, 1, m.component("gui.main.sell-name"), m.list("gui.main.sell-lore")));
        set(30, gui.balanceIcon(viewer)); set(32, ItemUtil.icon(Material.WRITABLE_BOOK,1,m.component("gui.main.contracts-name"),m.list("gui.main.contracts-lore")));
        set(34, ItemUtil.icon(Material.COMPASS,1,m.component("gui.main.search-name"),m.list("gui.main.search-lore")));
        set(40, gui.restockIcon()); set(49, ItemUtil.icon(Material.BARRIER,1,m.component("gui.main.close-name"),null));
    }
    @Override public void updateClock() { set(40, gui.restockIcon()); }
    @Override public void onClick(int slot, ClickType type) {
        if(slot==49){gui.later(viewer::closeInventory);return;}
        if(slot==28){gui.openSell(viewer);return;} if(slot==32){gui.openContract(viewer);return;}
        if(slot==34){gui.messages().send(viewer,"search.use-command");return;}
        for(ShopCategory c:gui.shop(viewer).categories()) if(c.slot()==slot){gui.openCategory(viewer,c,0);return;}
    }
}
