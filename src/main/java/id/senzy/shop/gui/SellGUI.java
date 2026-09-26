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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Menu jual: menampilkan item di inventory pemain yang boleh dijual. Tidak ada item yang
 * dipindahkan ke dalam GUI (jadi tidak ada risiko item hilang saat crash / close / drag).
 */
public final class SellGUI extends AbstractGui {
    private final Map<Integer, ShopItem> slotItems = new HashMap<>();

    public SellGUI(GuiManager gui, Player viewer) {
        super(gui, viewer);
    }

    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(this, 54, gui.messages().component("gui.sell.title"));
    }

    @Override
    public void render() {
        GuiLayout layout = gui.layout();
        MessageUtil m = gui.messages();
        inventory.clear();
        fillBackground();
        slotItems.clear();

        List<Integer> slots = layout.itemSlots;
        int index = 0;
        String world = viewer.getWorld().getName();
        for (ShopItem item : gui.shop(viewer).all()) {
            if (index >= slots.size()) break;
            if (!item.canSell() || !item.allowedIn(world)) continue;
            int have = ItemUtil.countPlain(viewer.getInventory(), item.material());
            if (have <= 0) continue;
            String total;
            try {
                total = m.money(Math.multiplyExact(item.sellPrice(), (long) have));
            } catch (ArithmeticException e) {
                total = "?";
            }
            List<Component> lore = m.list("gui.sell.item-lore", "have", have,
                    "price", m.money(item.sellPrice()), "total", total);
            int slot = slots.get(index++);
            slotItems.put(slot, item);
            set(slot, ItemUtil.icon(item.material(), Math.min(have, 64), null, lore));
        }

        set(layout.listSellAllSlot, ItemUtil.icon(Material.EMERALD_BLOCK, 1,
                m.component("gui.sell.sellall-name"), m.list("gui.sell.sellall-lore")));
        set(layout.listBackSlot, ItemUtil.icon(Material.BARRIER, 1, m.component("gui.category.back"), null));
        set(layout.listBalanceSlot, gui.balanceIcon(viewer));
    }

    @Override
    public void onClick(int slot, ClickType type) {
        GuiLayout layout = gui.layout();
        if (slot == layout.listBackSlot) {
            gui.openMain(viewer);
            return;
        }
        if (slot == layout.listSellAllSlot) {
            gui.trade(viewer).sellAll(viewer);
            render();
            return;
        }
        ShopItem item = slotItems.get(slot);
        if (item == null) return;
        switch (type) {
            case LEFT -> gui.trade(viewer).sell(viewer, item, 64);
            case RIGHT, SHIFT_LEFT, SHIFT_RIGHT -> gui.trade(viewer).sell(viewer, item, -1);
            default -> { }
        }
        render();
    }
}
