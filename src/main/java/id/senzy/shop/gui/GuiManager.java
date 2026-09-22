package id.senzy.shop.gui;

import id.senzy.shop.SenzyShop;
import id.senzy.shop.economy.EconomyManager;
import id.senzy.shop.restock.RestockManager;
import id.senzy.shop.shop.ShopCategory;
import id.senzy.shop.shop.ShopManager;
import id.senzy.shop.shop.StockManager;
import id.senzy.shop.shop.TradeService;
import id.senzy.shop.util.ItemUtil;
import id.senzy.shop.util.MessageUtil;
import id.senzy.shop.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/** Pintu masuk semua GUI + akses ke dependensi bersama. */
public final class GuiManager {
    private final SenzyShop plugin;
    private final GuiLayout layout;
    private final ShopManager shop;
    private final StockManager stock;
    private final EconomyManager economy;
    private final TradeService trade;
    private final RestockManager restock;
    private final MessageUtil messages;

    public GuiManager(SenzyShop plugin, GuiLayout layout, ShopManager shop, StockManager stock,
                      EconomyManager economy, TradeService trade, RestockManager restock, MessageUtil messages) {
        this.plugin = plugin;
        this.layout = layout;
        this.shop = shop;
        this.stock = stock;
        this.economy = economy;
        this.trade = trade;
        this.restock = restock;
        this.messages = messages;
    }

    public GuiLayout layout() { return layout; }
    public ShopManager shop() { return shop; }
    public StockManager stock() { return stock; }
    public EconomyManager economy() { return economy; }
    public TradeService trade() { return trade; }
    public RestockManager restock() { return restock; }
    public MessageUtil messages() { return messages; }

    /** Jalankan di tick berikutnya (aman dipanggil dari dalam InventoryClickEvent). */
    public void later(Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }

    public void openMain(Player player) {
        later(() -> {
            if (player.isOnline()) new MainShopGUI(this, player).open();
        });
    }

    public void openCategory(Player player, ShopCategory category, int page) {
        later(() -> {
            if (player.isOnline()) new CategoryGUI(this, player, category, page).open();
        });
    }

    public void openSell(Player player) {
        later(() -> {
            if (player.isOnline()) new SellGUI(this, player).open();
        });
    }

    public void closeAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (holderOf(p) != null) p.closeInventory();
        }
    }

    public void refreshAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            AbstractGui g = holderOf(p);
            if (g != null) g.refresh();
        }
    }

    public void tickClocks() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            AbstractGui g = holderOf(p);
            if (g != null) g.updateClock();
        }
    }

    public static AbstractGui holderOf(Player player) {
        InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder(false);
        return holder instanceof AbstractGui g ? g : null;
    }

    // ---- icon bersama ----

    public ItemStack balanceIcon(Player player) {
        return ItemUtil.icon(Material.GOLD_INGOT, 1,
                messages.component("gui.main.balance-name", "balance", messages.money(economy.getBalance(player.getUniqueId()))),
                messages.list("gui.main.balance-lore"));
    }

    public ItemStack restockIcon() {
        return ItemUtil.icon(Material.CLOCK, 1,
                messages.component("gui.main.restock-name", "time", TimeUtil.formatDuration(restock.remainingMillis())),
                messages.list("gui.main.restock-lore"));
    }
}
