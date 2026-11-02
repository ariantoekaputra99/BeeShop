# BeeShop

Toko online untuk retail: katalog, keranjang, checkout, payment gateway (Midtrans Snap), ongkir, lacak pesanan, panel admin, dan sinkronisasi stok dengan BeePos-App.

## Stack
- **Frontend:** React 18 + Vite + TypeScript + Tailwind CSS
- **Backend:** Java 21 + Spring Boot 3.3 + Spring Data JPA
- **Database:** PostgreSQL 16 (H2 khusus test)
- **Payment:** abstraksi `PaymentGateway` dengan adapter Midtrans Snap dan mock untuk development
- **Shipping:** abstraksi `ShippingProvider` dengan adapter RajaOngkir (Komerce), Biteship, dan mock
- **Skema DB:** Flyway (`backend/src/main/resources/db/migration`), Hibernate hanya `validate`

## Fitur
| Modul | Isi |
|---|---|
| Katalog | Pencarian nama/SKU, filter kategori, urutan harga/terbaru, paginasi, detail produk |
| Keranjang | Tersimpan di browser, stepper jumlah, harga & stok disinkronkan dengan katalog terbaru |
| Checkout | Data pelanggan + alamat, stok dikunci (pessimistic lock) supaya tidak oversell |
| Ongkir | RajaOngkir atau Biteship dari berat produk. Hasil di-cache 10 menit, jadi cek ongkir + checkout cukup 1 hit API |
| Payment | Midtrans Snap. Notifikasi diverifikasi dengan `signature_key` SHA-512 dan nominal dicocokkan dengan total order. Idempoten. Mode mock untuk development |
| Lifecycle order | `PENDING_PAYMENT → PAID → PROCESSING → SHIPPED → DELIVERED`, plus `CANCELLED` / `REFUNDED`. Transisi divalidasi, ada riwayat status, stok dikembalikan otomatis saat batal/refund |
| Auto-expire | Order yang tidak dibayar melewati `PAYMENT_TIMEOUT_MINUTES` dibatalkan dan stoknya dilepas |
| Lacak pesanan | Pelanggan cek status, resi, dan riwayat dengan nomor order + email. Order yang belum dibayar bisa dibatalkan |
| Admin | Akun per orang (username + password BCrypt), role OWNER/STAFF, sesi token 12 jam, akun terkunci setelah 5 kali gagal, batas login per IP. Ringkasan, kelola produk, proses order + resi. Setiap perubahan status mencatat siapa pelakunya |
| BeePos-App | `PUT` stok absolut per SKU, `GET` penjualan online yang sudah dibayar sejak waktu tertentu. Dilindungi `BEEPOS_INTEGRATION_KEY` |

## Menjalankan lokal
```bash
cp .env.example .env            # isi ADMIN_BOOTSTRAP_* dan BEEPOS_INTEGRATION_KEY
docker compose up -d postgres   # PostgreSQL di port 5433

cd backend && ADMIN_BOOTSTRAP_USERNAME=owner ADMIN_BOOTSTRAP_PASSWORD=ganti-password-ini mvn spring-boot:run
cd frontend && npm install && npm run dev
```
- Storefront: http://localhost:5173
- Admin: http://localhost:5173/#/admin (masuk dengan akun bootstrap, lalu buat akun staf di menu **Akun admin**)
- API: http://localhost:8081/api · Health: http://localhost:8081/actuator/health

Atau jalankan semuanya dengan Docker: `docker compose --profile app up --build`.

Tanpa `MIDTRANS_SERVER_KEY`, checkout diarahkan ke halaman **pembayaran simulasi** untuk menguji alur. Katalog contoh diisi otomatis saat database masih kosong (`SEED_DEMO_DATA=true`).

## Test
```bash
cd backend && mvn test        # 36 test: checkout, stok, payment, admin & login, BeePos, adapter ongkir, migrasi Flyway
cd frontend && npm run build  # type-check + build
```

## API
### Publik
| Method | Endpoint | Keterangan |
|---|---|---|
| GET | `/api/products?q=&category=&sort=&page=&size=` | `sort`: `name`, `price_asc`, `price_desc`, `newest` |
| GET | `/api/products/{id}` | Detail produk aktif |
| GET | `/api/categories` | Daftar kategori |
| POST | `/api/shipping/rates` | `{destinationCity, postalCode, items:[{productId, quantity}]}` |
| POST | `/api/checkout` | Membuat order + sesi pembayaran. `shippingCode` dari hasil cek ongkir |
| POST | `/api/orders/{orderNumber}/lookup` | `{email}` → detail & riwayat order |
| POST | `/api/orders/{orderNumber}/cancel` | `{email}` → batalkan order yang belum dibayar |
| GET | `/api/payments/config` | Provider aktif + Midtrans client key |
| POST | `/api/payments/midtrans/notification` | Webhook Midtrans (Payment Notification URL) |
| POST | `/api/payments/mock/{orderNumber}?result=success\|failed` | Hanya aktif di mode mock |

