package id.senzy.shop.shop;

import org.bukkit.Material;

import java.util.Locale;
import java.util.Objects;

/** Kategori shop bersifat dinamis dan dibaca dari shop/<shop>/message.yml. */
public final class ShopCategory {
    private final String id;
    private final String displayName;
    private final Material icon;
    private final int slot;

    public ShopCategory(String id, String displayName, Material icon, int slot) {
        this.id = normalize(id);
        this.displayName = displayName == null || displayName.isBlank() ? this.id : displayName;
        this.icon = icon == null ? Material.CHEST : icon;
        this.slot = Math.max(0, Math.min(53, slot));
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public Material icon() { return icon; }
    public int slot() { return slot; }

    public static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    @Override public boolean equals(Object o) {
        return o instanceof ShopCategory other && id.equals(other.id);
    }
    @Override public int hashCode() { return Objects.hash(id); }
    @Override public String toString() { return id; }
}
