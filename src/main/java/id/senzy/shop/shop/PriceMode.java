package id.senzy.shop.shop;

import java.util.Locale;

/** Cara harga item MATERIAL dihitung. Item non-MATERIAL selalu berperilaku seperti FIXED. */
public enum PriceMode {
    /** Harga = harga ore acuan x multiplier (dan sell-ratio untuk harga jual). */
    ORE_MULTIPLIER,
    /** Harga statis, diambil langsung dari buy-price/sell-price di items.yml. */
    FIXED;

    public static PriceMode fromString(String value, PriceMode fallback) {
        if (value == null) return fallback;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