### Admin (header `Authorization: Bearer <token>`)
Login: `POST /api/admin/auth/login` dengan body `{username, password}` → `{token, expiresAt, user}`. Lalu `POST /api/admin/auth/logout`, `GET /api/admin/auth/me`, `POST /api/admin/auth/password` dengan body `{currentPassword, newPassword}`.

Khusus OWNER: `GET|POST /api/admin/users`, `PATCH /api/admin/users/{id}` dengan body `{displayName?, role?, active?, newPassword?}`.

Lainnya (OWNER & STAFF): `GET /api/admin/dashboard` · `GET|POST /api/admin/products` · `GET|PUT|DELETE /api/admin/products/{id}` · `GET /api/admin/orders?status=&q=` · `GET /api/admin/orders/{orderNumber}` · `PATCH /api/admin/orders/{orderNumber}/status` dengan body `{status, trackingNumber?, note?}`

### BeePos-App (header `X-Integration-Key`)
- `PUT /api/integrations/beepos/stock` dengan body `{items:[{sku, stock}]}` → `{updated, unknownSkus}`
- `GET /api/integrations/beepos/sales?since=2026-11-01T00:00:00Z` → penjualan online yang sudah dibayar, per SKU

## Ongkir: RajaOngkir atau Biteship
Pilih salah satu lewat `SHIPPING_PROVIDER`. Atur kurir lewat `SHIPPING_COURIERS` (kode kurir dipisah koma, contoh `jne,sicepat,jnt`).

**RajaOngkir (Komerce)**: daftar di rajaongkir.com, ambil API key "Shipping Cost".
```bash
SHIPPING_PROVIDER=rajaongkir
SHIPPING_API_KEY=<api key>
SHIPPING_ORIGIN_ID=<id lokasi toko>
```
Untuk mencari `SHIPPING_ORIGIN_ID`, panggil sekali endpoint pencarian dengan kode pos toko, lalu ambil field `id`:
```bash
curl -H "key: <api key>" "https://rajaongkir.komerce.id/api/v1/destination/domestic-destination?search=10110"
```
ID tujuan dicari otomatis dari kode pos pelanggan dan disimpan di memori, supaya kuota harian tidak cepat habis.

**Biteship**: ambil API key di dashboard Biteship (`biteship_test...` untuk testing, `biteship_live...` untuk production), lalu aktifkan kurir yang dipakai di dashboard.
```bash
SHIPPING_PROVIDER=biteship
SHIPPING_API_KEY=<api key>
SHIPPING_ORIGIN_POSTAL_CODE=12440
```

Kalau provider error atau timeout, pelanggan melihat pesan "Layanan cek ongkir sedang tidak tersedia" dan detail error tercatat di log. Kalau rute tidak dilayani, pelanggan melihat "Belum ada layanan pengiriman ke alamat ini".

## Database & migrasi
Skema dibuat dan diubah oleh **Flyway** saat aplikasi start. Untuk perubahan skema, tambahkan file baru `V2__deskripsi.sql`, `V3__...`, dan jangan pernah mengedit file migrasi yang sudah berjalan. Hibernate memakai `ddl-auto: validate`, jadi aplikasi gagal start kalau entity tidak cocok dengan tabel. Dengan begitu ketidakcocokan ketahuan sejak awal, bukan saat runtime.

Kalau database lama sudah dibuat oleh `ddl-auto: update` versi sebelumnya, cara paling aman adalah membuat database baru. Kalau ingin tetap memakai database lama, set `FLYWAY_BASELINE_ON_MIGRATE=true` sekali. Pastikan dulu strukturnya sama dengan `V1__init.sql`.

## Konfigurasi production
1. Isi `ADMIN_BOOTSTRAP_USERNAME` / `ADMIN_BOOTSTRAP_PASSWORD` untuk start pertama. Setelah login, ganti password lalu hapus kedua variabel itu. Isi juga `BEEPOS_INTEGRATION_KEY` dengan string acak panjang (`openssl rand -hex 32`). Kalau kosong, endpoint integrasi selalu menolak akses (fail-closed).
2. Isi `MIDTRANS_SERVER_KEY` / `MIDTRANS_CLIENT_KEY` dan set `MIDTRANS_SANDBOX=false`. Daftarkan `https://<domain-api>/api/payments/midtrans/notification` sebagai Payment Notification URL di dashboard Midtrans.
3. Set `CORS_ORIGINS` dan `STOREFRONT_URL` ke domain storefront.
4. Set `SEED_DEMO_DATA=false`.
5. Jalankan di belakang HTTPS (token admin dikirim lewat header). Kalau memakai reverse proxy, set `FORWARD_HEADERS_STRATEGY=framework`.
6. Pilih provider ongkir (lihat bagian Ongkir) dan set `SHIPPING_PROVIDER` ke `rajaongkir` atau `biteship`.

Alur bisnis lengkap ada di [docs/business-flow.md](docs/business-flow.md).
