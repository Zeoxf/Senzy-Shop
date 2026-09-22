# SenzyShop V1.1

Plugin marketplace/ekonomi Minecraft (Paper 1.21.x, Java 21) dengan mata uang **Senzy Coin (SC)**.
V1.1 menambahkan kategori **Material** (harga otomatis dari ore), **Search**, dan sistem
**Contract** (kontrak harian/mingguan) di atas fondasi V1.0.

## 1. Build

Butuh **JDK 21** dan **Maven** terpasang di komputer kamu.

```bash
cd senzyshop
mvn clean package
```

Hasil build:

```
target/senzyshop-1.1.0.jar
```

Driver SQLite (`org.xerial:sqlite-jdbc`) sudah didaftarkan di `pom.xml` dan otomatis di-shade
(digabung) ke dalam jar lewat `maven-shade-plugin` — tidak perlu langkah tambahan apa pun.
Build pertama butuh internet untuk mengunduh Paper API + sqlite-jdbc.

## 2. Install

1. Copy `target/senzyshop-1.1.0.jar` ke folder `plugins/` server Paper 1.21.x.
2. Start/restart server.
3. Plugin otomatis membuat folder `plugins/SenzyShop/` berisi `config.yml`, `items.yml`,
   `contracts.yml`, `messages.yml`, `database.yml`, dan `senzyshop.db`.

Jika kamu upgrade dari V1.0: cukup timpa jar lama dengan yang baru dan restart. Database lama
tetap kompatibel (tabel `contracts`/`contract_progress` baru dibuat otomatis; `meta` yang lama
tetap terpakai untuk timer restock, hanya sekarang diakses lewat repository terpisah).

## 3. Konfigurasi harga

### Item biasa (Natural/Ore/Farming/Animals/Food)

Edit `plugins/SenzyShop/items.yml`, sama seperti V1.0:

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

### Item Material (harga otomatis dari ore)

Item kategori `MATERIAL` secara default memakai `price-mode: ORE_MULTIPLIER` — harga BUKAN
ditulis manual, tapi dihitung otomatis dari item ore acuannya setiap `items.yml` dimuat:

```yaml
items:
  diamond:
    material: DIAMOND
    category: MATERIAL
    enabled: true
    price-mode: ORE_MULTIPLIER
    ore-reference: diamond_ore   # WAJIB id item ORE yang valid & bisa dijual
    multiplier: 1.5              # harga beli = harga beli diamond_ore x 1.5
    sell-ratio: 0.5               # harga jual = harga beli diamond x 0.5
    stock:
      min: 1
      max: 4
      chance: 30
```

Kalau `diamond_ore` buy-price 500, maka `diamond` otomatis: buy 750, sell 375. Ganti harga
`diamond_ore` di `items.yml` lalu `/senzy admin reload` → harga `diamond` ikut berubah otomatis.

Untuk memberi item Material harga TETAP (tidak ikut ore), ganti `price-mode: FIXED` dan isi
`buy-price`/`sell-price` manual seperti item biasa (atau pakai `/senzy admin setprice`, yang
otomatis mengubah item jadi `FIXED`).

Setelah mengubah `items.yml` secara manual: `/senzy admin reload`.

## 4. Menambah item baru

1. Tambahkan entri baru di `items.yml` dengan key unik huruf kecil.
2. `material` harus nama enum `Material` Bukkit yang valid (huruf besar).
3. `category` harus salah satu dari: `NATURAL`, `ORE`, `MATERIAL`, `FARMING`, `ANIMALS`, `FOOD`.
4. Untuk kategori `MATERIAL`, tambahkan `ore-reference` (id item ORE yang sudah ada & bisa dijual)
   — atau pakai `price-mode: FIXED` + `buy-price`/`sell-price` manual.
5. Jalankan `/senzy admin reload`. Item baru mulai dengan stok 0 sampai restock berikutnya
   (atau jalankan `/senzy admin restock` untuk langsung mengisi stoknya).

Item dengan material Nether ore, Ancient Debris, Netherite, Elytra, Totem of Undying, atau
Nether Star otomatis ditolak saat load (dicatat di console).

## 5. Contract (kontrak harian/mingguan)

Edit `plugins/SenzyShop/contracts.yml` untuk mengatur pool kandidat kontrak:

```yaml
daily:
  pool:
    coal_run:
      item: coal          # WAJIB id item yang bisa dijual di items.yml
      amount: 40           # target jumlah SELL
      reward: 800           # Senzy Coin saat claim
```

Setiap reset (default: harian 24 jam, mingguan 7 hari — bisa diatur di `config.yml` bagian
`contracts:`), plugin memilih `random-count` kontrak SECARA ACAK dari pool; target & reward
SELALU tetap sesuai yang ditulis di `contracts.yml`, tidak pernah ikut di-random. Progress
hanya bertambah dari transaksi **SELL yang benar-benar berhasil** lewat `/senzy sell`/`sellall`
— membeli lalu menjual, item dari command lain, atau transaksi yang gagal tidak dihitung.

Timer reset tersimpan di database (bukan counter RAM), sama seperti restock — tidak reset ke
awal saat server restart.

## 6. Perintah

Pemain:
- `/senzy shop` — buka GUI toko (kini 6 kategori termasuk Material, plus tombol Contracts & Search)
- `/senzy balance` — lihat Senzy Coin
- `/senzy sell` — buka GUI jual
- `/senzy sellall` — jual semua item yang bisa dijual sekaligus
- `/senzy contract` — buka GUI kontrak harian/mingguan
- `/senzy restock` — lihat sisa waktu restock berikutnya

