package id.senzy.shop.shop;

import id.senzy.shop.economy.EconomyManager;
import id.senzy.shop.database.*;
import id.senzy.shop.event.ShopEventManager;
import id.senzy.shop.util.MessageUtil;

/** Satu instance marketplace lengkap: katalog + stok + transaksi. */
public final class ShopRuntime {
    private final String name;
    private final ShopManager shop;
    private final StockManager stock;
    private final TradeService trade;

    public ShopRuntime(String name, ShopManager shop, StockManager stock, TradeService trade) {
        this.name = name;
        this.shop = shop;
        this.stock = stock;
        this.trade = trade;
    }
    public String name() { return name; }
    public ShopManager shop() { return shop; }
    public StockManager stock() { return stock; }
    public TradeService trade() { return trade; }
}
