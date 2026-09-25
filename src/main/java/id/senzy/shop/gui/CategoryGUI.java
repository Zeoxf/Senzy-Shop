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

/**
 * Satu kategori. Setiap item bisa menentukan posisinya dengan dua cara, dan boleh dicampur bebas
 * dalam satu file kategori:
 *
 *  1. "slot: N"  - posisi absolut. Selama N < jumlah slot per halaman (gui.list.item-slots,
 *                  default 45 slot / 5 baris), item muncul di halaman 1 pada slot itu persis,
 *                  sama seperti sebelumnya (lihat blocks.yml).
 *                  Begitu N melebihi kapasitas 1 halaman, sisa lebihnya OTOMATIS dipindah ke
 *                  halaman berikutnya: halaman = (N / perPage) + 1, slot lokal = N % perPage.
 *                  Contoh dengan perPage=45: slot: 45 -> halaman 2 slot 0, slot: 90 -> halaman 3 slot 0.
 *  2. "page: N"  - dipakai bersama "slot" yang MASIH di dalam batas 1 halaman (< perPage), untuk
 *                  menaruh item itu langsung di halaman N tanpa perlu menghitung angka slot besar.
 *                  Default "page" = 1 kalau tidak ditulis.
 *
 *  PRIORITAS: kalau "slot" sudah overflow (>= perPage), halaman hasil hitungan otomatis dari slot
 *  itu SELALU menang dan field "page" diabaikan untuk item tersebut. "page" hanya berlaku selama
 *  "slot" masih dalam rentang satu halaman. Kalau dua item (baik lewat slot overflow, page manual,
 *  atau campuran keduanya) berakhir di halaman+slot lokal yang sama, salah satu akan tertimpa di
 *  render (item terakhir yang diproses yang tampil) - jadi hindari pemakaian bertabrakan.
 *
 *  Baris terakhir dari total 6 baris chest selalu dicadangkan untuk restock, tombol halaman
 *  sebelumnya/berikutnya, kembali, dan balance - baris ini tidak pernah dipakai untuk item.
 *  Jumlah baris yang dicadangkan bisa diperbesar lewat "gui.list.item-rows" di config.yml (mis. ubah
 *  ke 4 supaya 2 baris bawah yang dicadangkan, bukan cuma 1).
 */
public final class CategoryGUI extends AbstractGui {
    private final ShopCategory category;
    private final int page;
    private final Map<Integer, ShopItem> slotItems = new HashMap<>();
    private List<ShopItem> items;

    public CategoryGUI(GuiManager gui, Player viewer, ShopCategory category, int page) {
        super(gui, viewer);
        this.category = category;
        this.page = Math.max(0, page);
    }

    private List<ShopItem> items() {
        if (items == null) items = gui.shop().byCategory(category);
        return items;
    }

    /** Jumlah slot yang tersedia untuk item dalam satu halaman (sisanya dicadangkan untuk navigasi). */
    private int perPage() {
        return Math.max(1, gui.layout().itemSlots.size());
    }

    /** Halaman 1-based hasil resolusi "slot"/"page" milik satu item; slot overflow menang atas "page". */
    private int resolvedPage(ShopItem item) {
        int perPage = perPage();
        if (item.slot() >= perPage) return (item.slot() / perPage) + 1;
        return Math.max(1, item.page());
    }

    /** Slot lokal (0..perPage-1) tempat item digambar di dalam halaman hasil resolusi di atas. */
    private int resolvedSlot(ShopItem item) {
        int perPage = perPage();
        if (item.slot() >= perPage) return item.slot() % perPage;
        return item.slot();
    }

    /** Total halaman kategori ini = halaman tertinggi yang dipakai oleh item aktif, minimal 1. */
    private int pages() {
        int max = 1;
        for (ShopItem item : items()) if (item.enabled()) max = Math.max(max, resolvedPage(item));
        return max;
    }

    private int currentPage() {
        return Math.min(page, pages() - 1);
    }

    @Override protected Inventory createInventory() {
        int shownPage = currentPage() + 1;
        return Bukkit.createInventory(this, 54, gui.messages().component("gui.category.title",
                "category", category.displayName(), "page", shownPage, "pages", pages()));
    }

    @Override public void render() {
        GuiLayout layout = gui.layout();
        MessageUtil m = gui.messages();
        inventory.clear();
        fillBackground();
        slotItems.clear();

        int perPage = perPage();
        List<Integer> slots = layout.itemSlots;
        for (ShopItem item : items()) {
            if (!item.enabled()) continue;
            if (resolvedPage(item) - 1 != currentPage()) continue;
            int local = resolvedSlot(item);
            if (local < 0 || local >= perPage || local >= slots.size()) continue;
            int actualSlot = slots.get(local);
            slotItems.put(actualSlot, item);
            set(actualSlot, buildIcon(item));
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
        List<Component> lore = m.list("gui.item-lore", "stockcolor", stock > 0 ? "&a" : "&c", "stock", stock,
                "max", gui.stock().getMax(item), "buy", item.canBuy() ? m.money(gui.events().buyPrice(item)) : "-",
                "sell", item.canSell() ? m.money(gui.events().sellPrice(item)) : "-");
        return ItemUtil.icon(item.material(), Math.max(1, Math.min(item.material().getMaxStackSize(), Math.max(stock, 1))), MessageUtil.color(item.displayName()), lore);
    }

    @Override public void updateClock() { set(gui.layout().listRestockSlot, gui.restockIcon()); }

    @Override public void onClick(int slot, ClickType type) {
        GuiLayout layout = gui.layout();
        if (slot == layout.listBackSlot) { gui.openMain(viewer); return; }
        if (slot == layout.listPrevSlot && currentPage() > 0) { gui.openCategory(viewer, category, currentPage() - 1); return; }
        if (slot == layout.listNextSlot && currentPage() < pages() - 1) { gui.openCategory(viewer, category, currentPage() + 1); return; }
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
