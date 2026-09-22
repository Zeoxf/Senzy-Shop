package id.senzy.shop.command;

import id.senzy.shop.SenzyShop;
import id.senzy.shop.database.DatabaseManager;
import id.senzy.shop.database.TransactionRecord;
import id.senzy.shop.database.TransactionRepository;
import id.senzy.shop.economy.EconomyManager;
import id.senzy.shop.gui.GuiManager;
import id.senzy.shop.restock.RestockManager;
import id.senzy.shop.shop.ShopItem;
import id.senzy.shop.shop.ShopManager;
import id.senzy.shop.shop.StockManager;
import id.senzy.shop.util.MessageUtil;
import id.senzy.shop.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Semua subcommand /senzy admin ... (permission senzy.admin). */
public final class SenzyAdminCommand {
    private static final List<String> SUBS = List.of("reload", "restock", "setbalance", "addbalance",
            "removebalance", "setstock", "logs");

    private final SenzyShop plugin;
    private final EconomyManager economy;
    private final ShopManager shop;
    private final StockManager stock;
    private final RestockManager restock;
    private final DatabaseManager db;
    private final TransactionRepository transactions;
    private final GuiManager guis;
    private final MessageUtil msg;

    public SenzyAdminCommand(SenzyShop plugin, EconomyManager economy, ShopManager shop, StockManager stock,
                             RestockManager restock, DatabaseManager db, TransactionRepository transactions,
                             GuiManager guis, MessageUtil msg) {
        this.plugin = plugin;
        this.economy = economy;
        this.shop = shop;
        this.stock = stock;
        this.restock = restock;
        this.db = db;
        this.transactions = transactions;
        this.guis = guis;
        this.msg = msg;
    }

    /** args = argumen SETELAH kata "admin". */
    public void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("senzy.admin")) {
            msg.send(sender, "general.no-permission");
            return;
        }
        if (args.length == 0) {
            msg.send(sender, "admin.help");
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                plugin.reloadAll();
                msg.send(sender, "admin.reloaded");
            }
            case "restock" -> {
                restock.restockNow();
                msg.send(sender, "admin.restocked");
            }
            case "setbalance", "addbalance", "removebalance" -> balance(sender, sub, args);
            case "setstock" -> setStock(sender, args);
            case "logs" -> logs(sender, args);
            default -> msg.send(sender, "admin.help");
        }
    }

    private void balance(CommandSender sender, String sub, String[] args) {
        if (args.length != 3) {
            msg.send(sender, "admin.usage-balance", "sub", sub);
            return;
        }
        UUID target = economy.findByName(args[1]);
        if (target == null) {
            msg.send(sender, "admin.player-not-found");
            return;
        }
        Long amount = parseLong(args[2]);
        if (amount == null) {
            msg.send(sender, "admin.invalid-amount");
            return;
        }
        String actor = sender.getName();
        boolean ok = switch (sub) {
            case "setbalance" -> economy.adminSetBalance(actor, target, amount);
            case "addbalance" -> economy.adminAddBalance(actor, target, amount);
            default -> economy.adminRemoveBalance(actor, target, amount);
        };
        if (!ok) {
            msg.send(sender, "admin.balance-failed");
            return;
        }
        msg.send(sender, "admin.balance-updated", "player", economy.nameOf(target),
                "balance", msg.money(economy.getBalance(target)));
        guis.refreshAll();
    }

    private void setStock(CommandSender sender, String[] args) {
        if (args.length != 3) {
            msg.send(sender, "admin.usage-stock");
            return;
        }
        ShopItem item = shop.find(args[1]);
        if (item == null) {
            msg.send(sender, "admin.item-not-found");
            return;
        }
        Long amount = parseLong(args[2]);
        if (amount == null || amount > 1_000_000L) {
            msg.send(sender, "admin.invalid-amount");
            return;
        }
        stock.adminSetStock(sender.getName(), item, amount.intValue());
        msg.send(sender, "admin.stock-updated", "item", item.material().name(), "stock", amount);
        guis.refreshAll();
    }

    private void logs(CommandSender sender, String[] args) {
        int limit = 10;
        if (args.length > 1) {
            Long n = parseLong(args[1]);
            if (n != null) limit = (int) Math.max(1, Math.min(50, n));
        }
        final int finalLimit = limit;
        db.supply(c -> transactions.recent(c, finalLimit)).whenComplete((list, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null) {
                        msg.send(sender, "admin.logs-error");
                        return;
                    }
                    if (list.isEmpty()) {
                        msg.send(sender, "admin.logs-empty");
                        return;
                    }
                    msg.send(sender, "admin.logs-header", "count", list.size());
                    for (TransactionRecord r : list) {
                        msg.send(sender, "admin.logs-line",
                                "type", r.type().name(),
                                "player", r.playerName(),
                                "item", r.material() == null ? "-" : r.material(),
                                "amount", r.amount(),
                                "price", msg.money(r.price()),
                                "before", MessageUtil.fmt(r.balanceBefore()),
                                "after", MessageUtil.fmt(r.balanceAfter()),
                                "time", TimeUtil.formatDateTime(r.timestamp()));
                    }
                }));
    }

    public List<String> complete(String[] args) {
        if (args.length == 2) return SenzyCommand.filter(SUBS, args[1]);
        if (args.length == 3) {
            String sub = args[1].toLowerCase(Locale.ROOT);
            if (sub.equals("setbalance") || sub.equals("addbalance") || sub.equals("removebalance")) {
                return SenzyCommand.filter(economy.knownNames(), args[2]);
            }
            if (sub.equals("setstock")) {
                List<String> ids = new ArrayList<>();
                for (ShopItem i : shop.all()) ids.add(i.id());
                return SenzyCommand.filter(ids, args[2]);
            }
        }
        return List.of();
    }

    private static Long parseLong(String text) {
        try {
            long v = Long.parseLong(text.trim());
            return v < 0 ? null : v;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
