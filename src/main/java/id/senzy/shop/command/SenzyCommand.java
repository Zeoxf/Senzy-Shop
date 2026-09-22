package id.senzy.shop.command;

import id.senzy.shop.economy.EconomyManager;
import id.senzy.shop.gui.GuiManager;
import id.senzy.shop.restock.RestockManager;
import id.senzy.shop.shop.TradeService;
import id.senzy.shop.util.MessageUtil;
import id.senzy.shop.util.TimeUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** /senzy [shop|balance|sell|sellall|restock|admin]. */
public final class SenzyCommand implements CommandExecutor, TabCompleter {
    private final EconomyManager economy;
    private final GuiManager guis;
    private final TradeService trade;
    private final RestockManager restock;
    private final SenzyAdminCommand admin;
    private final MessageUtil msg;

    public SenzyCommand(EconomyManager economy, GuiManager guis, TradeService trade, RestockManager restock,
                        SenzyAdminCommand admin, MessageUtil msg) {
        this.economy = economy;
        this.guis = guis;
        this.trade = trade;
        this.restock = restock;
        this.admin = admin;
        this.msg = msg;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("senzy.use")) {
            msg.send(sender, "general.no-permission");
            return true;
        }
        if (args.length == 0) {
            msg.send(sender, "general.help");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "shop" -> {
                Player p = requirePlayer(sender, "senzy.shop");
                if (p != null) guis.openMain(p);
            }
            case "balance", "bal" -> {
                Player p = requirePlayer(sender, "senzy.balance");
                if (p != null) msg.send(p, "balance.show", "balance", msg.money(economy.getBalance(p.getUniqueId())));
            }
            case "sell" -> {
                Player p = requirePlayer(sender, "senzy.sell");
                if (p != null) guis.openSell(p);
            }
            case "sellall" -> {
                Player p = requirePlayer(sender, "senzy.sell");
                if (p != null) trade.sellAll(p);
            }
            case "restock" -> {
                if (!sender.hasPermission("senzy.shop")) {
                    msg.send(sender, "general.no-permission");
                } else {
                    msg.send(sender, "restock.info", "time", TimeUtil.formatDuration(restock.remainingMillis()));
                }
            }
            case "admin" -> admin.execute(sender, Arrays.copyOfRange(args, 1, args.length));
            default -> msg.send(sender, "general.help");
        }
        return true;
    }

    private Player requirePlayer(CommandSender sender, String permission) {
        if (!(sender instanceof Player player)) {
            msg.send(sender, "general.players-only");
            return null;
        }
        if (!player.hasPermission(permission)) {
            msg.send(player, "general.no-permission");
            return null;
        }
        return player;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("shop", "balance", "sell", "sellall", "restock"));
            if (sender.hasPermission("senzy.admin")) subs.add("admin");
            return filter(subs, args[0]);
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("admin") && sender.hasPermission("senzy.admin")) {
            return admin.complete(args);
        }
        return List.of();
    }

    static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) if (o.toLowerCase(Locale.ROOT).startsWith(p)) out.add(o);
        return out;
    }
}
