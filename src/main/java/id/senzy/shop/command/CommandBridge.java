package id.senzy.shop.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Menggabungkan /senzy dengan command plugin lain bernama "Senzy" (mis. sistem XPR Boost
 * Progression + LootBox) TANPA mengubah plugin tersebut sama sekali — kita hanya punya jar-nya,
 * bukan source-nya, jadi tidak ada yang di-decompile atau di-recompile.
 *
 * Caranya: kita bungkus (wrap) executor & tab-completer yang SEDANG terpasang di alias "senzy".
 * Argumen pertama yang cocok dengan subcommand SenzyShop (shop, balance, sell, sellall, restock,
 * admin) ditangani oleh SenzyShop. Semua argumen lain (mis. xpr, lootbox, reload, help) diteruskan
 * apa adanya ke executor/tab-completer ASLI, jadi perilaku plugin lain itu tidak berubah sedikit
 * pun.
 */
public final class CommandBridge implements CommandExecutor, TabCompleter {
    private static final Set<String> SHOP_KEYWORDS = Set.of(
            "shop", "balance", "bal", "sell", "sellall", "restock", "admin");

    private final CommandExecutor originalExecutor;
    private final TabCompleter originalTabCompleter;
    private final SenzyCommand shopCommand;

    public CommandBridge(CommandExecutor originalExecutor, TabCompleter originalTabCompleter, SenzyCommand shopCommand) {
        this.originalExecutor = originalExecutor;
        this.originalTabCompleter = originalTabCompleter;
        this.shopCommand = shopCommand;
    }

    private static boolean isShopSubcommand(String[] args) {
        return args.length > 0 && SHOP_KEYWORDS.contains(args[0].toLowerCase(Locale.ROOT));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (isShopSubcommand(args)) {
            return shopCommand.onCommand(sender, command, label, args);
        }
        return originalExecutor != null && originalExecutor.onCommand(sender, command, label, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            // Posisi argumen pertama: gabungkan saran dari SenzyShop + saran dari plugin asli.
            List<String> merged = new ArrayList<>(shopCommand.onTabComplete(sender, command, alias, args));
            if (originalTabCompleter != null) {
                List<String> other = originalTabCompleter.onTabComplete(sender, command, alias, args);
                if (other != null) merged.addAll(other);
            }
            return merged;
        }
        if (isShopSubcommand(args)) {
            return shopCommand.onTabComplete(sender, command, alias, args);
        }
        return originalTabCompleter != null ? originalTabCompleter.onTabComplete(sender, command, alias, args) : List.of();
    }
}
