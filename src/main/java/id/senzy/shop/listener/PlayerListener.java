package id.senzy.shop.listener;

import id.senzy.shop.contract.ContractManager;
import id.senzy.shop.economy.EconomyManager;
import id.senzy.shop.gui.GuiManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Membuat akun Senzy Coin untuk pemain baru (dan menyinkronkan nama); bersih-bersih saat keluar. */
public final class PlayerListener implements Listener {
    private final EconomyManager economy;
    private final ContractManager contracts;
    private final GuiManager guis;

    // UUID yang BARU SAJA dibuatkan akun (baris database tidak ada sebelumnya) saat pre-login,
    // ditandai di sini karena PersistentDataContainer belum bisa diakses sebelum PlayerJoinEvent.
    private final Set<UUID> pendingPdcRestore = ConcurrentHashMap.newKeySet();

    public PlayerListener(EconomyManager economy, ContractManager contracts, GuiManager guis) {
        this.economy = economy;
        this.contracts = contracts;
        this.guis = guis;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        boolean created = economy.ensureAccount(event.getUniqueId(), event.getName());
        if (created) pendingPdcRestore.add(event.getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        UUID uuid = p.getUniqueId();
        economy.ensureAccount(uuid, p.getName()); // idempotent - aman dipanggil lagi di sini

        if (pendingPdcRestore.remove(uuid)) {
            // Akun baru saja dibuat fresh (starting-balance) - kalau PDC pemain masih menyimpan
            // saldo dari instalasi SenzyShop sebelumnya, pulihkan sekarang.
            economy.restoreFromPdcIfPresent(p);
        }
        economy.syncPdc(p); // selalu segarkan cadangan PDC setiap kali online
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var uuid = event.getPlayer().getUniqueId();
        contracts.unloadPlayer(uuid);
        pendingPdcRestore.remove(uuid);
    }
}
