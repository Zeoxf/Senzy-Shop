package id.senzy.shop.listener;

import id.senzy.shop.gui.GuiManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Menangkap kata kunci pencarian lewat chat (tanpa dependency eksternal - AsyncChatEvent bawaan
 * Paper). Pesan chat pemain yang sedang dalam mode pencarian di-cancel (tidak tersiar ke server)
 * dan dipakai sebagai query, lalu SearchGUI dibuka.
 */
public final class SearchListener implements Listener {
    private final GuiManager guis;

    public SearchListener(GuiManager guis) {
        this.guis = guis;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!guis.isAwaitingSearch(player.getUniqueId())) return;
        event.setCancelled(true);
        String raw = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        guis.later(() -> {
            guis.cancelSearch(player.getUniqueId());
            if (!player.isOnline()) return;
            if (raw.equalsIgnoreCase("batal")) {
                guis.messages().send(player, "search.cancelled");
                return;
            }
            if (raw.isBlank()) {
                guis.messages().send(player, "search.empty-query");
                return;
            }
            guis.openSearchResults(player, raw, 0);
        });
    }
}
