package id.senzy.shop.gui;

import id.senzy.shop.contract.ContractType;
import id.senzy.shop.database.ContractProgressRecord;
import id.senzy.shop.database.ContractRecord;
import id.senzy.shop.shop.ShopItem;
import id.senzy.shop.util.ItemUtil;
import id.senzy.shop.util.MessageUtil;
import id.senzy.shop.util.TimeUtil;
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

/** Daftar kontrak Daily/Weekly dengan progress bar + tombol claim. */
public final class ContractGUI extends AbstractGui {
    private static final int BAR_SEGMENTS = 10;

    private ContractType activeTab;
    private final Map<Integer, ContractRecord> slotContracts = new HashMap<>();

    public ContractGUI(GuiManager gui, Player viewer, ContractType activeTab) {
        super(gui, viewer);
        this.activeTab = activeTab;
    }

    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(this, 54, gui.messages().component("gui.contract.title"));
    }

    @Override
    public void render() {
        GuiLayout layout = gui.layout();
        MessageUtil m = gui.messages();
        inventory.clear();
        fillBackground();
        slotContracts.clear();
        gui.contracts().ensurePlayerLoaded(viewer.getUniqueId());

        set(layout.contractDailyTabSlot, tabIcon(ContractType.DAILY, m));
        set(layout.contractWeeklyTabSlot, tabIcon(ContractType.WEEKLY, m));

        List<ContractRecord> list = gui.contracts().active(activeTab);
        List<Integer> slots = layout.contractSlots;
        if (list.isEmpty()) {
            m.send(viewer, "contract.none", "type", activeTab == ContractType.DAILY ? "harian" : "mingguan");
        }
        for (int i = 0; i < slots.size() && i < list.size(); i++) {
            ContractRecord contract = list.get(i);
            int slot = slots.get(i);
            slotContracts.put(slot, contract);
            set(slot, buildIcon(contract, m));
        }
        set(layout.contractBackSlot, ItemUtil.icon(Material.BARRIER, 1, m.component("gui.category.back"), null));
    }

    private ItemStack tabIcon(ContractType type, MessageUtil m) {
        String key = type == ContractType.DAILY ? "gui.contract.daily-tab-name" : "gui.contract.weekly-tab-name";
        Material material = type == activeTab
                ? (type == ContractType.DAILY ? Material.LIME_DYE : Material.LIGHT_BLUE_DYE)
                : Material.GRAY_DYE;
        long remaining = gui.contracts().remainingMillis(type);
        return ItemUtil.icon(material, 1, m.component(key), m.list("gui.contract.tab-lore",
                "time", TimeUtil.formatDuration(remaining)));
    }

    private ItemStack buildIcon(ContractRecord contract, MessageUtil m) {
        ShopItem targetItem = gui.shop(viewer).getById(contract.target());
        Material icon = targetItem != null ? targetItem.material() : Material.PAPER;
        ContractProgressRecord progress = gui.contracts().progressFor(viewer.getUniqueId(), contract.id());
        long current = progress != null ? progress.progress() : 0L;
        boolean completed = progress != null && progress.completed();
        boolean claimed = progress != null && progress.claimed();

        String bar = progressBar(current, contract.requiredAmount());
        String statusKey = claimed ? "gui.contract.status-claimed"
                : completed ? "gui.contract.status-completed" : "gui.contract.status-progress";
        String itemName = targetItem != null ? ItemUtil.prettyName(targetItem.material()) : contract.target();

        List<Component> lore = m.list("gui.contract.item-lore",
                "target", itemName,
                "amount", contract.requiredAmount(),
                "progress", current,
                "bar", bar,
                "reward", m.money(contract.reward()),
                "status", m.raw(statusKey));
        return ItemUtil.icon(icon, 1, m.component("gui.contract.item-name", "key", contract.contractKey()), lore);
    }

    private String progressBar(long current, long required) {
        int filled = required <= 0 ? 0 : (int) Math.round((double) current / required * BAR_SEGMENTS);
        filled = Math.max(0, Math.min(BAR_SEGMENTS, filled));
        StringBuilder sb = new StringBuilder("&a");
        for (int i = 0; i < BAR_SEGMENTS; i++) {
            if (i == filled) sb.append("&7");
            sb.append(i < filled ? '\u25AE' : '\u25AF');
        }
        return sb.toString();
    }

    @Override
    public void updateClock() {
        // Countdown kontrak tidak perlu update per detik seperti restock; cukup saat GUI dibuka ulang.
    }

    @Override
    public void onClick(int slot, ClickType type) {
        GuiLayout layout = gui.layout();
        if (slot == layout.contractBackSlot) {
            gui.openMain(viewer);
            return;
        }
        if (slot == layout.contractDailyTabSlot) {
            activeTab = ContractType.DAILY;
            render();
            return;
        }
        if (slot == layout.contractWeeklyTabSlot) {
            activeTab = ContractType.WEEKLY;
            render();
            return;
        }
        ContractRecord contract = slotContracts.get(slot);
        if (contract == null) return;
        gui.contracts().claim(viewer, contract.id());
        render();
    }
}
