package id.senzy.shop.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Perhitungan harga material dari ore (mis. "1.5x") tanpa menyimpan balance sebagai floating
 * point. Multiplier hanya dipakai sesaat untuk MENGHITUNG harga saat items.yml dimuat; hasilnya
 * langsung dibulatkan ke long dan itulah satu-satunya bentuk yang disimpan/dipakai selanjutnya.
 */
public final class NumberUtil {
    private NumberUtil() {}

    /** round(base * multiplier), pembulatan HALF_UP (75 * 1.5 = 112.5 -> 113). */
    public static long scale(long base, double multiplier) {
        if (base <= 0 || multiplier <= 0) return 0L;
        return BigDecimal.valueOf(base)
                .multiply(BigDecimal.valueOf(multiplier))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    /** round(base * ratio), dipakai untuk sell-ratio (mis. 0.5 dari buy-price). */
    public static long ratio(long base, double ratioValue) {
        return scale(base, ratioValue);
    }

    public static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }
}
