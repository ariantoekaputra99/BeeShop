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
| Gambar produk | Upload dari panel admin (JPG/PNG/WebP, maks 5 MB). Jenis file divalidasi dari isinya, orientasi foto HP diperbaiki, gambar diperkecil ke 1600 px, dan metadata/GPS dihapus |
| Admin | Akun per orang (username + password BCrypt), role OWNER/STAFF, sesi token 12 jam, akun terkunci setelah 5 kali gagal, batas login per IP. Ringkasan, kelola produk, proses order + resi. Setiap perubahan status mencatat siapa pelakunya |
| BeePos-App | `PUT` stok absolut per SKU, `GET` penjualan online yang sudah dibayar sejak waktu tertentu. Dilindungi `BEEPOS_INTEGRATION_KEY` |

## Menjalankan lokal
Tidak perlu menyiapkan env apa pun. Konfigurasi lokal sudah ada di `env/local.env` dan dibaca otomatis.
```bash
docker compose up -d postgres                  # PostgreSQL di port 5433
cd backend && ./mvnw spring-boot:run            # profile "local" aktif otomatis (Windows: mvnw.cmd)
cd frontend && npm install && npm run dev
```
- Storefront: http://localhost:5173
- Admin: http://localhost:5173/#/admin, login `admin` / `admin-lokal-123`
- API: http://localhost:8081/api · Health: http://localhost:8081/actuator/health

Atau jalankan semuanya dengan Docker: `docker compose --profile app up --build`.

Di lokal, pembayaran memakai **halaman simulasi**, ongkir memakai tarif simulasi, dan katalog contoh diisi otomatis. Kalau ingin mencoba Midtrans sandbox atau API ongkir sungguhan dari lokal, salin `env/local.override.env.example` ke `env/local.override.env` lalu isi key-nya. File itu di-ignore git.

## Environment
| | **local** | **sit** | **uat** | **prod** |
|---|---|---|---|---|
| Untuk | Laptop developer | QA / integrasi tim | Acceptance test oleh bisnis | Transaksi asli |
| Sumber env | `env/local.env` (otomatis) | Secret/env server | Secret/env server | Secret manager |
| Template | — | `env/sit.env.example` | `env/uat.env.example` | `env/prod.env.example` |
| Payment | mock | mock / Midtrans sandbox | Midtrans **sandbox** (wajib) | Midtrans **production** (wajib) |
| Ongkir | mock | mock / test key | API sungguhan (wajib) | API sungguhan (wajib) |
| Data demo | ya | ya | tidak | tidak |
| Log `com.bee.shop` | DEBUG | DEBUG | INFO | INFO (root WARN) |
| Validasi saat start | — | — | ketat | ketat |
| Frontend | `npm run dev` | `npm run build:sit` | `npm run build:uat` | `npm run build` |

- **Config Spring:** `application.yml` berisi bagian yang sama untuk semua environment. Perbedaan per environment ada di `application-{local,sit,uat,prod}.yml`. Profile dipilih lewat `SPRING_PROFILES_ACTIVE`, dan defaultnya `local`.
- **Secret** (password DB, key Midtrans/ongkir/BeePos) tidak pernah ditulis di yml, selalu dari environment variable. Yang boleh di-commit hanya `env/local.env` (khusus lokal) dan template `*.example`.
- **Variabel wajib kosong:** aplikasi berhenti dengan pesan yang menyebut nama variabelnya.
- **Validasi UAT/PROD** (`EnvironmentGuard`): aplikasi **menolak start** kalau salah satu kondisi ini terjadi:
  - payment masih mock, atau key Midtrans tidak sesuai environment (prod tidak boleh key `SB-...`, UAT wajib key `SB-...`)
  - ongkir masih mock
  - CORS/storefront bukan https atau masih localhost
  - password DB lemah
  - `BEEPOS_INTEGRATION_KEY` kurang dari 32 karakter
  - data demo aktif
- **Frontend:** URL API per environment ada di `frontend/.env.{development,sit,uat,production}`. Ganti `<domain-anda>` dengan domain sebenarnya. Selain prod, storefront menampilkan banner nama environment. Untuk Docker: `docker build --build-arg BUILD_MODE=uat ./frontend`.

