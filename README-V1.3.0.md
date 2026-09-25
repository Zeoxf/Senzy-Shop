# SenzyShop 1.3.0 – Custom Shop + Quantity Selector

Update ini merombak loader shop agar membaca katalog dari:

`plugins/SenzyShop/shop/<shop_name>/`

Contoh default:

```text
shop/
└── senzyshop/
    ├── message.yml
    ├── blocks.yml
    ├── items.yml
    ├── sword.yml
    └── ...
```

## Command admin

```text
/senzy shop addshop <name>
/senzy shop addcategory <slot> <texture> <name>
/senzy shop additem <name_shop> <name_category> <slot_item> <item> <amount> <chance>
/senzy shop setbuy <name_shop> <name_category> <slot_item> <number_buy>
/senzy shop setsell <name_shop> <name_category> <slot_item> <number_sell>
/senzy shop setsell <name_shop> <name_category> <slot_item> sell_at_buy
```

`chance` divalidasi `0.01..1.0`: `1.0 = 100%`, `0.9 = 90%`, `0.01 = 1%`.

`setsell ... sell_at_buy` menghitung harga jual `buy × 1.015`, dibulatkan ke Senzy Coin integer terdekat karena ekonomi plugin memakai integer.

## GUI item

- Slot kosong = `GRAY_STAINED_GLASS_PANE`.
- Klik kanan item = langsung membeli 1.
- Klik kiri item = membuka Quantity Selector.
- Quantity Selector berukuran 27 slot dengan background kaca ungu.
- Kaca pink kiri = kurangi jumlah.
- Kaca pink kanan = tambah jumlah.
- Tengah = `Yakin ingin membeli <jumlah> item?`; klik tengah mengonfirmasi jumlah.
- Pilihan jumlah mengikuti `1, 2, 4, 8, 16, 32, 64, 128` dan otomatis dibatasi stok, saldo, serta kapasitas inventory.

Konfirmasi pembelian mahal yang sudah ada di V1.2 tetap aktif sebagai lapisan keamanan kedua.

## Catatan kompatibilitas

`items.yml` lama tetap dipakai sebagai fallback jika custom shop tidak tersedia. Pada startup pertama V1.3, katalog default `shop/senzyshop` dibundel ke plugin.