Pencarian item: klik tombol Search di GUI utama, ketik kata kunci di chat (atau `batal` untuk
membatalkan) — tidak butuh dependency eksternal, memakai `AsyncChatEvent` bawaan Paper.

Admin (permission `senzy.admin`):
- `/senzy admin reload`
- `/senzy admin restock`
- `/senzy admin setbalance <pemain> <jumlah>`
- `/senzy admin addbalance <pemain> <jumlah>`
- `/senzy admin removebalance <pemain> <jumlah>`
- `/senzy admin setstock <item> <jumlah>`
- `/senzy admin setprice <item> <buy|sell> <harga>` — baru di V1.1, langsung menyimpan ke
  `items.yml` dan mengubah item itu jadi `price-mode: FIXED`
- `/senzy admin logs [jumlah]`

## 7. Expensive purchase confirmation

Jika total harga pembelian (lewat GUI) >= `security.expensive-threshold` di `config.yml`
(default 1000 SC), muncul dialog konfirmasi sebelum transaksi benar-benar dijalankan. Bisa
dimatikan dengan `security.confirm-expensive-items: false`.

## 8. Kompatibilitas dengan plugin "Senzy" (XPR Boost Progression + LootBox)

Jika servermu juga memakai plugin lain bernama **Senzy** (sistem XPR Boost Progression +
LootBox, command `/senzy xpr`, `/senzy lootbox`, `/senzy reload`), SenzyShop akan otomatis
"menyambung" ke command `/senzy` milik plugin itu saat startup — **tanpa mengubah plugin
tersebut sama sekali** (kita tidak menyentuh jar-nya):

- `/senzy shop`, `balance`, `sell`, `sellall`, `contract`, `restock`, `admin ...` → ditangani SenzyShop.
- `/senzy xpr`, `lootbox`, `reload`, `help` (dan lainnya) → tetap diteruskan apa adanya ke
  plugin Senzy, perilakunya tidak berubah.

Ini otomatis, tidak perlu konfigurasi tambahan — cukup pastikan kedua jar ada di folder
`plugins/`. Di console log saat startup akan muncul:

```
[SenzyShop] Menyambung ke /senzy milik plugin 'Senzy': subcommand shop/balance/sell/sellall/
contract/restock/admin kini aktif di sana juga, tanpa mengubah plugin tersebut.
```

Kalau baris itu **tidak muncul**, berarti SenzyShop tidak menemukan plugin lain yang memegang
alias `/senzy` — dalam kasus itu SenzyShop tetap berjalan normal sendirian.

## 9. Catatan arsitektur

- Semua nilai uang bertipe `long` (bukan `double`). Harga Material dihitung dengan `BigDecimal`
  + pembulatan `HALF_UP` (mis. 75 x 1.5 = 112.5 → 113), hasilnya langsung disimpan sebagai `long`
  — floating point tidak pernah dipakai untuk menyimpan balance/harga.
- Restock & reset kontrak berbasis timestamp sistem tersimpan di database (tabel `meta`), jadi
  countdown tetap akurat setelah server restart.
- Beli/jual divalidasi & dieksekusi sinkron di main thread, lalu balance + stok + log transaksi
  disimpan dalam SATU transaksi SQL (all-or-nothing) — mencegah duplikasi uang/item bahkan saat
  spam klik atau crash. Progress kontrak hanya diupdate SETELAH sell benar-benar berhasil.
- Kontrak berlaku satu set untuk SEMUA pemain (server-wide); progress per pemain disimpan
  terpisah di `contract_progress`. Target & reward selalu dari `contracts.yml`, hanya kontrak
  MANA yang tampil tiap reset yang diacak.
- Struktur package modular (`command/`, `economy/`, `database/`, `shop/`, `gui/`, `restock/`,
  `contract/`, `listener/`, `util/`) disiapkan agar V1.2+ (Boost Food, Reputation, Market Events,
  Seasonal Market, XPRoulette) bisa ditambahkan sebagai modul baru tanpa menulis ulang V1.1.

## 10. Uji manual (checklist acceptance test)

1. `/senzy balance` pemain baru → sesuai `economy.starting-balance`.
2. `/senzy shop` → GUI terbuka, 6 kategori (termasuk Material) + tombol Contracts & Search terlihat.
3. Kategori Material → harga Diamond = 1.5x harga Diamond Ore, stok terpisah dari ore-nya.
4. Kategori Ore → hanya ore Overworld, tanpa Nether Quartz/Nether Gold.
5. Search "diamond" → hasil Diamond Ore + Diamond muncul.
6. Beli item mahal (>= threshold) → dialog konfirmasi muncul; Confirm/Cancel bekerja benar.
7. `/senzy contract` → daftar kontrak Daily/Weekly + progress bar terlihat.
8. Jual item yang jadi target kontrak → progress bertambah; setelah penuh, klaim reward masuk
   Senzy Coin; klaim kedua ditolak.
9. Restart server → progress kontrak & timer reset tetap ada, tidak hilang/reset ke awal.
10. `/senzy admin setprice diamond_ore buy 600` → harga Diamond (Material) ikut naik otomatis
    ke 900 (600 x 1.5) setelah reload.
11. `/senzy admin restock` → stok berubah acak, countdown reset; harga tetap.
12. Spam klik GUI beli/jual → tidak ada uang/item ganda.
