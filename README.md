# SenzyShop V1.0 Core

Plugin marketplace/ekonomi Minecraft (Paper 1.21.x, Java 21) dengan mata uang **Senzy Coin (SC)**.

## 1. Build

Butuh **JDK 21** dan **Maven** terpasang di komputer kamu.

```bash
cd senzyshop
mvn clean package
```

Hasil build (nama file otomatis sesuai artifactId, tidak perlu di-rename manual lagi):

```
target/senzyshop-1.0.1.jar
```

Jika `mvn` belum ada, install dulu (Ubuntu/Debian: `apt install maven`, atau unduh dari
https://maven.apache.org/download.cgi). Build pertama akan mengunduh Paper API dan driver
SQLite (`org.xerial:sqlite-jdbc`) — **tambahkan dependency ini bila belum ada** (lihat catatan
di bawah bagian "Driver SQLite").

### Driver SQLite

`pom.xml` yang disertakan hanya mendaftarkan `paper-api` (scope `provided`, sudah ada di server
Paper). Paper **tidak** menyertakan driver JDBC SQLite secara bawaan, jadi kamu perlu menambahkan
salah satu dari dua opsi berikut sebelum build:

**Opsi A (disarankan) — shade driver ke dalam jar plugin.**
Tambahkan ke `pom.xml`:

```xml
<dependencies>
    ...
    <dependency>
        <groupId>org.xerial</groupId>
        <artifactId>sqlite-jdbc</artifactId>
        <version>3.46.1.3</version>
    </dependency>
</dependencies>

<build>
    <plugins>
        ...
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-shade-plugin</artifactId>
            <version>3.6.0</version>
            <executions>
                <execution>
                    <phase>package</phase>
                    <goals><goal>shade</goal></goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

**Opsi B** — taruh jar `sqlite-jdbc` di folder `plugins/` server sebagai library terpisah dan
muat lewat `plugin.yml` (`libraries:` Paper, versi 1.19.3+) atau plugin loader library seperti
`PlugMan`/`plugin-yml libraries`. Opsi A lebih sederhana dan sudah menjadi standar untuk plugin
dengan database lokal, jadi gunakan itu kalau tidak yakin.

## 2. Install

1. Copy `target/senzyshop-1.0.1.jar` ke folder `plugins/` server Paper 1.21.x.
2. Start/restart server.
3. Plugin otomatis membuat folder `plugins/SenzyShop/` berisi `config.yml`, `items.yml`,
   `messages.yml`, `database.yml`, dan `senzyshop.db`.

## 3. Konfigurasi harga

Edit `plugins/SenzyShop/items.yml`. Setiap entri:

```yaml
items:
  diamond_ore:
    material: DIAMOND_ORE
    category: ORE
    enabled: true
    buy-price: 500      # harga beli (SC). 0 = tidak dijual di toko
    sell-price: 250      # harga jual (SC). 0 = tidak bisa dijual. Harus <= buy-price
    stock:
      min: 2
      max: 8
      chance: 35         # % peluang item ini muncul tiap restock
    worlds: []            # kosong = semua world
```

Setelah mengubah harga: `/senzy admin reload` (harga langsung berlaku, stok saat ini tidak
diubah — stok baru menyesuaikan `min`/`max` pada restock berikutnya).

## 4. Menambah item baru

1. Tambahkan entri baru di `items.yml` dengan key unik (huruf kecil, mis. `nether_wart` — asal
   materialnya bukan item Nether/Netherite yang diblokir V1.0).
2. `material` harus nama enum `Material` Bukkit yang valid (huruf besar).
3. `category` harus salah satu dari: `NATURAL`, `ORE`, `FARMING`, `ANIMALS`, `FOOD`.
4. Jalankan `/senzy admin reload`. Item baru mulai dengan stok 0 sampai restock berikutnya
   (atau jalankan `/senzy admin restock` untuk langsung mengisi stoknya).

Item dengan material Nether ore, Ancient Debris, Netherite, Elytra, Totem of Undying, atau
Nether Star otomatis ditolak saat load (dicatat di console), sesuai batasan V1.0.

## 5. Perintah

Pemain:
- `/senzy shop` — buka GUI toko
- `/senzy balance` — lihat Senzy Coin
- `/senzy sell` — buka GUI jual
- `/senzy sellall` — jual semua item yang bisa dijual sekaligus
- `/senzy restock` — lihat sisa waktu restock berikutnya

Admin (permission `senzy.admin`):
- `/senzy admin reload`
- `/senzy admin restock`
- `/senzy admin setbalance <pemain> <jumlah>`
- `/senzy admin addbalance <pemain> <jumlah>`
- `/senzy admin removebalance <pemain> <jumlah>`
- `/senzy admin setstock <item> <jumlah>`
- `/senzy admin logs [jumlah]`

## 6. Kompatibilitas dengan plugin "Senzy" (XPR Boost Progression + LootBox)

Jika servermu juga memakai plugin lain bernama **Senzy** (sistem XPR Boost Progression +
LootBox, command `/senzy xpr`, `/senzy lootbox`, `/senzy reload`), SenzyShop akan otomatis
"menyambung" ke command `/senzy` milik plugin itu saat startup — **tanpa mengubah plugin
tersebut sama sekali** (kita tidak menyentuh jar-nya):

- `/senzy shop`, `/senzy balance`, `/senzy sell`, `/senzy sellall`, `/senzy restock`,
  `/senzy admin ...` → ditangani SenzyShop.
- `/senzy xpr`, `/senzy lootbox`, `/senzy reload`, `/senzy help` (dan lainnya) → tetap
  diteruskan apa adanya ke plugin Senzy, perilakunya tidak berubah.

Ini otomatis, tidak perlu konfigurasi tambahan — cukup pastikan kedua jar ada di folder
`plugins/`. Di console log saat startup akan muncul:

```
[SenzyShop] Menyambung ke /senzy milik plugin 'Senzy': subcommand shop/balance/sell/sellall/
restock/admin kini aktif di sana juga, tanpa mengubah plugin tersebut.
```

Kalau baris itu **tidak muncul**, berarti SenzyShop tidak menemukan plugin lain yang memegang
alias `/senzy` (mis. plugin Senzy belum terpasang, gagal load, atau urutan load-nya terbalik) —
dalam kasus itu SenzyShop tetap berjalan normal sendirian dengan command `/senzy` miliknya.

**Penting — nama plugin duplikat:** dari file yang kamu unggah, `SenzyShop.zip` dan
`XPRoulette__3_.zip` sama-sama berisi jar yang mendeklarasikan `name: Senzy` di plugin.yml
(dua build dari kode yang sama — satu lebih baru dengan tambahan package `id.xproulette`
yang belum terhubung ke command manapun). **Jangan taruh dua-duanya sekaligus** di folder
`plugins/` — Bukkit/Paper tidak mengizinkan dua plugin dengan nama yang sama aktif bersamaan
dan salah satunya akan gagal load atau server bisa error saat start. Pilih salah satu (biasanya
yang paling baru/lengkap), lalu taruh senzyshop-1.0.1.jar (hasil build folder ini) di sampingnya.

## 7. Catatan arsitektur

- Semua nilai uang bertipe `long` (bukan `double`) — tidak ada floating point error.
- Restock berbasis timestamp sistem tersimpan di database (tabel `meta`), jadi countdown tetap
  akurat setelah server restart — tidak pernah kembali ke 3 jam penuh secara keliru.
- Beli/jual divalidasi & dieksekusi sinkron di main thread (bukan lewat data dari GUI client),
  lalu balance + stok + log transaksi disimpan dalam SATU transaksi SQL (all-or-nothing) di
  thread database terpisah — mencegah duplikasi uang/item bahkan saat spam klik atau crash.
- Harga selalu statis dari `items.yml`; hanya ketersediaan & jumlah stok yang diacak saat restock.
- Struktur package modular (`command/`, `economy/`, `database/`, `shop/`, `gui/`, `restock/`,
  `listener/`, `util/`) disiapkan agar fitur V1.1+ (Contracts, Boost Food, Reputation, Market
  Events, Seasonal Market, XPRoulette) bisa ditambahkan sebagai modul baru tanpa menulis ulang
  V1.0.

## 8. Uji manual (checklist acceptance test)

Setelah instal, coba di server test:
1. `/senzy balance` pemain baru → sesuai `economy.starting-balance`.
2. `/senzy shop` → GUI terbuka, 5 kategori terlihat.
3. Kategori Ore → hanya ore Overworld, tanpa Nether Quartz/Nether Gold.
4. Beli 1 item → balance berkurang & item masuk inventory.
5. Jual item yang tidak terdaftar (lewat `/senzy sell`, tidak akan muncul di daftar) → ditolak.
6. Stok habis → tombol beli tidak memberi item/uang berkurang (pesan "Item tersebut sedang habis").
7. `/senzy admin restock` → stok berubah acak, countdown reset.
8. Restart server saat countdown < 3 jam → countdown melanjutkan sisa waktu, bukan reset ke 3 jam.
9. Restart server → balance pemain tetap sama.
10. Penuhi inventory lalu coba beli → balance tidak berkurang.
11. Spam klik GUI beli/jual dengan cepat → tidak ada uang/item ganda (dilindungi cooldown klik +
    kunci proses per pemain + transaksi SQL atomik).
