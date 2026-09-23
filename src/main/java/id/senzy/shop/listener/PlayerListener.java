package id.senzy.shop.listener;

import id.senzy.shop.contract.ContractManager;
import id.senzy.shop.economy.EconomyManager;
import id.senzy.shop.gui.GuiManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Membuat akun Senzy Coin untuk pemain baru (dan menyinkronkan nama); bersih-bersih saat keluar. */
public final class PlayerListener implements Listener {
    private final EconomyManager economy;
    private final ContractManager contracts;
    private final GuiManager guis;

    public PlayerListener(EconomyManager economy, ContractManager contracts, GuiManager guis) {
        this.economy = economy;
        this.contracts = contracts;
        this.guis = guis;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        economy.ensureAccount(event.getUniqueId(), event.getName());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        economy.ensureAccount(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var uuid = event.getPlayer().getUniqueId();
        contracts.unloadPlayer(uuid);
        guis.cancelSearch(uuid);
    }
}
