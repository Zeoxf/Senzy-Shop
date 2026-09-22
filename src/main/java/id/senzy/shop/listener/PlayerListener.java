package id.senzy.shop.listener;

import id.senzy.shop.economy.EconomyManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/** Membuat akun Senzy Coin untuk pemain baru (dan menyinkronkan nama). */
public final class PlayerListener implements Listener {
    private final EconomyManager economy;

    public PlayerListener(EconomyManager economy) {
        this.economy = economy;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        economy.ensureAccount(event.getUniqueId(), event.getName());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        economy.ensureAccount(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }
}
