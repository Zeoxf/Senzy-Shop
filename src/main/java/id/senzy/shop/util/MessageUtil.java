package id.senzy.shop.util;

import id.senzy.shop.SenzyShop;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Memuat messages.yml, mengganti placeholder {nama}, dan mengirim pesan berwarna. */
public final class MessageUtil {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final SenzyShop plugin;
    private volatile FileConfiguration messages = new YamlConfiguration();

    public MessageUtil(SenzyShop plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) plugin.saveResource("messages.yml", false);
        messages = YamlConfiguration.loadConfiguration(file);
    }

    public static Component color(String text) {
        return LEGACY.deserialize(text).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static String fmt(long value) {
        return String.format(Locale.US, "%,d", value);
    }

    /** Contoh: "12,500 SC". */
    public String money(long value) {
        return fmt(value) + " " + plugin.getConfig().getString("economy.currency-symbol", "SC");
    }

    public List<String> lines(String key, Object... placeholders) {
        FileConfiguration cfg = messages;
        List<String> raw;
        if (cfg.isList(key)) raw = cfg.getStringList(key);
        else raw = List.of(cfg.getString(key, "&cPesan tidak ditemukan: " + key));
        List<String> out = new ArrayList<>(raw.size());
        for (String line : raw) out.add(replace(line, placeholders));
        return out;
    }

    public String raw(String key, Object... placeholders) {
        return replace(messages.getString(key, "&cPesan tidak ditemukan: " + key), placeholders);
    }

    public Component component(String key, Object... placeholders) {
        return color(raw(key, placeholders));
    }

    public List<Component> list(String key, Object... placeholders) {
        List<Component> out = new ArrayList<>();
        for (String line : lines(key, placeholders)) out.add(color(line));
        return out;
    }

    public void send(CommandSender to, String key, Object... placeholders) {
        for (String line : lines(key, placeholders)) to.sendMessage(color(line));
    }

    public static String replace(String text, Object... placeholders) {
        String result = text;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            result = result.replace("{" + placeholders[i] + "}", String.valueOf(placeholders[i + 1]));
        }
        return result;
    }
}
