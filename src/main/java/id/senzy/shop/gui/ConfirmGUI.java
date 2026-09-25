package id.senzy.shop.gui;

import id.senzy.shop.shop.ShopItem;
import id.senzy.shop.util.ItemUtil;
import id.senzy.shop.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

/** Dialog konfirmasi untuk pembelian "mahal" (total >= security.expensive-threshold). */
public final class ConfirmGUI extends AbstractGui {
    private final ShopItem item;
    private final int amount;
    private final long cost;

    public ConfirmGUI(GuiManager gui, Player viewer, ShopItem item, int amount, long cost) {
        super(gui, viewer);
        this.item = item;
        this.amount = amount;
        this.cost = cost;
    }

    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(this, 27, gui.messages().component("gui.confirm.title"));
    }

    @Override
    public void render() {
        GuiLayout layout = gui.layout();
        MessageUtil m = gui.messages();
        inventory.clear();
        fillBackground();

        set(layout.confirmPreviewSlot, ItemUtil.icon(item.material(), Math.min(amount, 64),
                m.component("gui.confirm.item-name", "item", ItemUtil.prettyName(item.material())),
                m.list("gui.confirm.item-lore", "amount", amount, "total", m.money(cost))));
        set(layout.confirmYesSlot, ItemUtil.icon(Material.LIME_WOOL, 1, m.component("gui.confirm.yes-name"), null));
        set(layout.confirmNoSlot, ItemUtil.icon(Material.RED_WOOL, 1, m.component("gui.confirm.no-name"), null));
    }

    @Override
    public void onClick(int slot, ClickType type) {
        GuiLayout layout = gui.layout();
        if (slot == layout.confirmYesSlot) {
            gui.trade().buy(viewer, item, amount);   // jumlah PERSIS yang ditampilkan di dialog ini
            gui.openCategory(viewer, item.category(), item.page());
            return;
        }
        if (slot == layout.confirmNoSlot) {
            gui.openCategory(viewer, item.category(), item.page());
        }
    }
}
