package id.senzy.shop.gui;

import id.senzy.shop.util.ItemUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Dasar semua GUI SenzyShop. Holder ini dipakai listener untuk mengenali GUI kita.
 * GUI hanya "menu tampilan": tidak pernah ada item nyata yang berpindah lewat GUI,
 * jadi semua klik di-cancel dan logika dijalankan server-side oleh TradeService.
 */
public abstract class AbstractGui implements InventoryHolder {
    protected final GuiManager gui;
    protected final Player viewer;
    protected Inventory inventory;

    protected AbstractGui(GuiManager gui, Player viewer) {
        this.gui = gui;
        this.viewer = viewer;
    }

    protected abstract Inventory createInventory();

    public abstract void render();

    public abstract void onClick(int slot, ClickType type);

    /** Update ringan untuk countdown (dipanggil tiap detik). */
    public void updateClock() { }

    public void refresh() {
        if (inventory != null) render();
    }

    public Player viewer() {
        return viewer;
    }

    public void open() {
        inventory = createInventory();
        render();
        viewer.openInventory(inventory);
    }

    protected void set(int slot, ItemStack item) {
        if (inventory != null && slot >= 0 && slot < inventory.getSize()) inventory.setItem(slot, item);
    }

    protected void fillBackground() {
        ItemStack filler = ItemUtil.filler(gui.layout().filler);
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler.clone());
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
