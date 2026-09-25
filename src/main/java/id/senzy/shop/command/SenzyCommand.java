package id.senzy.shop.command;

import id.senzy.shop.economy.EconomyManager;
import id.senzy.shop.gui.GuiManager;
import id.senzy.shop.restock.RestockManager;
import id.senzy.shop.shop.TradeService;
import id.senzy.shop.shop.ShopManager;
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

/** /senzy [shop|balance|sell|sellall|contract|restock|admin]. */
public final class SenzyCommand implements CommandExecutor, TabCompleter {
    private final EconomyManager economy;
    private final GuiManager guis;
    private final TradeService trade;
    private final RestockManager restock;
    private final SenzyAdminCommand admin;
    private final MessageUtil msg;
    private final ShopManager shop;

    public SenzyCommand(EconomyManager economy, GuiManager guis, TradeService trade, RestockManager restock,
                        SenzyAdminCommand admin, MessageUtil msg, ShopManager shop) {
        this.economy = economy;
        this.guis = guis;
        this.trade = trade;
        this.restock = restock;
        this.admin = admin;
        this.msg = msg;
        this.shop = shop;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean adminAccess = isAdmin(sender);
        if (!sender.hasPermission("senzy.use") && !adminAccess) {
            msg.send(sender, "general.no-permission");
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender, adminAccess);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help", "?" -> sendHelp(sender, adminAccess);
            case "shop" -> {
                if (args.length >= 2 && isShopConfigCommand(args[1])) {
                    if (!sender.hasPermission("senzy.shop.admin") && !sender.hasPermission("senzy.admin")) {
                        msg.send(sender, "general.no-permission");
                    } else {
                        handleShopConfig(sender, args);
                    }
                    break;
                }
                if (args.length >= 3 && args[1].equalsIgnoreCase("admin") && args[2].equalsIgnoreCase("event")) {
                    if (!sender.hasPermission("senzy.shop.admin")) {
                        msg.send(sender, "general.no-permission");
                    } else {
                        String[] eventArgs = Arrays.copyOfRange(args, 3, args.length);
                        admin.execute(sender, prepend("event", eventArgs));
                    }
                    break;
                }
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
            case "contract" -> {
                Player p = requirePlayer(sender, "senzy.contract");
                if (p != null) guis.openContract(p);
            }
            case "shop-search" -> {
                Player p = requirePlayer(sender, "senzy.shop");
                if (p == null) break;
                if (args.length < 2 || String.join(" ", Arrays.copyOfRange(args, 1, args.length)).isBlank()) {
                    msg.send(p, "search.empty-query");
                    break;
                }
                // Buka langsung peti hasil pencarian - TIDAK lewat chat-capture (SearchListener),
                // supaya tidak bentrok dengan plugin chat lain dan tidak ada jeda tunggu-input.
                String query = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                guis.openSearchResults(p, query, 0);
            }
            case "restock" -> {
                if (!sender.hasPermission("senzy.shop")) {
                    msg.send(sender, "general.no-permission");
                } else {
                    msg.send(sender, "restock.info", "time", TimeUtil.formatDuration(restock.remainingMillis()));
                }
            }
            case "admin" -> admin.execute(sender, Arrays.copyOfRange(args, 1, args.length));
            default -> sendHelp(sender, adminAccess);
        }
        return true;
    }

    private boolean isAdmin(CommandSender sender) {
        return sender.hasPermission("senzy.admin") || sender.hasPermission("senzy.shop.admin");
    }

    private void sendHelp(CommandSender sender, boolean adminAccess) {
        msg.send(sender, "general.help");
        if (adminAccess) msg.send(sender, "admin.help");
    }

    private boolean isShopConfigCommand(String s) {
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "addshop", "addcategory", "additem", "setbuy", "setsell" -> true;
            default -> false;
        };
    }

    private void handleShopConfig(CommandSender sender, String[] args) {
        String sub = args[1].toLowerCase(Locale.ROOT);
        try {
            switch (sub) {
                case "addshop" -> {
                    if (args.length != 3) { sender.sendMessage("§c/senzy shop addshop <name>"); return; }
                    sender.sendMessage(shop.createShop(args[2]) ? "§aShop dibuat: §f" + args[2] : "§cShop sudah ada atau nama tidak valid.");
                }
                case "addcategory" -> {
                    if (args.length != 5) { sender.sendMessage("§c/senzy shop addcategory <slot> <texture> <name>"); return; }
                    int slot = Integer.parseInt(args[2]);
                    sender.sendMessage(shop.addCategory(slot, args[3], args[4]) ? "§aKategori ditambahkan: §f" + args[4] : "§cGagal menambah kategori.");
                }
                case "additem" -> {
                    if (args.length != 8) { sender.sendMessage("§c/senzy shop additem <shop> <category> <slot> <item> <amount> <chance>"); return; }
                    int slot=Integer.parseInt(args[4]); int amount=Integer.parseInt(args[6]); double chance=Double.parseDouble(args[7]);
                    sender.sendMessage(shop.addItem(args[2],args[3],slot,args[5],amount,chance) ? "§aItem ditambahkan: §f"+args[5] : "§cGagal menambah item.");
                }
                case "setbuy" -> {
                    if (args.length != 6) { sender.sendMessage("§c/senzy shop setbuy <shop> <category> <slot_item> <number_buy>"); return; }
                    sender.sendMessage(shop.setBuy(args[2],args[3],Integer.parseInt(args[4]),Long.parseLong(args[5])) ? "§aHarga buy diperbarui." : "§cGagal memperbarui buy.");
                }
                case "setsell" -> {
                    if (args.length != 6) { sender.sendMessage("§c/senzy shop setsell <shop> <category> <slot_item> <number_sell|sell_at_buy>"); return; }
                    sender.sendMessage(shop.setSell(args[2],args[3],Integer.parseInt(args[4]),args[5]) ? "§aHarga sell diperbarui." : "§cGagal memperbarui sell.");
                }
            }
            guis.refreshAll();
        } catch (NumberFormatException e) { sender.sendMessage("§cAngka/slot tidak valid."); } catch (java.io.IOException e) { sender.sendMessage("§cGagal memuat ulang GUI: " + e.getMessage()); }
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
            List<String> subs = new ArrayList<>(List.of(
                    "shop", "shop-search", "balance", "sell", "sellall", "contract", "restock"));
            if (sender.hasPermission("senzy.admin")) subs.add("admin");
            return filter(subs, args[0]);
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("admin") && sender.hasPermission("senzy.admin")) {
            return admin.complete(args);
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("shop") && sender.hasPermission("senzy.shop.admin")) {
            if (args.length == 2) return filter(List.of("addshop","addcategory","additem","setbuy","setsell","admin"), args[1]);
            if (args[1].equalsIgnoreCase("additem") && args.length == 3) return filter(List.of(shop.activeShop()), args[2]);
            if (args[1].equalsIgnoreCase("additem") && args.length == 4) return filter(new ArrayList<>(shop.categories().stream().map(c -> c.id()).toList()), args[3]);
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("shop") && args[1].equalsIgnoreCase("admin")
                && sender.hasPermission("senzy.shop.admin")) return filter(List.of("event"), args.length == 3 ? args[2] : "");
        return List.of(); // termasuk args[1] dari "shop-search": kata kunci bebas, tanpa saran tab
    }

    private static String[] prepend(String first, String[] rest) {
        String[] out = new String[rest.length + 1];
        out[0] = first;
        System.arraycopy(rest, 0, out, 1, rest.length);
        return out;
    }

    static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) if (o.toLowerCase(Locale.ROOT).startsWith(p)) out.add(o);
        return out;
    }
}
