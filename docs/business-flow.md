# BeeShop Business Flow

## Belanja & checkout
1. Pelanggan melihat katalog (`GET /api/products`), bisa mencari, memfilter kategori, dan membuka detail produk.
2. Keranjang disimpan di browser. Setiap kali katalog dimuat, harga dan stok di keranjang disamakan.
3. Saat checkout pelanggan mengisi data diri dan alamat, lalu cek ongkir (`POST /api/shipping/rates`). Berat dihitung server dari `weightGram` produk × jumlah.
4. `POST /api/checkout`:
   1. Produk dikunci (`SELECT ... FOR UPDATE`), lalu stok dan status aktif divalidasi.
   2. Ongkir **dihitung ulang di server** dari `shippingCode`. Nilai ongkir dari client tidak dipakai.
   3. Order dibuat dengan status `PENDING_PAYMENT`, stok dikurangi, `expiresAt = now + PAYMENT_TIMEOUT_MINUTES`.
   4. Di luar transaksi DB, backend meminta sesi pembayaran ke gateway. Kalau gateway gagal, order dibatalkan dan stok dikembalikan.
5. Pelanggan diarahkan ke Midtrans Snap (atau halaman simulasi di mode mock).

## Pembayaran
- Midtrans mengirim notifikasi ke `POST /api/payments/midtrans/notification`.
- Backend memverifikasi `signature_key = SHA512(order_id + status_code + gross_amount + server_key)` dan mencocokkan `gross_amount` dengan total order.
- Pemetaan status:
  - `settlement` / `capture`+`accept` → order `PAID`
  - `pending` → tidak berubah
  - `deny` / `cancel` / `failure` → `CANCELLED` (payment `FAILED`), stok dikembalikan
  - `expire` → `CANCELLED` (payment `EXPIRED`), stok dikembalikan
- Notifikasi duplikat diabaikan (idempoten).
- Kalau pembayaran masuk setelah order dibatalkan/expired, order tetap `CANCELLED` dan riwayat diberi catatan **"perlu refund manual"**. Error juga dicatat di log.

## Fulfillment (admin)
```
PENDING_PAYMENT ──(payment)──▶ PAID ──▶ PROCESSING ──▶ SHIPPED (wajib resi) ──▶ DELIVERED
       │                         │            │
       └──▶ CANCELLED            └────────────┴──▶ REFUNDED
```
- `PAID` hanya bisa di-set oleh payment gateway, tidak oleh admin.
- `CANCELLED` / `REFUNDED` dari status yang masih memegang stok akan mengembalikan stok.
- Setiap perubahan tercatat di `shop_order_status_history` dan tampil di halaman lacak pesanan.

## Order kedaluwarsa
Scheduler (`EXPIRY_CHECK_INTERVAL_MS`, default 60 detik) membatalkan order `PENDING_PAYMENT` yang sudah melewati `expiresAt` dan mengembalikan stoknya. Midtrans juga diberi `expiry` yang sama.

## Sinkronisasi BeePos-App
SKU dipakai sebagai kunci bersama.
- **BeePos → BeeShop:** `PUT /api/integrations/beepos/stock` mengirim stok absolut per SKU, misalnya setelah transaksi kasir atau stock opname. SKU yang tidak dikenal dikembalikan di `unknownSkus`.
- **BeeShop → BeePos:** BeePos menarik `GET /api/integrations/beepos/sales?since=...` secara berkala untuk mengurangi stok gudang dari penjualan online yang sudah dibayar. Simpan `paidAt` terakhir sebagai `since` berikutnya.

## Ongkir
- Provider: `mock`, `rajaongkir`, `biteship` (`ShippingConfig`). Kode layanan berformat `kurir:LAYANAN`, contoh `jne:REG`. Checkout mencocokkan ulang kode ini dengan quote terbaru, jadi ongkir tidak bisa dimanipulasi dari browser.
- Hasil quote di-cache per (kota, kode pos, berat, nilai barang) selama `SHIPPING_CACHE_TTL_MINUTES`. Cek ongkir lalu checkout di dalam jendela itu hanya memakai 1 hit API.
- RajaOngkir butuh ID destinasi. ID dicari dari kode pos pelanggan lalu di-cache di memori.
- Menambah provider lain: implementasikan `ShippingProvider` dan daftarkan di `ShippingConfig`. API key hanya dari environment.

## Akun admin
- Akun OWNER pertama dibuat dari `ADMIN_BOOTSTRAP_*` saat tabel `admin_users` masih kosong.
- **OWNER** bisa semua, termasuk membuat dan menonaktifkan akun serta reset password. **STAFF** hanya produk, pesanan, dan ringkasan.
- Login menghasilkan token acak 256-bit. Yang disimpan di DB hanya hash SHA-256-nya, dengan masa berlaku `ADMIN_SESSION_HOURS`.
- 5 login gagal → akun terkunci 15 menit. Maksimal 20 percobaan login per IP per 15 menit. Pesan error sama untuk username tidak ada maupun password salah.
- Menonaktifkan akun, menurunkan role, atau reset password langsung mengakhiri semua sesi akun tersebut. Ganti password sendiri mengakhiri sesi di perangkat lain.
- OWNER tidak bisa menonaktifkan atau menurunkan role dirinya sendiri, dan minimal harus ada satu OWNER aktif.
- Setiap perubahan status order mencatat `actor` (`customer`, `system`, `payment:midtrans`, `admin:<username>`). Nama admin hanya tampil di panel admin, tidak di halaman lacak pesanan pelanggan.