## Test
```bash
cd backend && ./mvnw test      # 52 test: checkout, stok, payment, admin & login, BeePos, adapter ongkir, Flyway, validasi config, upload gambar
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
| GET | `/api/images/{nama}` | Gambar produk hasil upload |
| GET | `/api/payments/config` | Provider aktif + Midtrans client key |
| POST | `/api/payments/midtrans/notification` | Webhook Midtrans (Payment Notification URL) |
| POST | `/api/payments/mock/{orderNumber}?result=success\|failed` | Hanya aktif di mode mock |

### Admin (header `Authorization: Bearer <token>`)
Login: `POST /api/admin/auth/login` dengan body `{username, password}` → `{token, expiresAt, user}`. Lalu `POST /api/admin/auth/logout`, `GET /api/admin/auth/me`, `POST /api/admin/auth/password` dengan body `{currentPassword, newPassword}`.

Khusus OWNER: `GET|POST /api/admin/users`, `PATCH /api/admin/users/{id}` dengan body `{displayName?, role?, active?, newPassword?}`.

Lainnya (OWNER & STAFF): `POST /api/admin/uploads/images` (multipart, field `file`) → `{url, width, height}` · `GET /api/admin/dashboard` · `GET|POST /api/admin/products` · `GET|PUT|DELETE /api/admin/products/{id}` · `GET /api/admin/orders?status=&q=` · `GET /api/admin/orders/{orderNumber}` · `PATCH /api/admin/orders/{orderNumber}/status` dengan body `{status, trackingNumber?, note?}`

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

## Gambar produk
File gambar disimpan di folder `IMAGE_DIR`. Di lokal defaultnya `./data/images`, relatif terhadap folder tempat backend dijalankan. Di database, yang disimpan hanya path relatif `/api/images/<uuid>.jpg`, sehingga data bisa dipindah antar environment tanpa mengubah URL.

Di SIT/UAT/PROD, `IMAGE_DIR` **wajib** berupa volume persisten. Image Docker sudah memakai `/app/data` sebagai volume. Backup folder ini bersama database. Kalau nanti backend dijalankan lebih dari 1 instance, pindahkan penyimpanan ke object storage (S3/R2/MinIO) dengan mengganti implementasi `ImageStorage`.

## Database & migrasi
Skema dibuat dan diubah oleh **Flyway** saat aplikasi start. Untuk perubahan skema, tambahkan file baru `V2__deskripsi.sql`, `V3__...`, dan jangan pernah mengedit file migrasi yang sudah berjalan. Hibernate memakai `ddl-auto: validate`, jadi aplikasi gagal start kalau entity tidak cocok dengan tabel. Dengan begitu ketidakcocokan ketahuan sejak awal, bukan saat runtime.

Kalau database lama sudah dibuat oleh `ddl-auto: update` versi sebelumnya, cara paling aman adalah membuat database baru. Kalau ingin tetap memakai database lama, set `FLYWAY_BASELINE_ON_MIGRATE=true` sekali. Pastikan dulu strukturnya sama dengan `V1__init.sql`.

## Konfigurasi production
Ringkasnya: isi semua nilai di `env/prod.env.example` ke secret manager, set `SPRING_PROFILES_ACTIVE=prod`, lalu deploy. `EnvironmentGuard` akan menolak start kalau ada yang terlewat.

1. Isi `ADMIN_BOOTSTRAP_USERNAME` / `ADMIN_BOOTSTRAP_PASSWORD` (minimal 16 karakter) untuk start pertama. Setelah login, ganti password lalu hapus kedua variabel itu.
2. Isi `MIDTRANS_SERVER_KEY` / `MIDTRANS_CLIENT_KEY` dengan key production. Mode sandbox otomatis mati di profile prod. Daftarkan `https://<domain-api>/api/payments/midtrans/notification` sebagai Payment Notification URL di dashboard Midtrans.
3. Set `CORS_ORIGINS` dan `STOREFRONT_URL` ke domain storefront (https).
4. Pilih provider ongkir (lihat bagian Ongkir).
5. Jalankan di belakang HTTPS. Profile prod sudah membaca header proxy (`X-Forwarded-*`) supaya IP login tercatat benar.

Yang otomatis di profile prod: data demo mati, Midtrans production, log root WARN.

Alur bisnis lengkap ada di [docs/business-flow.md](docs/business-flow.md).
