package id.senzy.shop.gui;

import id.senzy.shop.shop.ShopItem;
import id.senzy.shop.util.ItemUtil;
import id.senzy.shop.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

/** Selector jumlah: latar kaca ungu, kiri/kanan kaca pink, tengah konfirmasi jumlah. */
public final class AmountSelectorGUI extends AbstractGui {
    private final ShopItem item;
    private int amount;
    private final int max;

    public AmountSelectorGUI(GuiManager gui, Player viewer, ShopItem item, int amount, int max) {
        super(gui, viewer);
        this.item = item;
        this.amount = Math.max(1, Math.min(amount, max));
        this.max = Math.max(1, max);
    }

    @Override protected Inventory createInventory() {
        return Bukkit.createInventory(this, 27, gui.messages().component("gui.amount-selector.title"));
    }

    @Override public void render() {
        inventory.clear();
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, ItemUtil.filler(gui.layout().selectorBackground));
        MessageUtil m = gui.messages();
        set(gui.layout().selectorDecreaseSlot, ItemUtil.icon(gui.layout().selectorSide, 1,
                m.component("gui.amount-selector.decrease"), null));
        set(gui.layout().selectorIncreaseSlot, ItemUtil.icon(gui.layout().selectorSide, 1,
                m.component("gui.amount-selector.increase"), null));
        long price = gui.events().buyPrice(gui.shop(viewer).activeShop(), item);
        long total = price * amount;
        set(gui.layout().selectorConfirmSlot, ItemUtil.icon(item.material(), Math.min(64, Math.max(1, amount)),
                m.component("gui.amount-selector.confirm", "amount", amount),
                m.list("gui.amount-selector.lore", "item", item.displayName(), "amount", amount, "total", m.money(total), "max", max)));
        set(gui.layout().selectorCancelSlot, ItemUtil.icon(Material.RED_STAINED_GLASS_PANE, 1,
                m.component("gui.amount-selector.cancel"), null));
    }

    @Override public void onClick(int slot, ClickType type) {
        if (slot == gui.layout().selectorDecreaseSlot) {
            amount = previousAmount(amount); render(); return;
        }
        if (slot == gui.layout().selectorIncreaseSlot) {
            amount = nextAmount(amount); render(); return;
        }
        if (slot == gui.layout().selectorCancelSlot) { gui.openCategory(viewer, item.category(), item.page()); return; }
        if (slot == gui.layout().selectorConfirmSlot) {
            gui.attemptBuy(viewer, item, amount);
        }
    }

    private int nextAmount(int current) {
        int[] values = {1, 2, 4, 8, 16, 32, 64, 128};
        for (int v : values) if (v > current) return Math.min(v, max);
        return max;
    }

    private int previousAmount(int current) {
        int[] values = {1, 2, 4, 8, 16, 32, 64, 128};
        int previous = 1;
        for (int v : values) {
            if (v >= current) break;
            previous = v;
        }
        return previous;
    }
}
