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

/** Satu kategori. Slot item persis mengikuti field 'slot' pada file kategori. */
public final class CategoryGUI extends AbstractGui {
    private final ShopCategory category;
    private final Map<Integer, ShopItem> slotItems = new HashMap<>();

    public CategoryGUI(GuiManager gui, Player viewer, ShopCategory category) {
        super(gui, viewer);
        this.category = category;
    }

    @Override protected Inventory createInventory() {
        return Bukkit.createInventory(this, 54, gui.messages().component("gui.category.title", "category", category.displayName()));
    }

    @Override public void render() {
        MessageUtil m = gui.messages();
        inventory.clear();
        fillBackground();
        slotItems.clear();
        for (ShopItem item : gui.shop().byCategory(category)) {
            if (item.enabled() && item.slot() >= 0 && item.slot() < 54) {
                slotItems.put(item.slot(), item);
                set(item.slot(), buildIcon(item));
            }
        }
        set(gui.layout().listBackSlot, ItemUtil.icon(Material.BARRIER, 1, m.component("gui.category.back"), null));
        set(gui.layout().listBalanceSlot, gui.balanceIcon(viewer));
        updateClock();
    }

    private ItemStack buildIcon(ShopItem item) {
        MessageUtil m = gui.messages();
        int stock = gui.stock().getStock(item);
        List<Component> lore = m.list("gui.item-lore", "stockcolor", stock > 0 ? "&a" : "&c", "stock", stock,
                "max", gui.stock().getMax(item), "buy", item.canBuy() ? m.money(gui.events().buyPrice(item)) : "-",
                "sell", item.canSell() ? m.money(gui.events().sellPrice(item)) : "-");
        return ItemUtil.icon(item.material(), Math.max(1, Math.min(item.material().getMaxStackSize(), Math.max(stock, 1))), MessageUtil.color(item.displayName()), lore);
    }

    @Override public void updateClock() { set(gui.layout().listRestockSlot, gui.restockIcon()); }

    @Override public void onClick(int slot, ClickType type) {
        if (slot == gui.layout().listBackSlot) { gui.openMain(viewer); return; }
        ShopItem item = slotItems.get(slot);
        if (item == null) return;
        // Requirement baru: RIGHT = langsung beli 1, LEFT = quantity selector.
        if (type == ClickType.RIGHT) gui.attemptBuy(viewer, item, 1);
        else if (type == ClickType.LEFT) gui.openAmountSelector(viewer, item);
        else if (type == ClickType.SHIFT_RIGHT) gui.trade().sell(viewer, item, 1);
        else if (type == ClickType.SHIFT_LEFT) gui.trade().sell(viewer, item, -1);
        render();
    }
}
