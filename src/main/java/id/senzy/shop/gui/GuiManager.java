package id.senzy.shop.gui;

import id.senzy.shop.SenzyShop;
import id.senzy.shop.contract.ContractManager;
import id.senzy.shop.contract.ContractType;
import id.senzy.shop.economy.EconomyManager;
import id.senzy.shop.event.ShopEventManager;
import id.senzy.shop.restock.RestockManager;
import id.senzy.shop.shop.ShopCategory;
import id.senzy.shop.shop.ShopItem;
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

import java.util.Map;
import java.util.UUID;

/** Pintu masuk semua GUI + akses ke dependensi bersama. */
public final class GuiManager {
    private final SenzyShop plugin;
    private final GuiLayout layout;
    private final ShopManager shop;
    private final StockManager stock;
    private final EconomyManager economy;
    private final TradeService trade;
    private final RestockManager restock;
    private final ContractManager contracts;
    private final MessageUtil messages;
    private final ShopEventManager events;
    private final Map<String, id.senzy.shop.shop.ShopRuntime> runtimes;
    private final Map<UUID, String> playerShops = new java.util.HashMap<>();

    public GuiManager(SenzyShop plugin, GuiLayout layout, ShopManager shop, StockManager stock,
                      EconomyManager economy, TradeService trade, RestockManager restock,
                      ContractManager contracts, MessageUtil messages, ShopEventManager events,
                      Map<String, id.senzy.shop.shop.ShopRuntime> runtimes) {
        this.plugin = plugin;
        this.layout = layout;
        this.shop = shop;
        this.stock = stock;
        this.economy = economy;
        this.trade = trade;
        this.restock = restock;
        this.contracts = contracts;
        this.messages = messages;
        this.events = events;
        this.runtimes = runtimes;
    }

    public GuiLayout layout() { return layout; }
    public ShopManager shop() { return shop; }
    public ShopManager shop(Player player) { return runtime(player).shop(); }
    public StockManager stock() { return stock; }
    public StockManager stock(Player player) { return runtime(player).stock(); }
    public EconomyManager economy() { return economy; }
    public TradeService trade() { return trade; }
    public TradeService trade(Player player) { return runtime(player).trade(); }

    public boolean selectShop(Player player, String shopName) {
        String key = shopName == null ? "" : shopName.toLowerCase(java.util.Locale.ROOT);
        if (!runtimes.containsKey(key)) return false;
        playerShops.put(player.getUniqueId(), key);
        return true;
    }

    public java.util.List<String> shopNames() { return java.util.List.copyOf(runtimes.keySet()); }

    public String selectedShop(Player player) {
        return playerShops.getOrDefault(player.getUniqueId(), shop.activeShop());
    }

    private id.senzy.shop.shop.ShopRuntime runtime(Player player) {
        String key = selectedShop(player);
        id.senzy.shop.shop.ShopRuntime runtime = runtimes.get(key);
        return runtime == null ? runtimes.get(shop.activeShop()) : runtime;
    }
    public RestockManager restock() { return restock; }
    public ContractManager contracts() { return contracts; }
    public MessageUtil messages() { return messages; }
    public ShopEventManager events() { return events; }

    /** Jalankan di tick berikutnya (aman dipanggil dari dalam InventoryClickEvent). */
    public void later(Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }

    public void openMain(Player player) {
        openMain(player, selectedShop(player));
    }

    public void openMain(Player player, String shopName) {
        if (!selectShop(player, shopName)) return;
        later(() -> {
            if (player.isOnline()) new MainShopGUI(this, player).open();
        });
    }

    public void openCategory(Player player, ShopCategory category, int page) {
        later(() -> {
            if (player.isOnline() && category != null) new CategoryGUI(this, player, category, page).open();
        });
    }

    public void openSell(Player player) {
        later(() -> {
            if (player.isOnline()) new SellGUI(this, player).open();
        });
    }

    public void openContract(Player player) {
        later(() -> {
            if (player.isOnline()) new ContractGUI(this, player, ContractType.DAILY).open();
        });
    }

    public void openSearchResults(Player player, String query, int page) {
        later(() -> {
            if (player.isOnline()) new SearchGUI(this, player, query, page).open();
        });
    }

    /** Membuka selector jumlah. Nilai awal selalu 1 dan dibatasi stok/inventory/balance. */
    public void openAmountSelector(Player player, ShopItem item) {
        later(() -> {
            if (!player.isOnline()) return;
            int max = trade(player).maxBuyable(player, item);
            if (max <= 0) {
                messages.send(player, "trade.out-of-stock");
                return;
            }
            new AmountSelectorGUI(this, player, item, 1, max).open();
        });
    }

    /** Beli lewat GUI: jika total harga >= ambang batas config, tampilkan dialog konfirmasi dulu. */
    public void attemptBuy(Player player, ShopItem item, int requested) {
        TradeService.BuyPlan plan = trade(player).previewBuy(player, item, requested);
        boolean confirmEnabled = plugin.getConfig().getBoolean("security.confirm-expensive-items", true);
        long threshold = plugin.getConfig().getLong("security.expensive-threshold", 1000L);
        if (plan.ok() && confirmEnabled && plan.cost() >= threshold) {
            later(() -> {
                if (player.isOnline()) new ConfirmGUI(this, player, plan.item(), plan.amount(), plan.cost()).open();
            });
        } else {
            trade(player).buy(player, item, requested);
        }
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
