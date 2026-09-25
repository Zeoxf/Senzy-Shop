package id.senzy.shop;

import id.senzy.shop.command.CommandBridge;
import id.senzy.shop.command.SenzyAdminCommand;
import id.senzy.shop.command.SenzyCommand;
import id.senzy.shop.contract.ContractManager;
import id.senzy.shop.database.ContractRepository;
import id.senzy.shop.database.DatabaseManager;
import id.senzy.shop.database.MetaRepository;
import id.senzy.shop.database.PlayerRepository;
import id.senzy.shop.database.StockRepository;
import id.senzy.shop.database.TransactionRepository;
import id.senzy.shop.economy.BalanceManager;
import id.senzy.shop.economy.EconomyManager;
import id.senzy.shop.gui.GuiLayout;
import id.senzy.shop.gui.GuiManager;
import id.senzy.shop.listener.InventoryListener;
import id.senzy.shop.listener.PlayerListener;
import id.senzy.shop.restock.RestockManager;
import id.senzy.shop.shop.ShopManager;
import id.senzy.shop.shop.StockManager;
import id.senzy.shop.shop.TradeService;
import id.senzy.shop.util.MessageUtil;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Objects;
import java.util.logging.Level;

/** Hanya bootstrap: membuat dependensi dan menyambungkannya lewat constructor. */
public final class SenzyShop extends JavaPlugin {
    private MessageUtil messages;
    private DatabaseManager database;
    private ShopManager shop;
    private StockManager stock;
    private RestockManager restock;
    private ContractManager contracts;
    private GuiLayout layout;
    private GuiManager guis;

    // Dipakai untuk memulihkan command /senzy milik plugin lain jika SenzyShop di-disable,
    // supaya bridge yang menunjuk ke manager yang sudah mati tidak tertinggal di sana.
    private PluginCommand bridgedCommand;
    private CommandExecutor bridgedOriginalExecutor;
    private TabCompleter bridgedOriginalTabCompleter;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        for (String file : new String[]{"items.yml", "contracts.yml", "messages.yml", "database.yml"}) {
            if (!new File(getDataFolder(), file).exists()) saveResource(file, false);
        }
        messages = new MessageUtil(this);
        messages.reload();

        database = new DatabaseManager(this);
        try {
            database.connect();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Gagal membuka database. Plugin dimatikan.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            PlayerRepository playerRepo = new PlayerRepository();
            StockRepository stockRepo = new StockRepository();
            TransactionRepository transactionRepo = new TransactionRepository();
            MetaRepository metaRepo = new MetaRepository();
            ContractRepository contractRepo = new ContractRepository();

            BalanceManager balances = new BalanceManager();
            balances.load(database.supply(playerRepo::loadAll).join());
            EconomyManager economy = new EconomyManager(this, balances, database, playerRepo, transactionRepo);

            shop = new ShopManager(this);
            shop.load();
            stock = new StockManager(shop, database, stockRepo, transactionRepo);
            stock.load();
            restock = new RestockManager(this, shop, stock, database, stockRepo, metaRepo, messages);

            contracts = new ContractManager(this, shop, economy, database, contractRepo, metaRepo,
                    playerRepo, transactionRepo, messages);

            TradeService trade = new TradeService(shop, stock, economy, database, playerRepo, stockRepo,
                    transactionRepo, messages, contracts);

            layout = new GuiLayout(this);
            layout.reload();
            guis = new GuiManager(this, layout, shop, stock, economy, trade, restock, contracts, messages);
            restock.setHooks(guis::tickClocks, guis::refreshAll);
            restock.start();
            contracts.start();

            SenzyAdminCommand adminCommand = new SenzyAdminCommand(this, economy, shop, stock, restock,
                    database, transactionRepo, guis, messages);
            SenzyCommand command = new SenzyCommand(economy, guis, trade, restock, adminCommand, messages);
            PluginCommand senzy = Objects.requireNonNull(getCommand("senzy"), "command senzy tidak ada di plugin.yml");
            senzy.setExecutor(command);
            senzy.setTabCompleter(command);
            bridgeWithExistingSenzyCommand(command);

            getServer().getPluginManager().registerEvents(new InventoryListener(guis), this);
            getServer().getPluginManager().registerEvents(new PlayerListener(economy, contracts, guis), this);

            for (Player online : getServer().getOnlinePlayers()) {
                boolean created = economy.ensureAccount(online.getUniqueId(), online.getName());
                if (created) economy.restoreFromPdcIfPresent(online);
                economy.syncPdc(online);
            }
        } catch (RuntimeException e) {
            getLogger().log(Level.SEVERE, "Gagal menginisialisasi SenzyShop. Plugin dimatikan.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("SenzyShop V" + getPluginMeta().getVersion() + " aktif.");
    }

    @Override
    public void onDisable() {
        if (bridgedCommand != null) {
            // Kembalikan /senzy ke keadaan semula milik plugin lain, jangan tinggalkan bridge
            // yang menunjuk ke manager SenzyShop yang sudah dimatikan.
            bridgedCommand.setExecutor(bridgedOriginalExecutor);
            bridgedCommand.setTabCompleter(bridgedOriginalTabCompleter);
            bridgedCommand = null;
        }
        if (contracts != null) contracts.stop();
        if (restock != null) restock.stop();
        if (guis != null) guis.closeAll();
        if (database != null) database.close();   // menunggu semua penulisan database selesai
    }

    /**
     * Jika ada plugin lain (mis. "SenzyXPRLoot" - XPR Boost Progression + LootBox) yang sudah
     * memegang alias /senzy, sambungkan ke sana lewat {@link CommandBridge} alih-alih membiarkan
     * SenzyShop diam-diam hanya bisa diakses lewat "/senzyshop:senzy". Plugin lain itu TIDAK
     * diubah sama sekali - hanya executor & tab-completer yang sedang terpasang di alias "senzy"
     * dibungkus. softdepend: [SenzyXPRLoot] di plugin.yml memastikan SenzyXPRLoot (jika terpasang)
     * sudah aktif duluan, sehingga di sinilah dia sudah memegang alias bare "senzy". SenzyXPRLoot
     * sendiri juga sudah mendeklarasikan loadbefore: [SenzyShop] - keduanya sengaja dipasang
     * (dobel jaminan) supaya urutan load tidak bergantung pada satu sisi saja.
     */
    private void bridgeWithExistingSenzyCommand(SenzyCommand ourCommand) {
        PluginCommand active = getServer().getPluginCommand("senzy");
        if (active == null || active.getPlugin() == this) {
            return; // berdiri sendiri: tidak ada plugin lain yang memegang alias /senzy
        }
        CommandExecutor originalExecutor = active.getExecutor();
        TabCompleter originalTab = active.getTabCompleter();
        CommandBridge bridge = new CommandBridge(originalExecutor, originalTab, ourCommand);
        active.setExecutor(bridge);
        active.setTabCompleter(bridge);
        bridgedCommand = active;
        bridgedOriginalExecutor = originalExecutor;
        bridgedOriginalTabCompleter = originalTab;
        getLogger().info("Menyambung ke /senzy milik plugin '" + active.getPlugin().getName()
                + "': subcommand shop/balance/sell/sellall/contract/restock/admin kini aktif di sana juga, "
                + "tanpa mengubah plugin tersebut.");
    }

    /** Reload aman: tutup semua GUI yang terbuka lebih dulu, lalu muat ulang config/pesan/item. */
    public void reloadAll() {
        guis.closeAll();
        reloadConfig();
        messages.reload();
        layout.reload();
        shop.load();
        stock.reconcile();
        restock.onConfigReload();
    }
}
