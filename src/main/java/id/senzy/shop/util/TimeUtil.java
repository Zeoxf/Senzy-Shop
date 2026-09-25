package id.senzy.shop.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class TimeUtil {
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private TimeUtil() {}

    /** Format HH:MM:SS. */
    public static String formatDuration(long millis) {
        long s = Math.max(0L, millis) / 1000L;
        return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }

    public static String formatDateTime(long epochMillis) {
        return DATE_TIME.format(Instant.ofEpochMilli(epochMillis));
    }

    public static String formatInterval(long minutes) {
        if (minutes >= 60 && minutes % 60 == 0) return (minutes / 60) + " jam";
        return minutes + " menit";
    }
}
