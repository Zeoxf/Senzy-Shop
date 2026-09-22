package id.senzy.shop.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ItemUtil {
    private ItemUtil() {}

    /** Item "polos": material sama dan tanpa nama/enchant/lore/metadata apa pun. */
    public static boolean isPlain(ItemStack stack, Material material) {
        return stack != null && stack.getType() == material && stack.isSimilar(new ItemStack(material));
    }

    public static int countPlain(PlayerInventory inv, Material material) {
        int total = 0;
        for (ItemStack s : inv.getStorageContents()) {
            if (isPlain(s, material)) total += s.getAmount();
        }
        return total;
    }

    /** Menghapus sampai {@code amount} item polos. Mengembalikan jumlah yang benar-benar terhapus. */
    public static int removePlain(PlayerInventory inv, Material material, int amount) {
        ItemStack[] contents = inv.getStorageContents();
        int remaining = amount;
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack s = contents[i];
            if (!isPlain(s, material)) continue;
            int take = Math.min(remaining, s.getAmount());
            if (take == s.getAmount()) contents[i] = null;
            else s.setAmount(s.getAmount() - take);
            remaining -= take;
        }
        inv.setStorageContents(contents);
        return amount - remaining;
    }

    /** Ruang kosong (dalam jumlah item) yang tersedia untuk material ini. */
    public static int capacityFor(PlayerInventory inv, Material material) {
        int max = material.getMaxStackSize();
        int space = 0;
        for (ItemStack s : inv.getStorageContents()) {
            if (s == null || s.getType().isAir()) space += max;
            else if (isPlain(s, material)) space += Math.max(0, max - s.getAmount());
        }
        return space;
    }

    /** Memberi item. Mengembalikan jumlah yang TIDAK muat (0 = semua masuk). */
    public static int give(PlayerInventory inv, Material material, int amount) {
        int leftover = 0;
        int remaining = amount;
        int max = Math.max(1, material.getMaxStackSize());
        while (remaining > 0) {
            int n = Math.min(max, remaining);
            Map<Integer, ItemStack> rest = inv.addItem(new ItemStack(material, n));
            for (ItemStack r : rest.values()) leftover += r.getAmount();
            remaining -= n;
        }
        return leftover;
    }

    public static String prettyName(Material material) {
        String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    /** Membuat icon GUI. name == null berarti memakai nama bawaan Minecraft. */
    public static ItemStack icon(Material material, int amount, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material, Math.max(1, Math.min(amount, 64)));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        if (name != null) meta.displayName(name);
        if (lore != null && !lore.isEmpty()) meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack filler(Material material) {
        return icon(material, 1, Component.text(" "), null);
    }
}
