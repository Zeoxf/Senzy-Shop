package id.senzy.shop.shop;

import org.bukkit.Material;

import java.util.Locale;

public enum ShopCategory {
    NATURAL("&2&lNatural", Material.GRASS_BLOCK),
    ORE("&b&lOre", Material.DIAMOND),
    FARMING("&e&lFarming", Material.WHEAT),
    ANIMALS("&6&lAnimals", Material.BEEF),
    FOOD("&c&lFood", Material.BREAD);

    private final String displayName;
    private final Material icon;

    ShopCategory(String displayName, Material icon) {
        this.displayName = displayName;
        this.icon = icon;
    }

    public String displayName() {
        return displayName;
    }

    public Material icon() {
        return icon;
    }

    public static ShopCategory fromString(String value) {
        if (value == null) return null;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
