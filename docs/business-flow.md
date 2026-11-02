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

## Menambah provider ongkir sungguhan
Implementasikan `ShippingProvider` (misalnya `RajaOngkirShippingProvider`), lalu daftarkan di `ShippingConfig` untuk nilai `SHIPPING_PROVIDER` yang sesuai. API key hanya dari environment. Jangan commit API key.
