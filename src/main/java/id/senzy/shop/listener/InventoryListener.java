package id.senzy.shop.listener;

import id.senzy.shop.gui.AbstractGui;
import id.senzy.shop.gui.GuiManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Pengaman GUI: SEMUA interaksi di inventory milik SenzyShop di-cancel (shift-click, number key,
 * double click, drag, dst), lalu hanya klik yang valid + lolos cooldown yang diteruskan ke GUI.
 */
public final class InventoryListener implements Listener {
    private final GuiManager guis;
    private final Map<UUID, Long> lastClick = new HashMap<>();

    public InventoryListener(GuiManager guis) {
        this.guis = guis;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof AbstractGui gui)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (gui.viewer() != player) return;
        Inventory clicked = event.getClickedInventory();
        if (clicked == null || clicked != top) return;

        ClickType type = event.getClick();
        if (type != ClickType.LEFT && type != ClickType.RIGHT
                && type != ClickType.SHIFT_LEFT && type != ClickType.SHIFT_RIGHT) return;

        long now = System.currentTimeMillis();
        Long last = lastClick.get(player.getUniqueId());
        if (last != null && now - last < guis.layout().clickCooldownMs) return;
        lastClick.put(player.getUniqueId(), now);

        gui.onClick(event.getSlot(), type);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof AbstractGui) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastClick.remove(event.getPlayer().getUniqueId());
    }
}
